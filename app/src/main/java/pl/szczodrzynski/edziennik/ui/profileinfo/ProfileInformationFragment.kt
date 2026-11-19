package pl.szczodrzynski.edziennik.ui.profileinfo

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AtomicFile
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.*
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.Librus
import pl.szczodrzynski.edziennik.data.api.events.UserActionRequiredEvent
import pl.szczodrzynski.edziennik.data.api.interfaces.EdziennikCallback
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.databinding.FragmentProfileInformationBinding
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.minutes

class ProfileInformationFragment : BaseFragment<FragmentProfileInformationBinding, MainActivity>(FragmentProfileInformationBinding::inflate) {
    override fun getScrollingView() = b.root
    override fun hasCustomRefresh() = true
    private var model: JsonObject? = null
    private var loading = false
    private var pendingPhoto: ByteArray? = null
    private var client: Librus? = null
    private val savePhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val bytes = pendingPhoto
        pendingPhoto = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && bytes != null) launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { requireNotNull(activity.contentResolver.openOutputStream(uri)).use { it.write(bytes) } }.isSuccess
            }
            Toast.makeText(activity, if (success) R.string.profile_information_saved else R.string.profile_information_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun cache() = AtomicFile(File(app.filesDir, "profile-information-${App.profileId}.json"))

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        b.download.icon = CommunityMaterial.Icon.cmd_download_outline.toDrawable(activity)
        b.download.enableExpressiveShapeMorph()
        b.download.setOnClickListener { launch { refresh() } }
        model = withContext(Dispatchers.IO) {
            runCatching { cache().openRead().bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject } }.getOrNull()
        }
        render()
        b.sections.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) arrangeSections()
        }
    }

    override suspend fun refresh() {
        if (loading || app.profile.loginStoreType != LoginType.LIBRUS || app.profile.archived) {
            activity.swipeRefreshLayout.isRefreshing = false
            return
        }
        val profileId = App.profileId
        val cacheFile = cache()
        loading = true
        canRefreshDisabled = true
        b.progress.isVisible = true
        b.download.isEnabled = false
        try {
            val result = withContext(Dispatchers.IO) {
                val profile = requireNotNull(app.db.profileDao().getByIdNow(profileId))
                val login = requireNotNull(app.db.loginStoreDao().getByIdNow(profile.loginStoreId))
                withTimeoutOrNull(1.minutes) {
                    suspendCancellableCoroutine<JsonObject> { continuation ->
                        val finished = AtomicBoolean()
                        fun fail(error: Throwable) {
                            if (finished.compareAndSet(false, true)) continuation.resumeWithException(error)
                        }
                        val listener = object : EdziennikCallback {
                            override fun onCompleted() {}
                            override fun onProgress(step: Float) {}
                            override fun onStartProgress(stringRes: Int) {}
                            override fun onError(apiError: ApiError) = fail(InformationException(apiError))
                            override fun onRequiresUserAction(event: UserActionRequiredEvent) {
                                app.userActionManager.sendToUser(event)
                                fail(IOException("User action required"))
                            }
                        }
                        val current = Librus(app, profile, login, listener)
                        client = current
                        continuation.invokeOnCancellation { finished.set(true); current.data.cancelled = true }
                        current.getProfileInformation {
                            if (finished.compareAndSet(false, true)) continuation.resume(it)
                        }
                    }
                } ?: throw IOException("Profile information request timed out")
            }
            withContext(Dispatchers.IO) {
                check(app.db.profileDao().getByIdNow(profileId) != null)
                val stream = cacheFile.startWrite()
                try {
                    stream.write(result.toString().toByteArray(Charsets.UTF_8))
                    cacheFile.finishWrite(stream)
                    if (app.db.profileDao().getByIdNow(profileId) == null) cacheFile.delete()
                } catch (e: Exception) {
                    cacheFile.failWrite(stream)
                    throw e
                }
            }
            model = result
            render()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            b.status.text = getString(R.string.profile_information_error)
            if (e is InformationException) activity.errorSnackbar.addError(e.error.setCritical(false)).show()
        } finally {
            client?.data?.cancelled = true
            client = null
            loading = false
            canRefreshDisabled = false
            b.progress.isVisible = false
            b.download.isEnabled = !app.profile.archived
            if (isResumed && !isHidden) activity.swipeRefreshLayout.isRefreshing = false
        }
    }

    private class InformationException(val error: ApiError) : IOException()

    private fun render() {
        b.sections.removeAllViews()
        b.download.isEnabled = !app.profile.archived
        val data = model
        b.illustration.isVisible = data == null
        b.content.gravity = if (data == null) Gravity.CENTER else Gravity.TOP
        b.status.gravity = if (data == null) Gravity.CENTER else Gravity.START
        if (data == null) {
            b.status.setText(R.string.profile_information_not_downloaded)
            return
        }
        b.status.text = getString(R.string.profile_information_updated,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(data.getLong("downloadedAt") ?: 0)))
        val school = data.getJsonObject("school")
        val schoolClass = data.getJsonObject("schoolClass")
        val schoolCard = section(R.string.profile_information_school)
        field(schoolCard, R.string.profile_information_school_name, school.getString("Name"))
        val street = listOfNotNull(school.getString("Street"), school.getString("BuildingNumber")).joinToString(" ") +
            (school.getString("ApartmentNumber")?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "")
        val city = listOfNotNull(school.getString("PostCode"), school.getString("Town")).joinToString(" ")
        field(schoolCard, R.string.profile_information_address, listOf(street, city).filter { it.isNotBlank() }.joinToString("\n"))
        field(schoolCard, R.string.profile_information_region, school.getString("State"))
        field(schoolCard, R.string.profile_information_director,
            listOfNotNull(school.getString("NameHeadTeacher"), school.getString("SurnameHeadTeacher")).joinToString(" "))
        field(schoolCard, R.string.profile_information_phone, school.getString("PhoneNumber"), true)
        field(schoolCard, R.string.profile_information_email, school.getString("Email"), true)
        field(schoolCard, R.string.profile_information_class, schoolClass?.let { "${it.getInt("Number") ?: ""}${it.getString("Symbol").orEmpty()}" })
        val tutors = data.getJsonObject("tutors")
        val users = tutors.getJsonArray("Users")?.map { it.asJsonObject } ?: listOfNotNull(tutors.getJsonObject("User"))
        field(schoolCard, R.string.profile_information_tutors, users.map { listOfNotNull(it.getString("FirstName"), it.getString("LastName")).joinToString(" ") }.joinToString("\n"))
        school.getInt("SchoolYear")?.let { field(schoolCard, R.string.profile_information_year, "$it/${it + 1}") }
        field(schoolCard, R.string.profile_information_year_start, date(schoolClass.getString("BeginSchoolYear")))
        field(schoolCard, R.string.profile_information_semester_end, date(schoolClass.getString("EndFirstSemester")))
        field(schoolCard, R.string.profile_information_year_end, date(schoolClass.getString("EndSchoolYear")))
        val photos = data.getJsonObject("photos")
        val photoCard = section(R.string.profile_information_photo)
        if (photos == null) field(photoCard, R.string.profile_information_status, getString(R.string.profile_information_unavailable))
        else {
            val accepted = photos.getJsonObject("photo")
            val awaiting = photos.getJsonObject("awaitingPhoto")
            if (accepted == null && awaiting == null) field(photoCard, R.string.profile_information_status, status(photos.getString("status")))
            accepted?.let { photo(photoCard, it, if (awaiting == null) photos.getString("status") else "accepted") }
            awaiting?.let { photo(photoCard, it, photos.getString("status")) }
        }
        arrangeSections()
    }

    private fun date(value: String?): String? = value?.let { runCatching { pl.szczodrzynski.edziennik.utils.models.Date.fromY_m_d(it).formattedString }.getOrDefault(it) }

    private fun status(value: String?) = getString(when (value) {
        "accepted" -> R.string.profile_information_accepted
        "awaiting_approval", "change_request" -> R.string.profile_information_awaiting
        "removal_request" -> R.string.profile_information_removal
        "rejected" -> R.string.profile_information_rejected
        "not_uploaded" -> R.string.profile_information_no_photo
        else -> R.string.profile_information_unknown
    })

    private fun photo(parent: LinearLayout, photo: JsonObject, state: String?) {
        field(parent, R.string.profile_information_status, status(state))
        val bytes = runCatching { Base64.decode(photo.getString("content")?.substringAfter(","), Base64.DEFAULT) }.getOrNull()
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap != null) {
            val image = ImageView(activity).apply {
                adjustViewBounds = true; maxHeight = 240.dp; scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bitmap); contentDescription = getString(R.string.profile_information_photo)
                setOnClickListener { showPhotoPreview(bitmap) }
            }
            parent.addView(image, LinearLayout.LayoutParams(-1, -2))
            parent.addView(MaterialButton(activity).apply {
                setText(R.string.profile_information_download_photo)
                icon = CommunityMaterial.Icon.cmd_download.toDrawable(activity)
                enableExpressiveShapeMorph()
                setOnClickListener {
                    pendingPhoto = bytes
                    val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
                    val filename = photo.getString("fileName")?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
                        ?: if (png) "photo.png" else "photo.jpg"
                    savePhoto.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = if (png) "image/png" else "image/jpeg"
                        putExtra(Intent.EXTRA_TITLE, filename)
                    })
                }
            })
        }
        field(parent, R.string.profile_information_added, photo.getString("addedDate"))
        val person = photo.getJsonObject("addingPerson")
        field(parent, R.string.profile_information_added_by, listOfNotNull(person.getString("name"), person.getString("surname")).joinToString(" "))
        field(parent, R.string.profile_information_source, getString(when (photo.getString("source")) {
            "skr" -> R.string.profile_information_secretariat
            "apk" -> R.string.profile_information_librus_app
            "uro" -> R.string.profile_information_uro
            else -> R.string.profile_information_unknown
        }))
    }

    private fun showPhotoPreview(bitmap: Bitmap) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val surface = R.attr.colorSurface.resolveAttr(activity)
        val toolbar = MaterialToolbar(activity).apply {
            setTitle(R.string.profile_information_photo)
            navigationIcon = CommunityMaterial.Icon.cmd_close.toDrawable(activity, colorAttr = R.attr.colorPrimary)
            setNavigationContentDescription(R.string.close)
            setNavigationOnClickListener { dialog.dismiss() }
        }
        val image = PhotoPreviewView(activity, bitmap).apply {
            contentDescription = getString(R.string.profile_information_photo)
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val toolbarParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp)
            if (!app.config.ui.toolbarAtBottom) addView(toolbar, toolbarParams)
            addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            if (app.config.ui.toolbarAtBottom) addView(toolbar, toolbarParams)
        }
        dialog.setContentView(layout)
        dialog.window?.setBackgroundDrawable(ColorDrawable(surface))
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun section(title: Int): LinearLayout {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(MaterialColors.getColor(this, R.attr.colorSurfaceContainer))
            radius = 24.dp.toFloat(); strokeWidth = 0
        }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dp, 16.dp, 16.dp, 16.dp) }
        content.addView(TextView(activity).apply { setText(title); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge) })
        card.addView(content); b.sections.addView(card)
        return content
    }

    private fun field(parent: LinearLayout, label: Int, value: String?, links: Boolean = false) {
        if (value.isNullOrBlank()) return
        parent.addView(TextView(activity).apply {
            setText(label); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTextColor(MaterialColors.getColor(this, R.attr.colorSecondary))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp })
        parent.addView(TextView(activity).apply {
            if (links) autoLinkMask = android.text.util.Linkify.EMAIL_ADDRESSES or android.text.util.Linkify.PHONE_NUMBERS
            text = value; setTextIsSelectable(!links)
        })
    }

    private fun arrangeSections() {
        val wide = b.sections.width >= 600.dp
        b.sections.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (i in 0 until b.sections.childCount) b.sections.getChildAt(i).layoutParams =
            LinearLayout.LayoutParams(if (wide) 0 else -1, ViewGroup.LayoutParams.WRAP_CONTENT, if (wide) 1f else 0f).apply {
                if (i > 0) { if (wide) marginStart = 12.dp else topMargin = 12.dp }
            }
    }
}
