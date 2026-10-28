/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package pl.szczodrzynski.edziennik.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

public class WidgetConfig {
    public static final int THEME_MODE_LIGHT = 0;
    public static final int THEME_MODE_DARK = 1;
    public static final int THEME_MODE_SYSTEM = 2;

    public int profileId = -1;
    public boolean bigStyle = false;
    public boolean darkTheme = false;
    public int themeMode = THEME_MODE_SYSTEM;
    public int themeColor = -1; // -1 = same as app, or Theme.ordinal()
    public boolean colorSubjectBackground = false;
    public boolean headerSameAsBody = false;
    public boolean appBackground = false;
    public boolean useEmoji = false;
    public boolean useThemeIconColor = false;
    public float opacity = 0.8f;

    public WidgetConfig(int profileId) {
        this.profileId = profileId;
    }

    public WidgetConfig(int profileId, boolean bigStyle, boolean darkTheme, float opacity) {
        this.profileId = profileId;
        this.bigStyle = bigStyle;
        this.darkTheme = darkTheme;
        this.themeMode = darkTheme ? THEME_MODE_DARK : THEME_MODE_LIGHT;
        this.opacity = opacity;
    }

    public WidgetConfig(int profileId, boolean bigStyle, int themeMode, int themeColor, boolean colorSubjectBackground, boolean headerSameAsBody, boolean appBackground, float opacity) {
        this.profileId = profileId;
        this.bigStyle = bigStyle;
        this.themeMode = themeMode;
        this.darkTheme = themeMode == THEME_MODE_DARK;
        this.themeColor = themeColor;
        this.colorSubjectBackground = colorSubjectBackground;
        this.headerSameAsBody = headerSameAsBody;
        this.appBackground = appBackground;
        this.opacity = opacity;
    }

    public boolean isDarkTheme(Context context) {
        if (themeMode == THEME_MODE_DARK) return true;
        if (themeMode == THEME_MODE_LIGHT) return false;
        int ctxNight = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (ctxNight == Configuration.UI_MODE_NIGHT_YES) return true;
        if (ctxNight == Configuration.UI_MODE_NIGHT_NO) return false;
        int sysNight = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return sysNight == Configuration.UI_MODE_NIGHT_YES;
    }
}
