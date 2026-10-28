/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-13.
 */

package pl.szczodrzynski.edziennik.ui.main

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.dialogs.ErrorDetailsDialog
import kotlin.coroutines.CoroutineContext

class ErrorSnackbar(val activity: AppCompatActivity) : CoroutineScope {
    companion object {
        private const val TAG = "ErrorSnackbar"
    }

    private var snackbar: Snackbar? = null
    private lateinit var coordinator: CoordinatorLayout
    private var errors = mutableListOf<ApiError>()

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun setCoordinator(coordinatorLayout: CoordinatorLayout, showAbove: View? = null) {
        this.coordinator = coordinatorLayout
        val newSnackbar = Snackbar.make(coordinator, R.string.snackbar_error_text, Snackbar.LENGTH_INDEFINITE)
        newSnackbar.setAction(R.string.more) {
            ErrorDetailsDialog(activity, errors).show()
            errors = mutableListOf()
        }

        val errorBgColor = R.attr.colorError.resolveAttr(activity)
        val errorTextColor = R.attr.colorOnError.resolveAttr(activity)

        val density = activity.resources.displayMetrics.density
        val shapeDrawable = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, 16f * density)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(errorBgColor)
            strokeColor = ColorStateList.valueOf(
                MaterialColors.getColor(activity, R.attr.colorOutlineVariant, 0x33000000)
            )
            strokeWidth = 1f * density
        }

        val snackbarView = newSnackbar.view
        snackbarView.background = shapeDrawable

        val lp = snackbarView.layoutParams as? CoordinatorLayout.LayoutParams
        if (lp != null) {
            val margin = (12 * density).toInt()
            lp.setMargins(margin, margin, margin, margin)
            snackbarView.layoutParams = lp
        }

        newSnackbar.setTextColor(errorTextColor)
        newSnackbar.setActionTextColor(errorTextColor)

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        if (textView != null) {
            val alertDrawable = CommunityMaterial.Icon.cmd_alert_circle_outline.toDrawable(
                activity,
                colorInt = errorTextColor,
                sizeDp = 22
            )
            textView.setCompoundDrawablesWithIntrinsicBounds(alertDrawable, null, null, null)
            textView.compoundDrawablePadding = (12 * density).toInt()
        }

        showAbove?.let { newSnackbar.anchorView = it }
        this.snackbar = newSnackbar
    }

    fun addError(apiError: ApiError): ErrorSnackbar {
        errors.add(apiError)
        snackbar?.setText(apiError.getStringReason(activity))
        snackbar?.duration = 15000
        return this
    }

    fun show() = snackbar?.show()
    fun dismiss() = snackbar?.dismiss()
}
