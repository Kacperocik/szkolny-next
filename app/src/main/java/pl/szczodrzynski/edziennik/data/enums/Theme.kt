/*
 * Copyright (c) Kuba Szczodrzyński 2024-6-27.
 */

package pl.szczodrzynski.edziennik.data.enums

import pl.szczodrzynski.edziennik.R

enum class Theme(
    val nameRes: Int,
    val styleRes: Map<Pair<Type, Mode>, Int>,
) {
    DEFAULT(
        nameRes = R.string.theme_default,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3,
        ),
    ),
    RED(
        nameRes = R.string.theme_red,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Red,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Red_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Red,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Red_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Red_Classic,
        ),
    ),
    GREEN(
        nameRes = R.string.theme_green,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Green,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Green_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Green,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Green_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Green_Classic,
        ),
    ),
    BLUE(
        nameRes = R.string.theme_blue,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Blue,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Blue_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Blue,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Blue_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Blue_Classic,
        ),
    ),
    PURPLE(
        nameRes = R.string.theme_purple,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Purple,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Purple_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Purple,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Purple_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Purple_Classic,
        ),
    ),
    TEAL(
        nameRes = R.string.theme_teal,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Teal,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Teal_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Teal,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Teal_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Teal_Classic,
        ),
    ),
    YELLOW(
        nameRes = R.string.theme_yellow,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Yellow,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Yellow_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Yellow,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Yellow_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Yellow_Classic,
        ),
    ),
    ORANGE(
        nameRes = R.string.theme_orange,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Orange,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Orange_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Orange,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Orange_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Orange_Classic,
        ),
    ),
    BROWN(
        nameRes = R.string.theme_brown,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Brown,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Brown_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Brown,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Brown_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Brown_Classic,
        ),
    ),
    GRAY(
        nameRes = R.string.theme_gray,
        styleRes = mapOf(
            (Type.M2 to Mode.DAYNIGHT) to R.style.AppTheme_M2_Gray,
            (Type.M2 to Mode.FULL) to R.style.AppTheme_M2_Gray_Full,
            (Type.M3 to Mode.DAYNIGHT) to R.style.AppTheme_M3_Gray,
            (Type.M3 to Mode.FULL) to R.style.AppTheme_M3_Gray_Full,
            (Type.M3 to Mode.CLASSIC) to R.style.AppTheme_M3_Gray_Classic,
        ),
    );

    enum class Type {
        M2,
        M3,
    }

    enum class Mode {
        DAYNIGHT,
        FULL,
        CLASSIC,
    }

    fun intensityOverlay(intensity: Int, type: Type, mode: Mode): Int {
        if (type != Type.M3 || mode != Mode.DAYNIGHT || this == DEFAULT) return 0
        val vivid = when (intensity) {
            0 -> false
            2 -> true
            else -> return 0
        }
        return when (this) {
            RED -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Red else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Red
            GREEN -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Green else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Green
            BLUE -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Blue else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Blue
            PURPLE -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Purple else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Purple
            TEAL -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Teal else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Teal
            YELLOW -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Yellow else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Yellow
            ORANGE -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Orange else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Orange
            BROWN -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Brown else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Brown
            GRAY -> if (vivid) R.style.ThemeOverlay_AppTheme_Intensity_Vivid_Gray else R.style.ThemeOverlay_AppTheme_Intensity_Subtle_Gray
            DEFAULT -> 0
        }
    }
}
