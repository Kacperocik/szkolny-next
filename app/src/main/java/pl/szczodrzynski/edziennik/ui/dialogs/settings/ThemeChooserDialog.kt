package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.Theme
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ui.base.dialog.BaseDialog

class ThemeChooserDialog(
    activity: AppCompatActivity,
) : BaseDialog<Theme>(activity) {

    private lateinit var oledCheckBox: MaterialCheckBox
    private lateinit var intensitySection: View
    private lateinit var intensitySlider: Slider
    private lateinit var intensityValue: TextView
    private var selectedTheme: Theme = app.uiManager.themeColor

    override fun getTitleRes() = R.string.settings_theme_theme_color_text
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    override fun getView(): View {
        val radioGroup = RadioGroup(activity).apply {
            orientation = LinearLayout.VERTICAL
            Theme.entries.forEach { theme ->
                addView(MaterialRadioButton(activity).apply {
                    id = View.generateViewId()
                    text = activity.getString(theme.nameRes)
                    isChecked = theme == selectedTheme
                    tag = theme
                    setOnCheckedChangeListener { _, isChecked ->
                        if (!isChecked) return@setOnCheckedChangeListener
                        selectedTheme = theme
                        intensitySection.isVisible = theme != Theme.DEFAULT
                    }
                })
            }
        }

        oledCheckBox = MaterialCheckBox(activity).apply {
            setText(R.string.settings_theme_black_mode_text)
            isChecked = app.config.ui.themeBlackMode
        }
        intensityValue = TextView(activity).apply {
            TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            gravity = Gravity.CENTER
        }
        var lastStep = app.config.ui.themeIntensity.coerceIn(0, 2)
        intensitySlider = Slider(activity).apply {
            valueFrom = 0f
            valueTo = 2f
            stepSize = 1f
            labelBehavior = LabelFormatter.LABEL_GONE
            value = lastStep.toFloat()
            addOnChangeListener { slider, value, fromUser ->
                val newStep = value.toInt()
                if (fromUser && newStep != lastStep) {
                    lastStep = newStep
                    slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                intensityValue.text = intensityLabel(newStep)
            }
        }
        intensityValue.text = intensityLabel(intensitySlider.value.toInt())
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label(R.string.theme_intensity_subtle, Gravity.START))
            addView(label(R.string.theme_intensity_balanced, Gravity.CENTER))
            addView(label(R.string.theme_intensity_vivid, Gravity.END))
        }
        intensitySection = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = selectedTheme != Theme.DEFAULT
            addView(TextView(activity).apply {
                setText(R.string.theme_intensity_title)
                TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, 16.dp, 0, 4.dp)
            })
            addView(intensitySlider)
            addView(labels)
            addView(intensityValue)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 8.dp)
            addView(radioGroup)
            addView(oledCheckBox)
            addView(intensitySection)
        }
        return ScrollView(activity).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        val themeBlackMode = oledCheckBox.isChecked
        val themeIntensity = intensitySlider.value.toInt().coerceIn(0, 2)
        if (
            app.uiManager.themeColor != selectedTheme ||
            app.config.ui.themeBlackMode != themeBlackMode ||
            app.config.ui.themeIntensity != themeIntensity
        ) {
            app.config.ui.themeColor = selectedTheme
            app.config.ui.themeBlackMode = themeBlackMode
            app.config.ui.themeIntensity = themeIntensity
            activity.recreate()
        }
        return DISMISS
    }

    private fun label(textRes: Int, gravity: Int) = TextView(activity).apply {
        setText(textRes)
        this.gravity = gravity
        TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun intensityLabel(value: Int) = activity.getString(
        when (value) {
            0 -> R.string.theme_intensity_subtle
            2 -> R.string.theme_intensity_vivid
            else -> R.string.theme_intensity_balanced
        },
    )
}
