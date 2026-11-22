/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-5
 */

package pl.szczodrzynski.edziennik.ui.timetable

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.filterTimetableLessons
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskAllFinishedEvent
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.TimetableImageFormat
import pl.szczodrzynski.edziennik.databinding.DialogGenerateBlockTimetableBinding
import pl.szczodrzynski.edziennik.ext.JsonObject
import pl.szczodrzynski.edziennik.ext.asStrikethroughSpannable
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.getSchoolYearConstrains
import pl.szczodrzynski.edziennik.ext.setText
import pl.szczodrzynski.edziennik.ui.base.dialog.BaseDialog
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.edziennik.utils.models.Week
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.roundToInt

class GenerateBlockTimetableDialog(
    activity: AppCompatActivity,
) : BindingDialog<DialogGenerateBlockTimetableBinding>(activity) {
    companion object {
        private const val TAG = "GenerateBlockTimetableDialog"
        private const val WIDTH_CONSTANT = 70
        private const val WIDTH_WEEKDAY = 285
        private const val WIDTH_SPACING = 15
        private const val HEIGHT_CONSTANT = 60
        private const val HEIGHT_MINUTE = 3
        private const val HEIGHT_FOOTER = 40
    }

    override fun getTitleRes() = R.string.timetable_generate_range
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogGenerateBlockTimetableBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.save
    override fun getNegativeButtonText() = R.string.cancel

    private data class ExportRequest(val weekStart: Date, val weekEnd: Date, val format: TimetableImageFormat)

    private var showProfileName: Boolean = false
    private var showTeachersNames: Boolean = true
    private var noColors: Boolean = false

    private var enqueuedWeekDialog: BaseDialog<*>? = null
    private var pendingExport: ExportRequest? = null
    private var exportJob: Job? = null

    private val weekCurrentStart = Week.getWeekStart()
    private val weekCurrentEnd = Week.getWeekEnd()
    private val weekNextStart = weekCurrentEnd.clone().stepForward(0, 0, 1)
    private val weekNextEnd = weekNextStart.clone().stepForward(0, 0, 6)

    override suspend fun onShow() {
        b.withChangesCurrentWeekRadio.setText(R.string.timetable_generate_current_week_format, weekCurrentStart.formattedStringShort, weekCurrentEnd.formattedStringShort)
        b.withChangesNextWeekRadio.setText(R.string.timetable_generate_next_week_format, weekNextStart.formattedStringShort, weekCurrentEnd.formattedStringShort)

        b.showProfileNameCheckbox.setOnCheckedChangeListener { _, isChecked -> showProfileName = isChecked }
        b.showTeachersNamesCheckbox.setOnCheckedChangeListener { _, isChecked -> showTeachersNames = isChecked }
        b.noColorsCheckbox.setOnCheckedChangeListener { _, isChecked -> noColors = isChecked }
        b.colorBordersCheckbox.isChecked = app.profile.config.ui.timetableColorBorders
        b.imageFormatRadioGroup.check(when (app.config.timetable.exportImageFormat) {
            TimetableImageFormat.PNG -> R.id.imageFormatPngRadio
            TimetableImageFormat.WEBP -> R.id.imageFormatWebpRadio
        })
    }

    override suspend fun onPositiveClick(): Boolean {
        val format = if (b.imageFormatWebpRadio.isChecked) TimetableImageFormat.WEBP else TimetableImageFormat.PNG
        when (b.weekSelectionRadioGroup.checkedRadioButtonId) {
            R.id.withChangesCurrentWeekRadio -> generateBlockTimetable(ExportRequest(weekCurrentStart, weekCurrentEnd, format))
            R.id.withChangesNextWeekRadio -> generateBlockTimetable(ExportRequest(weekNextStart, weekNextEnd, format))
            R.id.forSelectedWeekRadio -> selectDate(format)
        }
        return NO_DISMISS
    }

    private fun selectDate(format: TimetableImageFormat) {
        MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(app.profile.getSchoolYearConstrains())
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    val dateSelected = Date.fromMillisUtc(millis)
                    generateBlockTimetable(ExportRequest(dateSelected.weekStart, dateSelected.weekEnd, format))
                }
            }
            .show(activity.supportFragmentManager, TAG)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskAllFinishedEvent(event: ApiTaskAllFinishedEvent) {
        val request = pendingExport ?: return
        pendingExport = null
        enqueuedWeekDialog?.dismiss()
        enqueuedWeekDialog = null
        generateBlockTimetable(request, canDownload = false)
    }

    private fun generateBlockTimetable(request: ExportRequest, canDownload: Boolean = true) {
        if (exportJob?.isActive == true || pendingExport != null)
            return
        exportJob = launch { exportTimetable(request, canDownload) }
    }

    private suspend fun exportTimetable(request: ExportRequest, canDownload: Boolean) {
        val (weekStart, weekEnd, format) = request
        val profile = app.profile
        val hideClassLessons = profile.config.ui.timetableHideClassLessons
        val colorBorders = b.colorBordersCheckbox.isChecked
        val heightProfileName = if (showProfileName) 100 else 0
        val visibleLessons = withContext(Dispatchers.Default) {
            val profileLessons = app.db.timetableDao().getBetweenDatesNow(weekStart, weekEnd)
                .filter { it.profileId == profile.id }
            filterTimetableLessons(profileLessons, hideClassLessons)
        }
        val lessonRanges = mutableMapOf<Int, Int>()

        var weekDayCount = 5
        var minTime: Time? = null
        var maxTime: Time? = null

        val lessons: List<LessonFull> = visibleLessons.mapNotNull { lesson ->
            if (lesson.type == Lesson.TYPE_NO_LESSONS
                    || lesson.displayDate == null || lesson.displayStartTime == null || lesson.displayEndTime == null)
                return@mapNotNull null

            weekDayCount = maxOf(weekDayCount, lesson.displayDate!!.weekDay + 1)

            lessonRanges[lesson.displayStartTime!!.value] = lesson.displayEndTime!!.value

            if (minTime == null || lesson.displayStartTime!! < minTime!!) {
                minTime = lesson.displayStartTime!!.clone()
            }

            if (maxTime == null || lesson.displayEndTime!! > maxTime!!) {
                maxTime = lesson.displayEndTime!!.clone()
            }

            return@mapNotNull lesson
        }

        if (lessons.isEmpty()) {
            if (!canDownload) {
                Toast.makeText(activity, R.string.timetable_generate_no_lessons, Toast.LENGTH_LONG).show()
                return
            }
            enqueuedWeekDialog = SimpleDialog<Unit>(activity) {
                title(R.string.please_wait)
                message(R.string.timetable_syncing_text)
                cancelable(false)
            }.show()

            pendingExport = request

            EdziennikTask.syncProfile(
                    profileId = profile.id,
                    featureTypes = setOf(FeatureType.TIMETABLE),
                    arguments = JsonObject(
                            "weekStart" to weekStart.stringY_m_d
                    )
            ).enqueue(activity)
            return
        }

        val progressDialog = SimpleDialog<Unit>(activity) {
            title(R.string.timetable_generate_progress_title)
            message(R.string.timetable_generate_progress_text)
            cancelable(false)
        }.show()

        if (minTime == null) {
            progressDialog.dismiss()
            // TODO: Toast
            return
        }

        val uri = withContext(Dispatchers.Default) {

            val diff = Time.diff(maxTime, minTime)

            val imageWidth = WIDTH_CONSTANT + weekDayCount * (WIDTH_WEEKDAY + WIDTH_SPACING) - WIDTH_SPACING
            val imageHeight = heightProfileName + HEIGHT_CONSTANT + diff.inMinutes * HEIGHT_MINUTE + HEIGHT_FOOTER
            val bitmap = Bitmap.createBitmap(imageWidth + 20, imageHeight + 30, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (noColors) canvas.drawARGB(255, 255, 255, 255)
            else canvas.drawARGB(255, 225, 225, 225)

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            val timePaint = Paint(paint).apply {
                color = 0xff7f7f7f.toInt()
                textAlign = Paint.Align.CENTER
                textSize = 16f
            }
            val linePaint = Paint(paint).apply {
                color = 0xff646464.toInt()
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            val timeTextCenter = ((timePaint.descent() + timePaint.ascent()) / 2).roundToInt()
            val minTimeMinutes = minTime!!.inMinutes

            // Draw the grid first so the lesson cards cover the lines.
            lessonRanges.forEach { (startTime, endTime) ->
                listOf(startTime, endTime).forEach { value ->
                    val hour = value / 10000
                    val minute = (value / 100) % 100
                    val time = Time(hour, minute, 0)
                    val firstOffset = time.inMinutes - minTimeMinutes
                    val top = (heightProfileName + HEIGHT_CONSTANT + firstOffset * HEIGHT_MINUTE).toFloat()

                    canvas.drawText(time.stringHM, WIDTH_CONSTANT / 2f, top - timeTextCenter, timePaint)
                    canvas.drawLine(WIDTH_CONSTANT.toFloat(), top, imageWidth.toFloat(), top, linePaint)
                }
            }

            lessons.forEach { lesson ->
                val lessonLength = Time.diff(lesson.displayEndTime, lesson.displayStartTime)
                val firstOffset = Time.diff(lesson.displayStartTime, minTime)
                val lessonWeekDay = lesson.displayDate!!.weekDay

                val left = WIDTH_CONSTANT + lessonWeekDay * (WIDTH_WEEKDAY + WIDTH_SPACING)
                val top = heightProfileName + HEIGHT_CONSTANT + firstOffset.inMinutes * HEIGHT_MINUTE

                val blockWidth = WIDTH_WEEKDAY
                val blockHeight = lessonLength.inMinutes * HEIGHT_MINUTE

                val viewWidth = 380.dp
                val viewHeight = lessonLength.inMinutes * 4.dp

                val layout = activity.layoutInflater.inflate(R.layout.row_timetable_block_item, null) as LinearLayout

                val item: LinearLayout = layout.findViewById(R.id.timetableItemLayout)
                val card: CardView = layout.findViewById(R.id.timetableItemCard)
                val subjectName: TextView = layout.findViewById(R.id.timetableItemSubjectName)
                val classroomName: TextView = layout.findViewById(R.id.timetableItemClassroomName)
                val teacherName: TextView = layout.findViewById(R.id.timetableItemTeacherName)
                val teamName: TextView = layout.findViewById(R.id.timetableItemTeamName)

                if (noColors) {
                    card.setCardBackgroundColor(Color.WHITE)
                    card.cardElevation = 0f
                    subjectName.setTextColor(Color.BLACK)
                    classroomName.setTextColor(0xffaaaaaa.toInt())
                    teacherName.setTextColor(0xffaaaaaa.toInt())
                    teamName.setTextColor(0xffaaaaaa.toInt())
                }

                val borderColor = when {
                    colorBorders -> lesson.color
                        ?: app.timetableManager.getSubjectColor(lesson.displaySubjectId, lesson.displaySubjectName)
                    noColors -> Color.BLACK
                    else -> null
                }
                if (borderColor != null) {
                    item.background = GradientDrawable().apply {
                        cornerRadius = card.radius
                        setStroke(6.dp, borderColor)
                    }
                }

                subjectName.text = lesson.displaySubjectName ?: ""
                classroomName.text = lesson.displayClassroom ?: ""
                teacherName.text = lesson.displayTeacherName ?: ""
                teamName.text = lesson.displayTeamName ?: ""

                if (!showTeachersNames) teacherName.visibility = View.GONE

                when (lesson.type) {
                    Lesson.TYPE_NORMAL -> Unit
                    Lesson.TYPE_CANCELLED, Lesson.TYPE_SHIFTED_SOURCE -> {
                        if (!noColors) {
                            card.setCardBackgroundColor(Color.BLACK)
                            subjectName.setTextColor(Color.WHITE)
                        }
                        subjectName.text = lesson.displaySubjectName?.asStrikethroughSpannable()
                                ?: ""
                    }
                    else -> {
                        if (!noColors) {
                            card.setCardBackgroundColor(0xff234158.toInt())
                            subjectName.setTextColor(Color.WHITE)
                        }
                        subjectName.setTypeface(null, Typeface.BOLD_ITALIC)
                    }
                }

                layout.isDrawingCacheEnabled = true
                layout.measure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY))
                layout.layout(0, 0, layout.measuredWidth, layout.measuredHeight)
                layout.buildDrawingCache(true)

                val itemBitmap = layout.drawingCache
                canvas.drawBitmap(itemBitmap, null, Rect(left, top, left + blockWidth, top + blockHeight), paint)
            }

            val textPaint = Paint().apply {
                setARGB(255, 0, 0, 0)
                textAlign = Paint.Align.CENTER
                textSize = 30f
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            for (w in 0 until weekDayCount) {
                val x = WIDTH_CONSTANT + w * WIDTH_WEEKDAY + w * WIDTH_SPACING
                canvas.drawText(Week.getFullDayName(w), x + (WIDTH_WEEKDAY / 2f), heightProfileName + HEIGHT_CONSTANT / 2 + 10f, textPaint)
            }

            if (showProfileName) {
                textPaint.textSize = 50f
                canvas.drawText("${profile.name} - plan lekcji, ${weekStart.formattedStringShort} - ${weekEnd.formattedStringShort}", (imageWidth + 20) / 2f, 80f, textPaint)
            }

            textPaint.apply {
                setARGB(128, 0, 0, 0)
                textAlign = Paint.Align.RIGHT
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }

            val footerTextPaintCenter = ((textPaint.descent() + textPaint.ascent()) / 2).roundToInt()
            canvas.drawText("Wygenerowano w aplikacji Szkolny.eu", imageWidth - 10f, imageHeight - footerTextPaintCenter - 10f, textPaint)

            val today = Date.getToday().stringY_m_d
            val now = Time.getNow().stringH_M_S
            val filename = "plan_lekcji_${profile.name}_${today}_${now}.${format.extension}"
            saveImage(bitmap, filename, format)
        }

        progressDialog.dismiss()
        if (uri == null) {
            Toast.makeText(activity, R.string.timetable_generate_save_error, Toast.LENGTH_LONG).show()
            return
        }
        app.config.timetable.exportImageFormat = format
        SimpleDialog<Unit>(activity) {
            title(R.string.timetable_generate_success_title)
            message(R.string.timetable_generate_success_text)
            positive(R.string.share) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = format.mimeType
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(
                    Intent.createChooser(
                        intent,
                        activity.getString(R.string.share_intent)
                    )
                )
            }
            negative(R.string.open) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, format.mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(intent)
            }
            neutral(R.string.do_nothing)
        }.showModal()
        dismiss()
    }

    private fun saveImage(bitmap: Bitmap, filename: String, format: TimetableImageFormat): Uri? {
        val resolver = activity.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, File(Environment.DIRECTORY_PICTURES, "Szkolny.eu").path)
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val picturesDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Szkolny.eu")
            picturesDirectory.mkdirs()
            values.put(MediaStore.MediaColumns.DATA, File(picturesDirectory, filename).path)
        }

        var insertedUri: Uri? = null
        return try {
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            insertedUri = uri
            if (resolver.openOutputStream(uri)?.use { format.compress(bitmap, it) } != true)
                throw IOException("Could not write timetable image")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (resolver.update(uri, published, null, null) <= 0)
                    throw IOException("Could not publish timetable image")
            }
            uri
        } catch (e: Exception) {
            Timber.e(e)
            insertedUri?.let { uri ->
                runCatching { resolver.delete(uri, null, null) }.onFailure { Timber.e(it) }
            }
            null
        }
    }
}
