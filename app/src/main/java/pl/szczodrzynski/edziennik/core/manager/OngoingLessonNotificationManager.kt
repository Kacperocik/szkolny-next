package pl.szczodrzynski.edziennik.core.manager

import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time

object OngoingLessonNotificationManager {
    private const val NOTIFICATION_ID = 15000
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    fun update(app: App) {
        CoroutineScope(Dispatchers.IO).launch {
            updateInternal(app)
        }
    }

    private fun updateInternal(app: App) {
        val enabled = app.profile.config.sync.ongoingLessonNotification
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

        if (!enabled || app.profile.id < 0) {
            notificationManager.cancel(NOTIFICATION_ID)
            stopTimer()
            return
        }

        val today = Date.getToday()
        val lessonsAll = app.db.timetableDao().getAllForDateNow(app.profile.id, today)
        val lessons = lessonsAll.filter { !it.isCancelled && it.type != Lesson.TYPE_NO_LESSONS }

        if (lessons.isEmpty()) {
            notificationManager.cancel(NOTIFICATION_ID)
            stopTimer()
            return
        }

        val firstLesson = lessons.minByOrNull { it.displayStartTime?.inMillis ?: Long.MAX_VALUE }
        val lastLesson = lessons.maxByOrNull { it.displayEndTime?.inMillis ?: Long.MIN_VALUE }

        val firstStartTime = firstLesson?.displayStartTime ?: return
        val lastEndTime = lastLesson?.displayEndTime ?: return

        // windowStart: 10 minutes before first lesson start
        val windowStart = firstStartTime.inMillis - 10 * 60 * 1000
        // windowEnd: 10 minutes after last lesson end
        val windowEnd = lastEndTime.inMillis + 10 * 60 * 1000

        val nowMillis = Time.getNow().inMillis

        if (nowMillis in windowStart..windowEnd) {
            showNotification(app, notificationManager, lessons)
            scheduleTimer(app)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
            stopTimer()
        }
    }

