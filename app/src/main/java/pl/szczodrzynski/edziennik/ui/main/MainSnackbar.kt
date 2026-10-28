/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-22.
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
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.toDrawable

class MainSnackbar(val activity: AppCompatActivity) {
    companion object {
        private const val TAG = "MainSnackbar"
    }

    private var snackbar: Snackbar? = null
    private lateinit var coordinator: CoordinatorLayout

    fun setCoordinator(coordinatorLayout: CoordinatorLayout, showAbove: View? = null) {
        this.coordinator = coordinatorLayout
        val newSnackbar = Snackbar.make(coordinator, "", Snackbar.LENGTH_INDEFINITE)

        val surfaceInverseColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceInverse, 0xff303030.toInt())
        val onSurfaceInverseColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceInverse, 0xffffffff.toInt())
        val primaryInverseColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorPrimaryInverse, 0xff90caf9.toInt())

        val density = activity.resources.displayMetrics.density
        val shapeDrawable = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, 16f * density)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(surfaceInverseColor)
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

        newSnackbar.setTextColor(onSurfaceInverseColor)
        newSnackbar.setActionTextColor(primaryInverseColor)

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        if (textView != null) {
            val infoDrawable = CommunityMaterial.Icon2.cmd_information_outline.toDrawable(
                activity,
                colorInt = primaryInverseColor,
                sizeDp = 22
            )
            textView.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null)
            textView.compoundDrawablePadding = (12 * density).toInt()
        }

        showAbove?.let { newSnackbar.anchorView = it }
        this.snackbar = newSnackbar
    }

    fun snackbar(text: String, actionText: String? = null, onClick: (() -> Unit)? = null) {
        snackbar?.apply {
            setText(text)
            setAction(actionText) {
                onClick?.invoke()
            }
            duration = 7000
            show()
        }
    }

    fun dismiss() = snackbar?.dismiss()
}
