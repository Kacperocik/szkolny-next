package pl.szczodrzynski.edziennik.ui.base.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.ViewNumberStepperBinding
import pl.szczodrzynski.edziennik.ext.onClick
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

class NumberStepperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val b = ViewNumberStepperBinding.inflate(LayoutInflater.from(context), this)

    var minValue = 1f
        set(value) {
            field = value
            this.value = this.value
        }

    var maxValue = 20f
        set(value) {
            field = value
            this.value = this.value
        }

    var stepSize = 1f
        set(value) {
            field = if (value > 0f) value else 1f
            this.value = this.value
        }

    private var internalValue = 1f

    var value: Float
        get() {
            commitInput()
            return internalValue
        }
        set(value) {
            val coerced = snap(value)
            if (internalValue == coerced) {
                updateText()
                updateEnabledState()
                return
            }
            internalValue = coerced
            updateText()
            updateEnabledState()
            onValueChanged?.invoke(coerced)
        }

    var onValueChanged: ((Float) -> Unit)? = null

    var editable = false
        set(value) {
            field = value
            applyEditableState()
        }

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL

        context.obtainStyledAttributes(attrs, R.styleable.NumberStepperView).apply {
            minValue = getFloat(R.styleable.NumberStepperView_stepperMin, minValue)
            maxValue = getFloat(R.styleable.NumberStepperView_stepperMax, maxValue)
            stepSize = getFloat(R.styleable.NumberStepperView_stepperStep, stepSize)
            this@NumberStepperView.value = getFloat(R.styleable.NumberStepperView_stepperValue, minValue)
            editable = getBoolean(R.styleable.NumberStepperView_stepperEditable, false)
            recycle()
        }

        b.stepperMinus.onClick {
            commitInput()
            this.value -= stepSize
        }
        b.stepperPlus.onClick {
            commitInput()
            this.value += stepSize
        }
        b.stepperValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitInput()
                b.stepperValue.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }
        b.stepperValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                commitInput()
            }
        }
        applyEditableState()
        updateText()
        updateEnabledState()
    }

    private val decimalPlaces: Int
        get() = when {
            stepSize >= 1f -> 0
            stepSize >= 0.1f -> 1
            else -> 2
        }

    private fun snap(raw: Float): Float {
        val steps = ((raw - minValue) / stepSize).roundToInt()
        val snapped = minValue + steps * stepSize
        val factor = 10f.pow(decimalPlaces)
        return ((snapped * factor).roundToInt() / factor).coerceIn(minValue, maxValue)
    }

    private fun updateText() {
        val format = if (decimalPlaces == 0) "0" else "0." + "0".repeat(decimalPlaces)
        val formatted = DecimalFormat(format, DecimalFormatSymbols(Locale.getDefault())).format(internalValue)
        if (b.stepperValue.text?.toString() != formatted) {
            b.stepperValue.setText(formatted)
        }
    }

    private fun applyEditableState() {
        b.stepperValue.apply {
            isFocusable = editable
            isFocusableInTouchMode = editable
            isCursorVisible = editable
            isClickable = editable
            isLongClickable = editable
        }
    }

    private fun commitInput() {
        if (!editable) return
        val parsed = b.stepperValue.text
            ?.toString()
            ?.trim()
            ?.replace(',', '.')
            ?.toFloatOrNull()
        val coerced = snap(parsed ?: internalValue)
        if (internalValue != coerced) {
            internalValue = coerced
            onValueChanged?.invoke(coerced)
        }
        updateEnabledState()
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(b.stepperValue.windowToken, 0)
    }

    private fun updateEnabledState() {
        b.stepperMinus.isEnabled = internalValue > minValue
        b.stepperPlus.isEnabled = internalValue < maxValue
    }
}
