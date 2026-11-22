package pl.szczodrzynski.edziennik.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.UserActionRequiredEvent
import pl.szczodrzynski.edziennik.data.api.interfaces.EdziennikCallback
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.api.task.IApiTask
import pl.szczodrzynski.edziennik.data.api.task.SzkolnyTask
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.ext.toApiError
import pl.szczodrzynski.edziennik.ext.toJsonObject
import pl.szczodrzynski.edziennik.ui.widgets.WidgetUpdater
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val LEGACY_TAG = "SyncWorker"
        private const val WORK_NAME = "PeriodicSchoolSync"
        private const val PUSH_PROFILE_IDS = "pushProfileIds"
        private const val PUSH_FEATURE_IDS = "pushFeatureIds"
        private const val PUSH_ENDPOINTS = "pushEndpoints"
        private const val PUSH_ARGUMENTS = "pushArguments"
        private const val MAX_RETRIES = 2

        internal fun enqueuePushSync(context: Context, tasks: List<IApiTask>): Boolean {
            val profileRequests = tasks.map { task ->
                val request = (task as? EdziennikTask)?.request as? EdziennikTask.SyncProfileRequest
                    ?: return false
                request to task.profileId
            }
            val workManager = WorkManager.getInstance(context)
            for ((request, profileIds) in profileRequests.groupBy({ it.first }, { it.second })) {
                val work = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(workDataOf(
                        PUSH_PROFILE_IDS to profileIds.distinct().toIntArray(),
                        PUSH_FEATURE_IDS to request.featureTypes?.map { it.id }?.toIntArray(),
                        PUSH_ENDPOINTS to request.onlyEndpoints?.toIntArray(),
                        PUSH_ARGUMENTS to request.arguments?.toString(),
                    ))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                    .build()
                workManager.enqueue(work)
            }
            return true
        }

        fun scheduleNext(app: App) {
            WorkManager.getInstance(app).cancelAllWorkByTag(LEGACY_TAG)
            schedule(app, ExistingPeriodicWorkPolicy.KEEP)
        }

        fun rescheduleNext(app: App) = schedule(app, ExistingPeriodicWorkPolicy.UPDATE)

        private fun schedule(app: App, policy: ExistingPeriodicWorkPolicy) {
            val manager = WorkManager.getInstance(app)
            if (!app.config.sync.enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }

            val interval = app.config.sync.interval.toLong().coerceAtLeast(30 * 60L)
            val network = if (app.config.sync.onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.SECONDS)
                .setInitialDelay(interval, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }

    private enum class Outcome { SUCCESS, NEEDS_USER, FAILED, RETRY }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as App
        val pushProfileIds = inputData.getIntArray(PUSH_PROFILE_IDS)?.toSet()
        val periodic = pushProfileIds == null
        if (periodic && (WORK_NAME !in tags || !app.config.sync.enabled)) return@withContext Result.success()
        if (withTimeoutOrNull(30_000) { app.syncReady.await() } == null) return@withContext retryOrFail()
        if (periodic && !app.config.sync.enabled) return@withContext Result.success()

        if (!ApiService.executionLock.tryLock()) return@withContext retryOrFail()

        try {
            val tasks = getSyncTasks(app, pushProfileIds)
            if (!periodic && tasks.isEmpty()) return@withContext Result.success()

            val outcomes = mutableListOf<Outcome>()
            val profiles = mutableListOf<Profile>()
            val completed = withTimeoutOrNull(TimeUnit.MINUTES.toMillis(7)) {
                for (task in tasks) {
                    outcomes += runTask(app, task) { callback ->
                        task.profile?.let(profiles::add)
                        task.run(app, callback)
                    }
                }
                true
            }
            if (completed == null) {
                Timber.w("Background sync exceeded the cycle time limit")
                outcomes += Outcome.RETRY
            }

            val notifications = SzkolnyTask(app, profiles)
            outcomes += runTask(app, notifications, timeoutSeconds = 60) { notifications.run(it) }
            WidgetUpdater.updateAll(app)
            Timber.d("Background sync outcomes: %s", outcomes)

            when {
                Outcome.RETRY in outcomes -> retryOrFail()
                Outcome.FAILED in outcomes || Outcome.NEEDS_USER in outcomes -> Result.failure()
                else -> Result.success()
            }
        } finally {
            ApiService.executionLock.unlock()
        }
    }

    private fun getSyncTasks(app: App, pushProfileIds: Set<Int>?): List<EdziennikTask> {
        val profiles = app.db.profileDao().profilesForSyncNow
        if (pushProfileIds != null) {
            val features = inputData.getIntArray(PUSH_FEATURE_IDS)?.let { ids ->
                FeatureType.entries.filter { it.id in ids }.toSet()
            }
            val endpoints = inputData.getIntArray(PUSH_ENDPOINTS)?.toSet()
            val arguments = inputData.getString(PUSH_ARGUMENTS)?.toJsonObject()
            return profiles.filter { it.id in pushProfileIds }.map {
                EdziennikTask.syncProfile(it.id, features, endpoints, arguments)
            }
        }

        return profiles.mapNotNull { profile ->
            val selected = app.config[profile.id].sync.automaticSyncFeatures
                ?.intersect(profile.loginStoreType.features)
            if (selected?.isEmpty() == true) return@mapNotNull null

            // Including the required features keeps a single selection on the normal endpoint timers.
            EdziennikTask.syncProfile(profile.id, selected?.plus(FeatureType.getNecessary()))
        }
    }

    private fun retryOrFail() = if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    private suspend fun runTask(
        app: App,
        task: IApiTask,
        timeoutSeconds: Long = 90,
        start: (EdziennikCallback) -> Unit,
    ): Outcome {
        val result = CompletableDeferred<Outcome>()
        val retryNeeded = AtomicBoolean()
        val failed = AtomicBoolean()
        val completedNormally = AtomicBoolean()

        fun outcome() = when {
            retryNeeded.get() -> Outcome.RETRY
            failed.get() -> Outcome.FAILED
            else -> Outcome.SUCCESS
        }

        fun reportError(error: ApiError): Outcome {
            val outcome = if (error.isRetryable()) Outcome.RETRY else Outcome.FAILED
            error.profileId = task.profileId
            Timber.w("Background sync error %s (%s), profile %s", error.errorCode, error.tag, task.profileId)
            if (error.isCritical && (outcome == Outcome.FAILED || runAttemptCount >= MAX_RETRIES)) {
                EventBus.getDefault().postSticky(ApiTaskErrorEvent(error, (task as? EdziennikTask)?.request))
            }
            return outcome
        }

        val callback = object : EdziennikCallback {
            override fun onCompleted() {
                completedNormally.set(true)
                result.complete(outcome())
            }

            override fun onRequiresUserAction(event: UserActionRequiredEvent) {
                if (result.complete(Outcome.NEEDS_USER)) app.userActionManager.sendToUser(event)
            }

            override fun onError(apiError: ApiError) {
                if (result.isCompleted || result.isCancelled) return
                val outcome = reportError(apiError)
                if (outcome == Outcome.RETRY) retryNeeded.set(true) else failed.set(true)
                if (apiError.isCritical) result.complete(outcome())
            }

            override fun onProgress(step: Float) = Unit
            override fun onStartProgress(stringRes: Int) = Unit
        }

        var cancelTask = true
        try {
            val outcome = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                task.prepare(app)
                start(callback)
                result.await()
            }
            if (outcome == null) {
                Timber.w("Background sync timed out for profile %s", task.profileId)
                return Outcome.RETRY
            }
            cancelTask = !completedNormally.get()
            return outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return reportError(e.toApiError(WORK_NAME))
        } finally {
            result.cancel()
            if (cancelTask) {
                runCatching { task.cancel() }
                    .onFailure { Timber.w("Could not cancel background sync for profile %s", task.profileId) }
            }
        }
    }

    private fun ApiError.isRetryable(): Boolean =
        errorCode in setOf(
            ERROR_REQUEST_FAILURE,
            ERROR_REQUEST_FAILURE_HOSTNAME_NOT_FOUND,
            ERROR_REQUEST_FAILURE_TIMEOUT,
            ERROR_REQUEST_FAILURE_NO_INTERNET,
            ERROR_LOGIN_LIBRUS_API_CONNECTION_PROBLEMS,
            ERROR_API_MAINTENANCE,
        ) || response?.code() == 429 || (response?.code() ?: 0) in 500..599 ||
            errorCode == ERROR_REQUEST_HTTP_500 || errorCode == ERROR_REQUEST_HTTP_503
}
