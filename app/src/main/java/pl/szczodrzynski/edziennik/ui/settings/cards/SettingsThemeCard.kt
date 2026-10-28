/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-18.
 */

package pl.szczodrzynski.edziennik.ui.settings.cards

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.AppLanguageDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.DrawerMenuConfigDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.MiniMenuConfigDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.ThemeChooserDialog
import pl.szczodrzynski.edziennik.ui.settings.ItemCornerPositions
import pl.szczodrzynski.edziennik.ui.settings.SettingsCard
import pl.szczodrzynski.edziennik.ui.settings.SettingsUtil
import pl.szczodrzynski.edziennik.utils.BigNightUtil
import pl.szczodrzynski.edziennik.utils.models.Date

private enum class ThemeNightMode(val value: Boolean?) {
    SYSTEM(null),
    LIGHT(false),
    DARK(true);

    companion object {
        fun from(value: Boolean?) = entries.first { it.value == value }
    }
}

class SettingsThemeCard(util: SettingsUtil) : SettingsCard(util) {

    override fun buildCard() = util.createCard(
        titleRes = null,
        items = ::getItems,
        itemsMore = { emptyList() },
    )

    override fun getItems(card: MaterialAboutCard): List<MaterialAboutItem> {
        val items = mutableListOf<MaterialAboutItem>()
        items.add(util.createSectionItem(R.string.settings_card_theme_title))

        val isSystemNightModeSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val currentThemeNightMode = if (!isSystemNightModeSupported && configGlobal.ui.themeNightMode == null) {
            false
        } else {
            configGlobal.ui.themeNightMode
        }

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_mode_text,
                subText = when (currentThemeNightMode) {
                    false -> R.string.settings_theme_mode_light
                    true -> R.string.settings_theme_mode_dark
                    null -> R.string.settings_theme_mode_system
                },
                icon = CommunityMaterial.Icon3.cmd_theme_light_dark
            ) {
                SimpleDialog<ThemeNightMode>(activity) {
                    title(R.string.settings_theme_mode_text)
                    if (isSystemNightModeSupported) {
                        singleRes(
                            R.string.settings_theme_mode_system to ThemeNightMode.SYSTEM,
                            R.string.settings_theme_mode_light to ThemeNightMode.LIGHT,
                            R.string.settings_theme_mode_dark to ThemeNightMode.DARK,
                            default = ThemeNightMode.from(currentThemeNightMode),
                        )
                    } else {
                        singleRes(
                            R.string.settings_theme_mode_light to ThemeNightMode.LIGHT,
                            R.string.settings_theme_mode_dark to ThemeNightMode.DARK,
                            default = ThemeNightMode.from(currentThemeNightMode ?: false),
                        )
                    }
                    positive(R.string.ok) {
                        val mode = getSingleSelection() ?: return@positive
                        if (configGlobal.ui.themeNightMode != mode.value) {
                            configGlobal.ui.themeNightMode = mode.value
                            app.uiManager.applyNightMode()
                            activity.recreate()
                        }
                    }
                    negative(R.string.cancel)
                }.show()
            }
        )

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_theme_color_text,
                subText = app.uiManager.themeColor.nameRes,
                icon = CommunityMaterial.Icon3.cmd_palette_outline
            ) {
                ThemeChooserDialog(activity).show()
            }
        )

        items.add(
            util.createActionItem(
                text = R.string.settings_about_language_text,
                subText = R.string.settings_about_language_subtext,
                icon = CommunityMaterial.Icon3.cmd_translate
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.fromParts("package", activity.packageName, null)
                        }
                    )
                } else {
                    AppLanguageDialog(activity).show()
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            items.add(
                util.createPropertyItem(
                    text = R.string.settings_theme_fullscreen_text,
                    icon = CommunityMaterial.Icon2.cmd_fullscreen,
                    value = configGlobal.ui.appFullscreen
                ) { _, it ->
                    configGlobal.ui.appFullscreen = it
                    activity.recreate()
                }
            )
        }

        items.add(
            util.createPropertyItem(
                text = R.string.settings_theme_toolbar_at_bottom_text,
                subText = R.string.settings_theme_toolbar_at_bottom_subtext,
                icon = CommunityMaterial.Icon2.cmd_format_vertical_align_bottom,
                value = configGlobal.ui.toolbarAtBottom
            ) { _, it ->
                configGlobal.ui.toolbarAtBottom = it
                activity.navView.toolbarAtBottom = it
                activity.recreate()
            }
        )

        items.add(
            util.createPropertyItem(
                text = R.string.settings_theme_mini_drawer_text,
                subText = R.string.settings_theme_mini_drawer_subtext,
                icon = CommunityMaterial.Icon.cmd_dots_vertical,
                value = configGlobal.ui.miniMenuVisible
            ) { _, it ->
                configGlobal.ui.miniMenuVisible = it
                activity.navView.drawer.miniDrawerVisiblePortrait = it
            }
        )

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_mini_drawer_buttons_text,
                icon = CommunityMaterial.Icon2.cmd_format_list_checks
            ) {
                MiniMenuConfigDialog(activity).show()
            }
        )

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_drawer_buttons_text,
                subText = R.string.settings_theme_drawer_buttons_subtext,
                icon = CommunityMaterial.Icon2.cmd_format_list_checks
            ) {
                DrawerMenuConfigDialog(activity).show()
            }
        )

        items.add(
            util.createPropertyItem(
                text = R.string.settings_theme_drawer_header_visible_text,
                subText = R.string.settings_theme_drawer_header_visible_subtext,
                icon = CommunityMaterial.Icon.cmd_account_circle_outline,
                value = configGlobal.ui.drawerHeaderVisible
            ) { _, it ->
                configGlobal.ui.drawerHeaderVisible = it
                activity.navView.drawer.accountHeaderVisible = it
            }
        )

        items.add(
            util.createPropertyItem(
                text = R.string.settings_theme_open_drawer_on_back_pressed_text,
                icon = CommunityMaterial.Icon3.cmd_menu_open,
                value = configGlobal.ui.openDrawerOnBackPressed
            ) { _, it ->
                configGlobal.ui.openDrawerOnBackPressed = it
            }
        )

        items.add(util.createSectionItem(R.string.settings_theme_backgrounds_section))

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_drawer_header_text,
                icon = CommunityMaterial.Icon2.cmd_image_outline
            ) {
                if (app.config.ui.headerBackground == null) {
                    setHeaderBackground()
                    return@createActionItem
                }
                SimpleDialog<Int>(activity) {
                    itemsRes(
                        R.string.settings_theme_drawer_header_dialog_set to 0,
                        R.string.settings_theme_drawer_header_dialog_restore to 1,
                    ) {
                        when (it) {
                            0 -> setHeaderBackground()
                            1 -> {
                                app.config.ui.headerBackground = null
                                activity.drawer.setAccountHeaderBackground(null)
                                activity.drawer.open()
                            }
                        }
                    }
                    negative(R.string.cancel)
                }.show()
            }
        )

        items.add(
            util.createActionItem(
                text = R.string.settings_theme_app_background_text,
                subText = R.string.settings_theme_app_background_subtext,
                icon = CommunityMaterial.Icon2.cmd_image_filter_hdr
            ) {
                if (app.config.ui.appBackground == null) {
                    setAppBackground()
                    return@createActionItem
                }
                SimpleDialog<Int>(activity) {
                    itemsRes(
                        R.string.settings_theme_app_background_dialog_set to 0,
                        R.string.settings_theme_app_background_dialog_restore to 1,
                    ) {
                        when (it) {
                            0 -> setAppBackground()
                            1 -> {
                                app.config.ui.appBackground = null
                                activity.setAppBackground()
                            }
                        }
                    }
                    negative(R.string.cancel)
                }.show()
            }
        )

        val today = Date.getToday()
        val isWinter = today.month == 12 || today.month == 1 || today.month == 2
        val isEaster = BigNightUtil().isDataWielkanocyNearDzisiaj()

        if (isWinter) {
            items.add(util.createSectionItem("???"))
            items.add(
                util.createPropertyItem(
                    text = R.string.settings_theme_snowfall_text,
                    subText = R.string.settings_theme_snowfall_subtext,
                    icon = CommunityMaterial.Icon3.cmd_snowflake,
                    value = configGlobal.ui.snowfall
                ) { _, it ->
                    configGlobal.ui.snowfall = it
                    activity.recreate()
                }
            )
        } else if (isEaster) {
            items.add(util.createSectionItem("???"))
            items.add(
                util.createPropertyItem(
                    text = R.string.settings_theme_eggfall_text,
                    subText = R.string.settings_theme_eggfall_subtext,
                    icon = CommunityMaterial.Icon.cmd_egg_easter,
                    value = configGlobal.ui.eggfall
                ) { _, it ->
                    configGlobal.ui.eggfall = it
                    activity.recreate()
                }
            )
        }

        return ItemCornerPositions.applyGroupPositions(items)
    }

    private fun setHeaderBackground() = activity.requestHandler.requestHeaderBackground {
        activity.drawer.setAccountHeaderBackground(null)
        activity.drawer.setAccountHeaderBackground(app.config.ui.headerBackground)
        activity.drawer.open()
    }

    private fun setAppBackground() = activity.requestHandler.requestAppBackground {
        activity.setAppBackground()
    }
}
