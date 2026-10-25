package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class WalkChatTimerManager {
    public static final String KEY_WALK_ENABLED = "walk_chat_enabled";
    public static final String KEY_WALK_SESSION_START = "walk_chat_session_start";
    private static final long WALK_CHAT_LIMIT_MILLIS = 15 * 60 * 1000;

    private WalkChatTimerManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void startSession(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean(KEY_WALK_ENABLED, true);
        edit.putLong(KEY_WALK_SESSION_START, System.currentTimeMillis());
        edit.apply();
    }

    public static void stopSession(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean(KEY_WALK_ENABLED, false);
        edit.putLong(KEY_WALK_SESSION_START, 0L);
        edit.apply();
    }

    public static boolean isWalkChatActive(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(KEY_WALK_ENABLED, false);
    }

    public static long getSessionStartTime(SharedPreferences sharedPreferences) {
        return sharedPreferences.getLong(KEY_WALK_SESSION_START, 0L);
    }

    public static long getElapsedMillis(SharedPreferences sharedPreferences) {
        long sessionStart = getSessionStartTime(sharedPreferences);
        if (sessionStart <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - sessionStart;
    }

    public static boolean hasExceededLimit(SharedPreferences sharedPreferences) {
        if (!isWalkChatActive(sharedPreferences)) {
            return false;
        }
        long elapsedMillis = getElapsedMillis(sharedPreferences);
        return elapsedMillis >= WALK_CHAT_LIMIT_MILLIS;
    }

    public static long getRemainingMillis(SharedPreferences sharedPreferences) {
        long elapsedMillis = getElapsedMillis(sharedPreferences);
        long remaining = WALK_CHAT_LIMIT_MILLIS - elapsedMillis;
        return Math.max(0, remaining);
    }
}
