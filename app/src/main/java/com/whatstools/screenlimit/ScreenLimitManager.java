package com.whatstools.screenlimit;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;

public final class ScreenLimitManager {
    public static final String KEY_ENABLED = "screen_limit_enabled";
    public static final String KEY_LIMIT_MINUTES = "screen_limit_limit_minutes";
    public static final String KEY_TODAY_USAGE = "screen_limit_today_usage";
    public static final String KEY_TODAY_DATE = "screen_limit_today_date";
    public static final String KEY_SESSION_START = "screen_limit_session_start";
    public static final String KEY_BLOCKED = "screen_limit_blocked";

    private ScreenLimitManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static String todayKey() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static void resetIfNewDay(SharedPreferences sharedPreferences) {
        String todayKey = todayKey();
        if (!todayKey.equals(sharedPreferences.getString(KEY_TODAY_DATE, ""))) {
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(KEY_TODAY_DATE, todayKey);
            edit.putLong(KEY_TODAY_USAGE, 0L);
            edit.putLong(KEY_SESSION_START, 0L);
            edit.putBoolean(KEY_BLOCKED, false);
            edit.apply();
        }
    }

    public static long getTodayUsageMillis(SharedPreferences sharedPreferences) {
        resetIfNewDay(sharedPreferences);
        return sharedPreferences.getLong(KEY_TODAY_USAGE, 0L);
    }

    public static long getLimitMillis(SharedPreferences sharedPreferences) {
        return sharedPreferences.getInt(KEY_LIMIT_MINUTES, 0) * 60L * 1000L;
    }

    public static boolean isEnabled(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(KEY_ENABLED, false);
    }

    public static boolean isBlocked(SharedPreferences sharedPreferences) {
        resetIfNewDay(sharedPreferences);
        return sharedPreferences.getBoolean(KEY_BLOCKED, false);
    }

    public static void setBlocked(SharedPreferences sharedPreferences, boolean blocked) {
        sharedPreferences.edit().putBoolean(KEY_BLOCKED, blocked).apply();
    }

    public static void addUsage(SharedPreferences sharedPreferences, long millis) {
        if (millis <= 0) {
            return;
        }
        resetIfNewDay(sharedPreferences);
        long current = sharedPreferences.getLong(KEY_TODAY_USAGE, 0L);
        sharedPreferences.edit().putLong(KEY_TODAY_USAGE, current + millis).apply();
    }
}
