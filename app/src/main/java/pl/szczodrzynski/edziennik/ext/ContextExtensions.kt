/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package pl.szczodrzynski.edziennik.ext

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import pl.szczodrzynski.edziennik.App

fun Activity.hideKeyboard() {
    val view = currentFocus ?: window.decorView
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

val AppCompatActivity.app
    get() = applicationContext as App

val Context.isNightMode: Boolean
    get() {
        if (getSystemService<UiModeManager>()?.nightMode == UiModeManager.MODE_NIGHT_YES)
            return true
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) != 0
    }
