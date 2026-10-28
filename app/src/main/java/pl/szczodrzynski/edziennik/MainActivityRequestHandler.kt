/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-23.
 */

package pl.szczodrzynski.edziennik

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.ui.crop.AppCropImageActivity
import pl.szczodrzynski.edziennik.ui.login.LoginActivity
import pl.szczodrzynski.edziennik.utils.Utils
import java.io.File
import java.io.FileOutputStream

class MainActivityRequestHandler(val hostActivity: MainActivity) {
    val activity get() = hostActivity

    companion object {
        private const val REQUEST_LOGIN_ACTIVITY = 2000
        private const val REQUEST_FILE_HEADER_BACKGROUND = 3000
        private const val REQUEST_FILE_APP_BACKGROUND = 4000
        private const val REQUEST_FILE_PROFILE_IMAGE = 5000
        private const val REQUEST_CROP_HEADER_BACKGROUND = 3100
        private const val REQUEST_CROP_APP_BACKGROUND = 4100
        private const val REQUEST_CROP_PROFILE_IMAGE = 5100
    }

    private val app = hostActivity.app
    private val requestData = mutableMapOf<Int, Any?>()
    private val listeners = mutableMapOf<Int, (data: Any?) -> Unit>()

    private val manager
        get() = app.permissionManager

    fun requestLogin() = hostActivity.startActivityForResult(
        Intent(hostActivity, LoginActivity::class.java),
        REQUEST_LOGIN_ACTIVITY
    )

