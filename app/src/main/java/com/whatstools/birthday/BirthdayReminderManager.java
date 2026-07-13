package com.whatstools.birthday;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;

public final class BirthdayReminderManager {
    public static final String KEY_ENABLED = "birthday_reminder_enabled";
    public static final String KEY_MONTH = "birthday_reminder_month";
    public static final String KEY_DAY = "birthday_reminder_day";
    public static final String KEY_HOUR = "birthday_reminder_hour";
    public static final String KEY_MINUTE = "birthday_reminder_minute";
    public static final String KEY_NEXT_TRIGGER = "birthday_reminder_next_trigger";
    public static final int REQUEST_CODE = 7012;

    private BirthdayReminderManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void saveReminder(Context context, int month, int day, int hour, int minute) {
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_MONTH, month)
                .putInt(KEY_DAY, day)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
        scheduleNext(context);
    }

    public static void clearReminder(Context context) {
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_MONTH)
                .remove(KEY_DAY)
                .remove(KEY_HOUR)
                .remove(KEY_MINUTE)
                .remove(KEY_NEXT_TRIGGER)
                .apply();
        cancel(context);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static Calendar buildNextTrigger(Context context) {
        SharedPreferences preferences = prefs(context);
        int month = preferences.getInt(KEY_MONTH, -1);
        int day = preferences.getInt(KEY_DAY, -1);
        int hour = preferences.getInt(KEY_HOUR, 9);
        int minute = preferences.getInt(KEY_MINUTE, 0);

        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.MONTH, month);
        trigger.set(Calendar.DAY_OF_MONTH, day);
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();
        if (!trigger.after(now)) {
            trigger.add(Calendar.YEAR, 1);
        }
        return trigger;
    }

    public static long scheduleNext(Context context) {
        if (!isEnabled(context)) {
            return -1L;
        }
        Calendar trigger = buildNextTrigger(context);
        SharedPreferences preferences = prefs(context);
        preferences.edit().putLong(KEY_NEXT_TRIGGER, trigger.getTimeInMillis()).apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return trigger.getTimeInMillis();
        }

        PendingIntent pendingIntent = buildPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarmManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pendingIntent);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pendingIntent);
            }
        }
        return trigger.getTimeInMillis();
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = buildPendingIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, BirthdayReminderReceiver.class);
        intent.setAction("com.whatstools.birthday.REMINDER");
        int finalFlags = flags;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            finalFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, finalFlags);
    }
}
