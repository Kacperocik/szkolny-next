/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-18.
 */

package pl.szczodrzynski.edziennik.ui.base.dialog

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph

abstract class ConfigDialog<B : ViewBinding>(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
) : BindingDialog<B>(activity) {

    final override fun getPositiveButtonText() = R.string.ok
    final override suspend fun onShow() = Unit

    protected val config by lazy { app.config.grades }

    protected open suspend fun loadConfig() = Unit
    protected open suspend fun saveConfig() = Unit
    protected open fun initView() = Unit

    private fun applyShapeMorphRecursively(view: View) {
        if (view is MaterialCardView) {
            view.enableExpressiveCardShapeMorph()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyShapeMorphRecursively(view.getChildAt(i))
            }
        }
    }

    final override suspend fun onBeforeShow(): Boolean {
        initView()
        loadConfig()
        applyShapeMorphRecursively(b.root)
        return true
    }

    final override suspend fun onDismiss() {
        if (getButton() == AlertDialog.BUTTON_NEGATIVE) {
            return
        }
        saveConfig()
        if (reloadOnDismiss && activity is MainActivity)
            activity.reloadTarget()
    }
}
