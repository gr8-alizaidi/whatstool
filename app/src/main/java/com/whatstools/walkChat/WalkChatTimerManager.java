package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class WalkChatTimerManager {
    public static final String KEY_WALK_CHAT_TIMER_START = "walk_chat_timer_start";
    public static final long WALK_CHAT_DURATION_MILLIS = 15 * 60 * 1000;

    private WalkChatTimerManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void startTimer(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        sharedPreferences.edit()
                .putLong(KEY_WALK_CHAT_TIMER_START, System.currentTimeMillis())
                .apply();
    }

    public static void stopTimer(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        sharedPreferences.edit()
                .putLong(KEY_WALK_CHAT_TIMER_START, 0L)
                .apply();
    }

    public static boolean isTimerExpired(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        long timerStart = sharedPreferences.getLong(KEY_WALK_CHAT_TIMER_START, 0L);
        if (timerStart == 0L) {
            return false;
        }
        long elapsedTime = System.currentTimeMillis() - timerStart;
        return elapsedTime >= WALK_CHAT_DURATION_MILLIS;
    }

    public static long getRemainingTimeMillis(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        long timerStart = sharedPreferences.getLong(KEY_WALK_CHAT_TIMER_START, 0L);
        if (timerStart == 0L) {
            return WALK_CHAT_DURATION_MILLIS;
        }
        long elapsedTime = System.currentTimeMillis() - timerStart;
        long remaining = WALK_CHAT_DURATION_MILLIS - elapsedTime;
        return Math.max(0, remaining);
    }

    public static boolean isTimerActive(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        return sharedPreferences.getLong(KEY_WALK_CHAT_TIMER_START, 0L) != 0L;
    }
}