    private fun startImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        val chooser = Intent.createChooser(intent, hostActivity.getString(R.string.choose_file))
        hostActivity.startActivityForResult(chooser, requestCode)
    }

    fun requestHeaderBackground(listener: (Any?) -> Unit) =
        manager.requestCameraPermission(
            hostActivity, 0, isRequired = false
        ) {
            listeners[REQUEST_FILE_HEADER_BACKGROUND] = listener
            startImagePicker(REQUEST_FILE_HEADER_BACKGROUND)
        }

    fun requestAppBackground(listener: (Any?) -> Unit) =
        manager.requestCameraPermission(
            hostActivity, 0, isRequired = false
        ) {
            listeners[REQUEST_FILE_APP_BACKGROUND] = listener
            startImagePicker(REQUEST_FILE_APP_BACKGROUND)
        }

    fun requestProfileImage(profile: Profile, listener: (Any?) -> Unit) =
        manager.requestCameraPermission(
            hostActivity, 0, isRequired = false
        ) {
            listeners[REQUEST_FILE_PROFILE_IMAGE] = listener
            requestData[REQUEST_FILE_PROFILE_IMAGE] = profile
            startImagePicker(REQUEST_FILE_PROFILE_IMAGE)
        }

    private fun getFileInfo(uri: Uri): Pair<String, String?> {
        if (uri.scheme == "file") {
            return (uri.lastPathSegment ?: "unknown") to null
        }
        val cursor = hostActivity.contentResolver.query(
            uri,
            null,
            null,
            null,
            null,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                val mimeIndex = it.getColumnIndex("mime_type")
                val mimeType = if (mimeIndex != -1) it.getString(mimeIndex) else null

                name to mimeType
            } else
                null
        } ?: "unknown" to null
    }

    private fun shouldCrop(uri: Uri): Boolean {
        val (filename, mimeType) = getFileInfo(uri)
        return !filename.endsWith(".gif") && mimeType?.endsWith("/gif") != true
    }

    private fun saveFile(uri: Uri, name: String): String {
        val (filename, _) = getFileInfo(uri)
        val extension = filename.substringAfterLast('.')
        val file = File(hostActivity.filesDir, "$name.$extension")
        activity.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    private fun createCropIntent(uri: Uri, options: CropImageOptions): Intent {
        return Intent(hostActivity, AppCropImageActivity::class.java).apply {
            putExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE, Bundle().apply {
                putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, uri)
                putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, options)
            })
        }
    }

    @Suppress("DEPRECATION")
    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK)
            return
        var uri = data?.data
        when (requestCode) {
            REQUEST_LOGIN_ACTIVITY -> {
                if (!app.config.loginFinished)
                    hostActivity.finish()
                else {
                    hostActivity.handleIntent(data?.extras)
                }
            }
            REQUEST_FILE_HEADER_BACKGROUND -> {
                if (uri == null)
                    return
                if (shouldCrop(uri)) {
                    val options = CropImageOptions().apply {
                        aspectRatioX = 512
                        aspectRatioY = 288
                        fixAspectRatio = true
                        guidelines = CropImageView.Guidelines.ON_TOUCH
                        allowFlipping = true
                        allowRotation = true
                        allowCounterRotation = true
                        activityBackgroundColor = Utils.getAttr(hostActivity, android.R.attr.colorBackground)
                        activityMenuIconColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarColor = Utils.getAttr(hostActivity, R.attr.colorSurface)
                        toolbarTitleColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarBackButtonColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                    }
                    val intent = createCropIntent(uri, options)
                    hostActivity.startActivityForResult(intent, REQUEST_CROP_HEADER_BACKGROUND)
                } else {
                    val path = saveFile(uri, "header")
                    app.config.ui.headerBackground = path
                    listeners.remove(REQUEST_FILE_HEADER_BACKGROUND)?.invoke(path)
                }
            }
            REQUEST_FILE_APP_BACKGROUND -> {
                if (uri == null)
                    return
                if (shouldCrop(uri)) {
                    val options = CropImageOptions().apply {
                        guidelines = CropImageView.Guidelines.ON_TOUCH
                        allowFlipping = true
                        allowRotation = true
                        allowCounterRotation = true
                        activityBackgroundColor = Utils.getAttr(hostActivity, android.R.attr.colorBackground)
                        activityMenuIconColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarColor = Utils.getAttr(hostActivity, R.attr.colorSurface)
                        toolbarTitleColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarBackButtonColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                    }
                    val intent = createCropIntent(uri, options)
                    hostActivity.startActivityForResult(intent, REQUEST_CROP_APP_BACKGROUND)
                } else {
                    val path = saveFile(uri, "background")
                    app.config.ui.appBackground = path
                    listeners.remove(REQUEST_FILE_APP_BACKGROUND)?.invoke(path)
                }
            }
            REQUEST_FILE_PROFILE_IMAGE -> {
                if (uri == null)
                    return
                if (shouldCrop(uri)) {
                    val options = CropImageOptions().apply {
                        aspectRatioX = 1
                        aspectRatioY = 1
                        fixAspectRatio = true
                        cropShape = CropImageView.CropShape.OVAL
                        guidelines = CropImageView.Guidelines.ON_TOUCH
                        allowFlipping = true
                        allowRotation = true
                        allowCounterRotation = true
                        activityBackgroundColor = Utils.getAttr(hostActivity, android.R.attr.colorBackground)
                        activityMenuIconColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarColor = Utils.getAttr(hostActivity, R.attr.colorSurface)
                        toolbarTitleColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                        toolbarBackButtonColor = Utils.getAttr(hostActivity, R.attr.colorOnSurface)
                    }
                    val intent = createCropIntent(uri, options)
                    hostActivity.startActivityForResult(intent, REQUEST_CROP_PROFILE_IMAGE)
                } else {
                    val profile =
                        requestData.remove(REQUEST_FILE_PROFILE_IMAGE) as? Profile ?: return
                    val path = saveFile(uri, "profile${profile.id}")
                    profile.image = path
                    listeners.remove(REQUEST_FILE_PROFILE_IMAGE)?.invoke(profile)
                }
            }
            REQUEST_CROP_HEADER_BACKGROUND -> {
                val cropResult = data?.getParcelableExtra<CropImage.ActivityResult>(CropImage.CROP_IMAGE_EXTRA_RESULT)
                uri = cropResult?.uriContent ?: return
                val path = saveFile(uri, "header")
                app.config.ui.headerBackground = path
                listeners.remove(REQUEST_FILE_HEADER_BACKGROUND)?.invoke(path)
            }
            REQUEST_CROP_APP_BACKGROUND -> {
                val cropResult = data?.getParcelableExtra<CropImage.ActivityResult>(CropImage.CROP_IMAGE_EXTRA_RESULT)
                uri = cropResult?.uriContent ?: return
                val path = saveFile(uri, "background")
                app.config.ui.appBackground = path
                listeners.remove(REQUEST_FILE_APP_BACKGROUND)?.invoke(path)
            }
            REQUEST_CROP_PROFILE_IMAGE -> {
                val cropResult = data?.getParcelableExtra<CropImage.ActivityResult>(CropImage.CROP_IMAGE_EXTRA_RESULT)
                uri = cropResult?.uriContent ?: return
                val profile = requestData.remove(REQUEST_FILE_PROFILE_IMAGE) as? Profile ?: return
                val path = saveFile(uri, "profile${profile.id}")
                profile.image = path
                listeners.remove(REQUEST_FILE_PROFILE_IMAGE)?.invoke(profile)
            }
        }
    }
}