    private fun showNotification(
        app: App,
        notificationManager: AndroidNotificationManager,
        lessons: List<LessonFull>
    ) {
        val nowTime = Time.getNow()
        val nowMillis = nowTime.inMillis

        val currentLesson = lessons.firstOrNull {
            val start = it.displayStartTime?.inMillis ?: Long.MAX_VALUE
            val end = it.displayEndTime?.inMillis ?: Long.MIN_VALUE
            nowMillis in start..end
        }

        val title: String
        val body: String
        val subText: String?

        if (currentLesson != null) {
            val subject = currentLesson.displaySubjectName ?: ""
            val classroom = currentLesson.displayClassroom?.takeIf { it.isNotBlank() }
            val subjectWithClassroom = if (classroom != null) "$subject [$classroom]" else subject

            val endTimeStr = currentLesson.displayEndTime?.stringHM ?: ""
            val nextLesson = lessons.firstOrNull {
                (it.displayStartTime?.inMillis ?: 0L) > (currentLesson.displayEndTime?.inMillis ?: 0L)
            }

            val nextText = if (nextLesson != null) {
                val nextSubj = nextLesson.displaySubjectName ?: ""
                val nextRoom = nextLesson.displayClassroom?.takeIf { it.isNotBlank() }
                if (nextRoom != null) {
                    app.getString(R.string.ongoing_lesson_next_classroom_format, nextSubj, nextRoom)
                } else {
                    app.getString(R.string.ongoing_lesson_next_format, nextSubj)
                }
            } else ""

            val nowText = app.getString(R.string.ongoing_lesson_now_format, subjectWithClassroom)
            val timeText = if (endTimeStr.isNotBlank()) "(do $endTimeStr)" else ""

            title = listOfNotNull(
                listOfNotNull(nowText, timeText.takeIf { it.isNotBlank() }).joinToString(" "),
                nextText.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            body = listOfNotNull(
                if (endTimeStr.isNotBlank()) "Do $endTimeStr" else null,
                nextText.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            subText = nextText.takeIf { it.isNotBlank() }

        } else {
            val nextLesson = lessons.firstOrNull {
                (it.displayStartTime?.inMillis ?: 0L) > nowMillis
            }

            if (nextLesson != null) {
                val nextStart = nextLesson.displayStartTime
                val nextStartStr = nextStart?.stringHM ?: ""
                val nextSubj = nextLesson.displaySubjectName ?: ""
                val nextRoom = nextLesson.displayClassroom?.takeIf { it.isNotBlank() }
                val nextSubjWithRoom = if (nextRoom != null) "$nextSubj [$nextRoom]" else nextSubj

                val minsRemaining = if (nextStart != null) {
                    ((nextStart.inMillis - nowMillis) / (60 * 1000)).coerceAtLeast(1)
                } else 0

                val firstLesson = lessons.minByOrNull { it.displayStartTime?.inMillis ?: Long.MAX_VALUE }
                if (nextLesson == firstLesson && nowMillis < (firstLesson?.displayStartTime?.inMillis ?: 0L)) {
                    val firstText = app.getString(R.string.ongoing_lesson_first_format, minsRemaining, nextStartStr)
                    title = "$firstText • $nextSubjWithRoom"
                    body = nextSubjWithRoom
                    subText = nextSubjWithRoom
                } else {
                    val breakText = app.getString(R.string.ongoing_lesson_break_format, nextStartStr)
                    val nextText = app.getString(R.string.ongoing_lesson_next_format, nextSubjWithRoom)
                    title = "$breakText • $nextText"
                    body = nextText
                    subText = nextText
                }
            } else {
                val tomorrow = Date.getToday().clone().stepForward(0, 0, 1)
                val tomorrowLessonsAll = app.db.timetableDao().getAllForDateNow(app.profile.id, tomorrow)
                val tomorrowLessons = tomorrowLessonsAll.filter { !it.isCancelled && it.type != Lesson.TYPE_NO_LESSONS }

                if (tomorrowLessons.isNotEmpty()) {
                    val lessonCount = tomorrowLessons.size
                    title = app.resources.getQuantityString(R.plurals.ongoing_lesson_finished_title_format, lessonCount, lessonCount)

                    val firstTomorrow = tomorrowLessons.minByOrNull { it.displayStartTime?.inMillis ?: Long.MAX_VALUE }
                    val subj = firstTomorrow?.displaySubjectName ?: ""
                    val room = firstTomorrow?.displayClassroom?.takeIf { it.isNotBlank() }
                    val subjWithRoom = if (room != null) "$subj [$room]" else subj
                    val time = firstTomorrow?.displayStartTime?.stringHM ?: ""

                    body = app.getString(R.string.ongoing_lesson_finished_body_format, subjWithRoom, time)
                    subText = body
                } else {
                    title = app.getString(R.string.ongoing_lesson_finished_title_no_lessons)
                    body = app.getString(R.string.ongoing_lesson_finished_body_no_lessons)
                    subText = null
                }
            }
        }

        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtras(
                "fragmentId" to NavTarget.TIMETABLE,
                "timetableDate" to Date.getToday().stringY_m_d
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = app.notificationManager.ongoingLesson.key

        val progress: Int? = if (currentLesson != null) {
            val start = currentLesson.displayStartTime?.inMillis ?: 0L
            val end = currentLesson.displayEndTime?.inMillis ?: 0L
            if (end > start) {
                (((nowMillis - start).toDouble() / (end - start).toDouble()) * 100).toInt().coerceIn(0, 100)
            } else null
        } else {
            val nextLesson = lessons.firstOrNull {
                (it.displayStartTime?.inMillis ?: 0L) > nowMillis
            }
            val prevLesson = lessons.filter { (it.displayEndTime?.inMillis ?: Long.MAX_VALUE) <= nowMillis }
                .maxByOrNull { it.displayEndTime?.inMillis ?: Long.MIN_VALUE }
            val breakStart = prevLesson?.displayEndTime?.inMillis
            val breakEnd = nextLesson?.displayStartTime?.inMillis
            if (breakStart != null && breakEnd != null && breakEnd > breakStart && nowMillis in breakStart..breakEnd) {
                (((nowMillis - breakStart).toDouble() / (breakEnd - breakStart).toDouble()) * 100).toInt().coerceIn(0, 100)
            } else null
        }

        val notification = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_notification_timetable)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .apply {
                if (!subText.isNullOrBlank()) {
                    setSubText(subText)
                }
                if (progress != null) {
                    setProgress(100, progress, false)
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun scheduleTimer(app: App) {
        stopTimer()
        updateRunnable = Runnable {
            update(app)
        }
        handler.postDelayed(updateRunnable!!, 30_000L)
    }

    private fun stopTimer() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }
}
