package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class WalkChatLimitManager {
    public static final String KEY_SESSION_START = "walk_chat_session_start";
    public static final String KEY_BLOCKED = "walk_chat_blocked";
    private static final long LIMIT_MILLIS = 15 * 60 * 1000; // 15 minutes

    private WalkChatLimitManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void startSession(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putLong(KEY_SESSION_START, System.currentTimeMillis());
        edit.putBoolean(KEY_BLOCKED, false);
        edit.apply();
    }

    public static void endSession(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putLong(KEY_SESSION_START, 0L);
        edit.putBoolean(KEY_BLOCKED, false);
        edit.apply();
    }

    public static long getSessionElapsedMillis(SharedPreferences sharedPreferences) {
        long sessionStart = sharedPreferences.getLong(KEY_SESSION_START, 0L);
        if (sessionStart <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - sessionStart;
    }

    public static boolean isSessionActive(SharedPreferences sharedPreferences) {
        long sessionStart = sharedPreferences.getLong(KEY_SESSION_START, 0L);
        return sessionStart > 0;
    }

    public static boolean isLimitExceeded(SharedPreferences sharedPreferences) {
        long elapsedMillis = getSessionElapsedMillis(sharedPreferences);
        return elapsedMillis >= LIMIT_MILLIS;
    }

    public static boolean isBlocked(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(KEY_BLOCKED, false);
    }

    public static void setBlocked(SharedPreferences sharedPreferences, boolean blocked) {
        sharedPreferences.edit().putBoolean(KEY_BLOCKED, blocked).apply();
    }

    public static long getLimitMillis() {
        return LIMIT_MILLIS;
    }

    public static long getRemainingMillis(SharedPreferences sharedPreferences) {
        long elapsedMillis = getSessionElapsedMillis(sharedPreferences);
        long remaining = LIMIT_MILLIS - elapsedMillis;
        return Math.max(0, remaining);
    }
}
