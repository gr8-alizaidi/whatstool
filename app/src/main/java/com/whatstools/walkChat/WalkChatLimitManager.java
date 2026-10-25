package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class WalkChatLimitManager {
    public static final String KEY_SESSION_START = "walk_chat_session_start";
    public static final long SESSION_LIMIT_MILLIS = 15 * 60 * 1000; // 15 minutes

    private WalkChatLimitManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void startSession(SharedPreferences prefs) {
        long now = System.currentTimeMillis();
        prefs.edit().putLong(KEY_SESSION_START, now).apply();
    }

    public static void endSession(SharedPreferences prefs) {
        prefs.edit().putLong(KEY_SESSION_START, 0L).apply();
    }

    public static long getSessionElapsedMillis(SharedPreferences prefs) {
        long sessionStart = prefs.getLong(KEY_SESSION_START, 0L);
        if (sessionStart <= 0) {
            return 0L;
        }
        return System.currentTimeMillis() - sessionStart;
    }

    public static boolean isSessionActive(SharedPreferences prefs) {
        return prefs.getLong(KEY_SESSION_START, 0L) > 0L;
    }

    public static long getRemainingTimeMillis(SharedPreferences prefs) {
        long elapsed = getSessionElapsedMillis(prefs);
        long remaining = SESSION_LIMIT_MILLIS - elapsed;
        return Math.max(0L, remaining);
    }

    public static boolean isSessionLimitReached(SharedPreferences prefs) {
        return getRemainingTimeMillis(prefs) <= 0L;
    }
}
