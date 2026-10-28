package pl.szczodrzynski.edziennik.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.mikepenz.iconics.utils.colorInt
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.DialogColorPickerBinding
import pl.szczodrzynski.edziennik.databinding.DialogColorPickerItemBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.utils.Colors

class ColorPickerDialog(
    activity: AppCompatActivity,
    initialColor: Int,
    private val onColorSelected: (Int) -> Unit,
) : BindingDialog<DialogColorPickerBinding>(activity) {

    private data class ColorFamily(
        val primaryColor: Int,
        val shades: List<Int>,
    )

    private val colorFamilies: List<ColorFamily> = listOf(
        // 1. Red
        ColorFamily(
            primaryColor = 0xFFF44336.toInt(),
            shades = listOf(
                0xFFFFEBEE.toInt(),
                0xFFFFCDD2.toInt(),
                0xFFEF9A9A.toInt(),
                0xFFE57373.toInt(),
                0xFFEF5350.toInt(),
                0xFFF44336.toInt(),
                0xFFE53935.toInt(),
                0xFFD32F2F.toInt(),
                0xFFC62828.toInt(),
                0xFFB71C1C.toInt(),
            )
        ),
        // 2. Pink
        ColorFamily(
            primaryColor = 0xFFE91E63.toInt(),
            shades = listOf(
                0xFFFCE4EC.toInt(),
                0xFFF8BBD0.toInt(),
                0xFFF48FB1.toInt(),
                0xFFF06292.toInt(),
                0xFFEC407A.toInt(),
                0xFFE91E63.toInt(),
                0xFFD81B60.toInt(),
                0xFFC2185B.toInt(),
                0xFFAD1457.toInt(),
                0xFF880E4F.toInt(),
            )
        ),
        // 3. Purple
        ColorFamily(
            primaryColor = 0xFF9C27B0.toInt(),
            shades = listOf(
                0xFFF3E5F5.toInt(),
                0xFFE1BEE7.toInt(),
                0xFFCE93D8.toInt(),
                0xFFBA68C8.toInt(),
                0xFFAB47BC.toInt(),
                0xFF9C27B0.toInt(),
                0xFF8E24AA.toInt(),
                0xFF7B1FA2.toInt(),
                0xFF6A1B9A.toInt(),
                0xFF4A148C.toInt(),
            )
        ),
        // 4. Deep Purple
        ColorFamily(
            primaryColor = 0xFF673AB7.toInt(),
            shades = listOf(
                0xFFEDE7F6.toInt(),
                0xFFD1C4E9.toInt(),
                0xFFB39DDB.toInt(),
                0xFF9575CD.toInt(),
                0xFF7E57C2.toInt(),
                0xFF673AB7.toInt(),
                0xFF5E35B1.toInt(),
                0xFF512DA8.toInt(),
                0xFF4527A0.toInt(),
                0xFF311B92.toInt(),
            )
        ),
        // 5. Indigo
        ColorFamily(
            primaryColor = 0xFF3F51B5.toInt(),
            shades = listOf(
                0xFFE8EAF6.toInt(),
                0xFFC5CAE9.toInt(),
                0xFF9FA8DA.toInt(),
                0xFF7986CB.toInt(),
                0xFF5C6BC0.toInt(),
                0xFF3F51B5.toInt(),
                0xFF3949AB.toInt(),
                0xFF303F9F.toInt(),
                0xFF283593.toInt(),
                0xFF1A237E.toInt(),
            )
        ),
        // 6. Blue
        ColorFamily(
            primaryColor = 0xFF2196F3.toInt(),
            shades = listOf(
                0xFFE3F2FD.toInt(),
                0xFFBBDEFB.toInt(),
                0xFF90CAF9.toInt(),
                0xFF64B5F6.toInt(),
                0xFF42A5F5.toInt(),
                0xFF2196F3.toInt(),
                0xFF1E88E5.toInt(),
                0xFF1976D2.toInt(),
                0xFF1565C0.toInt(),
                0xFF0D47A1.toInt(),
            )
        ),
        // 7. Light Blue
        ColorFamily(
            primaryColor = 0xFF03A9F4.toInt(),
            shades = listOf(
                0xFFE1F5FE.toInt(),
                0xFFB3E5FC.toInt(),
                0xFF81D4FA.toInt(),
                0xFF4FC3F7.toInt(),
                0xFF29B6F6.toInt(),
                0xFF03A9F4.toInt(),
                0xFF039BE5.toInt(),
                0xFF0288D1.toInt(),
                0xFF0277BD.toInt(),
                0xFF01579B.toInt(),
            )
        ),
        // 8. Cyan
        ColorFamily(
            primaryColor = 0xFF00BCD4.toInt(),
            shades = listOf(
                0xFFE0F7FA.toInt(),
                0xFFB2EBF2.toInt(),
                0xFF80DEEA.toInt(),
                0xFF4DD0E1.toInt(),
                0xFF26C6DA.toInt(),
                0xFF00BCD4.toInt(),
                0xFF00ACC1.toInt(),
                0xFF0097A7.toInt(),
                0xFF00838F.toInt(),
                0xFF006064.toInt(),
            )
        ),
        // 9. Teal
        ColorFamily(
            primaryColor = 0xFF009688.toInt(),
            shades = listOf(
                0xFFE0F2F1.toInt(),
                0xFFB2DFDB.toInt(),
                0xFF80CBC4.toInt(),
                0xFF4DB6AC.toInt(),
                0xFF26A69A.toInt(),
                0xFF009688.toInt(),
                0xFF00897B.toInt(),
                0xFF00796B.toInt(),
                0xFF00695C.toInt(),
                0xFF004D40.toInt(),
            )
        ),
        // 10. Green
        ColorFamily(
            primaryColor = 0xFF4CAF50.toInt(),
            shades = listOf(
                0xFFE8F5E9.toInt(),
                0xFFC8E6C9.toInt(),
                0xFFA5D6A7.toInt(),
                0xFF81C784.toInt(),
                0xFF66BB6A.toInt(),
                0xFF4CAF50.toInt(),
                0xFF43A047.toInt(),
                0xFF388E3C.toInt(),
                0xFF2E7D32.toInt(),
                0xFF1B5E20.toInt(),
            )
        ),
        // 11. Light Green
        ColorFamily(
            primaryColor = 0xFF8BC34A.toInt(),
            shades = listOf(
                0xFFF1F8E9.toInt(),
                0xFFDCEDC8.toInt(),
                0xFFC5E1A5.toInt(),
                0xFFAED581.toInt(),
                0xFF9CCC65.toInt(),
                0xFF8BC34A.toInt(),
                0xFF7CB342.toInt(),
                0xFF689F38.toInt(),
                0xFF558B2F.toInt(),
                0xFF33691E.toInt(),
            )
        ),
        // 12. Lime
        ColorFamily(
            primaryColor = 0xFFCDDC39.toInt(),
            shades = listOf(
                0xFFF9FBE7.toInt(),
                0xFFF0F4C3.toInt(),
                0xFFE6EE9C.toInt(),
                0xFFDCE775.toInt(),
                0xFFD4E157.toInt(),
                0xFFCDDC39.toInt(),
                0xFFC0CA33.toInt(),
                0xFFAFB42B.toInt(),
                0xFF9E9D24.toInt(),
                0xFF827717.toInt(),
            )
        ),
        // 13. Yellow
        ColorFamily(
            primaryColor = 0xFFFFEB3B.toInt(),
            shades = listOf(
                0xFFFFFDE7.toInt(),
                0xFFFFF9C4.toInt(),
                0xFFFFF59D.toInt(),
                0xFFFFF176.toInt(),
                0xFFFFEE58.toInt(),
                0xFFFFEB3B.toInt(),
                0xFFFDD835.toInt(),
                0xFFFBC02D.toInt(),
                0xFFF9A825.toInt(),
                0xFFF57F17.toInt(),
            )
        ),
        // 14. Amber
        ColorFamily(
            primaryColor = 0xFFFFC107.toInt(),
            shades = listOf(
                0xFFFFF8E1.toInt(),
                0xFFFFECB3.toInt(),
                0xFFFFE082.toInt(),
                0xFFFFD54F.toInt(),
                0xFFFFCA28.toInt(),
                0xFFFFC107.toInt(),
                0xFFFFB300.toInt(),
                0xFFFFA000.toInt(),
                0xFFFF8F00.toInt(),
                0xFFFF6F00.toInt(),
            )
        ),
        // 15. Orange
        ColorFamily(
            primaryColor = 0xFFFF9800.toInt(),
            shades = listOf(
                0xFFFFF3E0.toInt(),
                0xFFFFE0B2.toInt(),
                0xFFFFCC80.toInt(),
                0xFFFFB74D.toInt(),
                0xFFFFA726.toInt(),
                0xFFFF9800.toInt(),
                0xFFFB8C00.toInt(),
                0xFFF57C00.toInt(),
                0xFFEF6C00.toInt(),
                0xFFE65100.toInt(),
            )
        ),
        // 16. Deep Orange
        ColorFamily(
            primaryColor = 0xFFFF5722.toInt(),
            shades = listOf(
                0xFFFBE9E7.toInt(),
                0xFFFFCCBC.toInt(),
                0xFFFFAB91.toInt(),
                0xFFFF8A65.toInt(),
                0xFFFF7043.toInt(),
                0xFFFF5722.toInt(),
                0xFFF4511E.toInt(),
                0xFFE64A19.toInt(),
                0xFFD84315.toInt(),
                0xFFBF360C.toInt(),
            )
        ),
        // 17. Brown
        ColorFamily(
            primaryColor = 0xFF795548.toInt(),
            shades = listOf(
                0xFFEFEBE9.toInt(),
                0xFFD7CCC8.toInt(),
                0xFFBCAAA4.toInt(),
                0xFFA1887F.toInt(),
                0xFF8D6E63.toInt(),
                0xFF795548.toInt(),
                0xFF6D4C41.toInt(),
                0xFF5D4037.toInt(),
                0xFF4E342E.toInt(),
                0xFF3E2723.toInt(),
            )
        ),
        // 18. Grey / Neutrals
        ColorFamily(
            primaryColor = 0xFF9E9E9E.toInt(),
            shades = listOf(
                0xFFFFFFFF.toInt(),
                0xFFFAFAFA.toInt(),
                0xFFF5F5F5.toInt(),
                0xFFEEEEEE.toInt(),
                0xFFE0E0E0.toInt(),
                0xFFBDBDBD.toInt(),
                0xFF9E9E9E.toInt(),
                0xFF757575.toInt(),
                0xFF616161.toInt(),
                0xFF424242.toInt(),
                0xFF212121.toInt(),
                0xFF000000.toInt(),
            )
        ),
    )

    private val selectedInitial = ColorUtils.setAlphaComponent(initialColor, 255)
    private var selectedColor = selectedInitial
    private var selectedFamily: ColorFamily? = null
    private var updatingHex = false
    private var updatingCustom = false
    private var customMode = false
    private val hsv = FloatArray(3)
    private val gridSwatches = mutableListOf<DialogColorPickerItemBinding>()
    private val shadesSwatches = mutableListOf<Pair<Int, DialogColorPickerItemBinding>>()

    override fun getTitleRes() = R.string.dialog_color_picker_title
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogColorPickerBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel
    override fun getNeutralButtonText() = R.string.dialog_color_picker_custom

    override suspend fun onShow() {
        b.previewOld.setCardBackgroundColor(selectedInitial)
        b.previewOld.onClick {
            selectedColor = selectedInitial
            bindSelection()
        }
        TooltipCompat.setTooltipText(b.previewOld, activity.getString(R.string.dialog_color_picker_old_color))
        TooltipCompat.setTooltipText(b.preview, activity.getString(R.string.dialog_color_picker_new_color))

        bindGrid()

        selectedFamily = findFamilyForColor(selectedColor)
        bindShades()

        b.satVal.onChanged = { saturation, value ->
            if (!updatingCustom) {
                hsv[1] = saturation
                hsv[2] = value
                applyHsv()
            }
        }
        b.hueBar.onChanged = { hue ->
            if (!updatingCustom) {
                hsv[0] = hue
                b.satVal.hue = hue
                applyHsv()
            }
        }

        bindSelection()

        b.hex.doAfterTextChanged { text ->
            if (updatingHex) return@doAfterTextChanged
            val color = parseHex(text?.toString().orEmpty()) ?: return@doAfterTextChanged
            selectedColor = color
            b.preview.setCardBackgroundColor(color)
            if (!customMode) {
                val family = findFamilyForColor(selectedColor)
                if (family != selectedFamily) {
                    selectedFamily = family
                    bindShades()
                }
                bindChecks()
            }
            syncCustomFromColor()
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        onColorSelected(selectedColor)
        return DISMISS
    }

    override suspend fun onNeutralClick(): Boolean {
        customMode = !customMode
        b.presetPanel.isVisible = !customMode
        b.customPanel.isVisible = customMode
        getDialogButton(AlertDialog.BUTTON_NEUTRAL)?.setText(
            if (customMode) R.string.dialog_color_picker_presets
            else R.string.dialog_color_picker_custom
        )
        if (customMode) {
            syncCustomFromColor()
        } else {
            selectedFamily = findFamilyForColor(selectedColor)
            bindShades()
            bindChecks()
        }
        return NO_DISMISS
    }

    private fun bindGrid() {
        b.grid.removeAllViews()
        gridSwatches.clear()

        colorFamilies.forEach { family ->
            val item = DialogColorPickerItemBinding.inflate(activity.layoutInflater, b.grid, false)
            item.root.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            (item.swatch.background?.mutate() as? GradientDrawable)?.apply {
                setColor(family.primaryColor)
                val strokeColor = if (ColorUtils.calculateLuminance(family.primaryColor) > 0.85) 0x33000000.toInt() else 0x1A000000.toInt()
                setStroke(1.dp, strokeColor)
            }
            val isSelected = (family == selectedFamily)
            item.check.isVisible = isSelected
            if (isSelected) {
                item.check.icon?.colorInt = Colors.legibleTextColor(family.primaryColor)
            }
            item.swatch.setSwatchSelected(isSelected, animated = false)

            item.swatch.onClick {
                selectedFamily = family
                if (selectedColor !in family.shades) {
                    selectedColor = family.primaryColor
                }
                bindShades()
                bindSelection()
            }
            b.grid.addView(item.root)
            gridSwatches += item
        }
    }

    private fun bindShades() {
        b.shadesContainer.removeAllViews()
        shadesSwatches.clear()

        val family = selectedFamily ?: return
        val shades = buildList {
            if (selectedInitial !in family.shades && findFamilyForColor(selectedInitial) == family) {
                add(selectedInitial)
            }
            addAll(family.shades)
        }

        var selectedView: View? = null

        shades.forEach { color ->
            val item = DialogColorPickerItemBinding.inflate(activity.layoutInflater, b.shadesContainer, false)
            item.root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            item.root.setPadding(2.dp, 4.dp, 2.dp, 4.dp)
            (item.swatch.background?.mutate() as? GradientDrawable)?.apply {
                setColor(color)
                val strokeColor = if (ColorUtils.calculateLuminance(color) > 0.85) 0x33000000.toInt() else 0x1A000000.toInt()
                setStroke(1.dp, strokeColor)
            }
            item.swatch.onClick {
                selectedColor = color
                bindSelection()
            }

            val isSelected = (color == selectedColor)
            item.check.isVisible = isSelected
            if (isSelected) {
                item.check.icon?.colorInt = Colors.legibleTextColor(color)
                selectedView = item.root
            }
            item.swatch.setSwatchSelected(isSelected, animated = false)

            b.shadesContainer.addView(item.root)
            shadesSwatches += (color to item)
        }

        selectedView?.let { view ->
            b.shadesScroll.post {
                val targetScrollX = (view.left + view.width / 2) - b.shadesScroll.width / 2
                b.shadesScroll.smoothScrollTo(targetScrollX.coerceAtLeast(0), 0)
            }
        }
    }

    private fun applyHsv() {
        selectedColor = Color.HSVToColor(hsv)
        b.preview.setCardBackgroundColor(selectedColor)
        setHexText(selectedColor)
    }

    private fun syncCustomFromColor() {
        updatingCustom = true
        Color.colorToHSV(selectedColor, hsv)
        b.hueBar.hue = hsv[0]
        b.satVal.hue = hsv[0]
        b.satVal.saturation = hsv[1]
        b.satVal.value = hsv[2]
        updatingCustom = false
    }

    private fun bindSelection() {
        val family = selectedFamily
        if (family == null || (selectedColor !in family.shades && selectedColor != selectedInitial)) {
            val newFamily = findFamilyForColor(selectedColor)
            if (newFamily != selectedFamily) {
                selectedFamily = newFamily
                bindShades()
            }
        }

        b.preview.setCardBackgroundColor(selectedColor)
        setHexText(selectedColor)
        bindChecks()
        syncCustomFromColor()
    }

    private fun bindChecks() {
        gridSwatches.forEachIndexed { index, item ->
            val family = colorFamilies[index]
            val isSelected = (family == selectedFamily)
            item.check.isVisible = isSelected
            if (isSelected) {
                item.check.icon?.colorInt = Colors.legibleTextColor(family.primaryColor)
            }
            item.swatch.setSwatchSelected(isSelected, animated = true)
        }

        shadesSwatches.forEach { (color, item) ->
            val isSelected = (color == selectedColor)
            item.check.isVisible = isSelected
            if (isSelected) {
                item.check.icon?.colorInt = Colors.legibleTextColor(color)
            }
            item.swatch.setSwatchSelected(isSelected, animated = true)
        }
    }

    private fun findFamilyForColor(color: Int): ColorFamily {
        colorFamilies.firstOrNull { family -> color in family.shades }?.let { return it }

        return colorFamilies.minByOrNull { family ->
            family.shades.minOfOrNull { colorDistance(it, color) } ?: Double.MAX_VALUE
        } ?: colorFamilies.first()
    }

    private fun colorDistance(c1: Int, c2: Int): Double {
        val rDiff = Color.red(c1) - Color.red(c2)
        val gDiff = Color.green(c1) - Color.green(c2)
        val bDiff = Color.blue(c1) - Color.blue(c2)
        return (rDiff * rDiff + gDiff * gDiff + bDiff * bDiff).toDouble()
    }

    private fun setHexText(color: Int) {
        updatingHex = true
        b.hex.setText("%06X".format(color and 0xFFFFFF))
        updatingHex = false
    }

    private fun parseHex(text: String): Int? {
        val hex = text.removePrefix("#").trim()
        if (hex.length != 6) return null
        return runCatching { Color.parseColor("#$hex") }.getOrNull()
    }
}
