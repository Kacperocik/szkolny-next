package pl.szczodrzynski.edziennik.data.api.task

import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.Librus
import pl.szczodrzynski.edziennik.data.api.events.UserActionRequiredEvent
import pl.szczodrzynski.edziennik.data.api.interfaces.EdziennikCallback
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.ext.getAppData
import pl.szczodrzynski.edziennik.ext.toApiError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

class MarkAllReadTask(
    private val selectedProfileId: Int?,
    private val includeMessagesAndAnnouncements: Boolean,
) : IApiTask(selectedProfileId ?: -1) {
    private val job = Job()
    private var librus: Librus? = null

    override fun prepare(app: App) {
        taskName = app.getString(R.string.menu_mark_everything_as_read)
    }

    override fun cancel() {
        job.cancel()
    }

    fun run(app: App, callback: EdziennikCallback) {
        CoroutineScope(job + Dispatchers.IO).launch {
            var result = R.string.main_menu_mark_as_read_success
            try {
                val profiles = selectedProfileId?.let {
                    listOfNotNull(app.db.profileDao().getByIdNow(it))
                } ?: app.db.profileDao().allNow
                for (profile in profiles) {
                    try {
                        if (!markProfile(app, profile, callback))
                            result = R.string.main_menu_mark_as_read_partial
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        callback.onError(e.toApiError("MarkAllReadTask").setCritical(false))
                        result = R.string.main_menu_mark_as_read_partial
                    }
                }
            } catch (_: CancellationException) {
                result = R.string.main_menu_mark_as_read_cancelled
            } catch (e: Exception) {
                callback.onError(e.toApiError("MarkAllReadTask").setCritical(false))
                result = R.string.main_menu_mark_as_read_partial
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    Toast.makeText(app, result, Toast.LENGTH_LONG).show()
                    callback.onCompleted()
                }
            }
        }
    }

    private suspend fun markProfile(app: App, profile: Profile, callback: EdziennikCallback): Boolean {
        val metadata = app.db.metadataDao()
        if (!includeMessagesAndAnnouncements || profile.loginStoreType != LoginType.LIBRUS) {
            if (profile.getAppData().uiConfig.enableMarkAsReadAnnouncements)
                metadata.setAllSeenExceptMessages(profile.id, true)
            else
                metadata.setAllSeenExceptMessagesAndAnnouncements(profile.id, true)
            return true
        }

        metadata.setAllSeenExceptMessagesAndAnnouncements(profile.id, true)
        var success = true
        for (message in app.db.messageDao().getAllNow(profile.id).filter { !it.seen }) {
            if (!message.isReceived) {
                metadata.setSeen(profile.id, message, true)
            } else if (!awaitLibrus(app, profile, callback) { it.getMessage(message) }) {
                success = false
            }
        }
        if (metadata.countUnseenNow(profile.id, MetadataType.ANNOUNCEMENT) > 0 &&
            !awaitLibrus(app, profile, callback) { it.markAllAnnouncementsAsRead() }) {
            success = false
        }
        return success
    }

    private suspend fun awaitLibrus(
        app: App,
        profile: Profile,
        callback: EdziennikCallback,
        action: (Librus) -> Unit,
    ): Boolean {
        val login = app.db.loginStoreDao().getByIdNow(profile.loginStoreId)
            ?: return false
        var completed = false
        try {
            val success = withTimeoutOrNull(1.minutes) {
                suspendCancellableCoroutine { continuation ->
                    val finished = AtomicBoolean()
                    fun finish(success: Boolean) {
                        if (finished.compareAndSet(false, true))
                            continuation.resume(success)
                    }
                    val listener = object : EdziennikCallback {
                        override fun onCompleted() = finish(true)
                        override fun onError(apiError: ApiError) {
                            if (!finished.get()) {
                                callback.onError(apiError.setCritical(false))
                                finish(false)
                            }
                        }
                        override fun onRequiresUserAction(event: UserActionRequiredEvent) {
                            app.userActionManager.sendToUser(event)
                            finish(false)
                        }
                        override fun onProgress(step: Float) {}
                        override fun onStartProgress(stringRes: Int) {
                            if (!finished.get()) callback.onStartProgress(stringRes)
                        }
                    }
                    val client = Librus(app, profile, login, listener)
                    librus = client
                    continuation.invokeOnCancellation {
                        finished.set(true)
                    }
                    action(client)
                }
            } ?: false
            completed = success
            return success
        } finally {
            if (!completed) librus?.cancel()
            librus = null
        }
    }
}
