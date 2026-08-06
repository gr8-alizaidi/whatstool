package com.whatstools;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.whatstools.shake_Detector.appPreferences;

public class ThemeHelper {

    public static void applyTheme(Context context) {
        appPreferences prefs = new appPreferences(context);
        int themeMode = prefs.getTheme();
        applyThemeMode(themeMode);
    }

    public static void applyThemeMode(int themeMode) {
        switch (themeMode) {
            case appPreferences.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case appPreferences.THEME_AUTO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                break;
            case appPreferences.THEME_LIGHT:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    public static void setTheme(Context context, int themeMode) {
        appPreferences prefs = new appPreferences(context);
        prefs.setTheme(themeMode);
        applyThemeMode(themeMode);
    }
}
