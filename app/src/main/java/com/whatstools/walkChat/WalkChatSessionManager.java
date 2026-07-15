package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

public final class WalkChatSessionManager {
    private static final String KEY_SESSION_START = "walk_chat_session_start";
    private static final long SESSION_TIMEOUT_MILLIS = 15 * 60 * 1000; // 15 minutes

    private static Handler timerHandler;
    private static Runnable timerRunnable;
    private static WalkSessionCallback callback;

    public interface WalkSessionCallback {
        void onSessionExpired();
    }

    private WalkChatSessionManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void startSession(Context context, WalkSessionCallback sessionCallback) {
        SharedPreferences prefs = prefs(context);
        long sessionStartTime = System.currentTimeMillis();
        prefs.edit().putLong(KEY_SESSION_START, sessionStartTime).apply();

        callback = sessionCallback;
        scheduleSessionTimeout(context);
    }

    public static void stopSession(Context context) {
        SharedPreferences prefs = prefs(context);
        prefs.edit().remove(KEY_SESSION_START).apply();

        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        callback = null;
    }

    public static boolean isSessionActive(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getLong(KEY_SESSION_START, 0) > 0;
    }

    public static long getSessionElapsedMillis(Context context) {
        SharedPreferences prefs = prefs(context);
        long sessionStartTime = prefs.getLong(KEY_SESSION_START, 0);
        if (sessionStartTime <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - sessionStartTime;
    }

    public static boolean hasSessionExpired(Context context) {
        return getSessionElapsedMillis(context) >= SESSION_TIMEOUT_MILLIS;
    }

    public static long getRemainingSessionMillis(Context context) {
        long elapsed = getSessionElapsedMillis(context);
        long remaining = SESSION_TIMEOUT_MILLIS - elapsed;
        return Math.max(0, remaining);
    }

    private static void scheduleSessionTimeout(Context context) {
        if (timerHandler == null) {
            timerHandler = new Handler(Looper.getMainLooper());
        }

        // Remove any existing timeout
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        timerRunnable = () -> {
            if (callback != null) {
                callback.onSessionExpired();
            }
        };

        timerHandler.postDelayed(timerRunnable, SESSION_TIMEOUT_MILLIS);
    }

    public static long getSessionTimeoutMillis() {
        return SESSION_TIMEOUT_MILLIS;
    }
}
