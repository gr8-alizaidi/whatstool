package com.whatstools.birthday;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ReminderScheduler {
    public static final String KEY_ENABLED = "birthday_reminder_enabled";
    public static final String KEY_DAYS_BEFORE = "birthday_reminder_days_before";
    public static final String KEY_NOTIFICATION_STYLE = "birthday_reminder_notification_style";
    public static final String NOTIFICATION_STYLE_SINGLE = "single";
    public static final String NOTIFICATION_STYLE_DUAL = "dual";

    private static final int DEFAULT_DAYS_BEFORE = 1;

    private ReminderScheduler() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static boolean isEnabled(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(SharedPreferences sharedPreferences, boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getDaysBefore(SharedPreferences sharedPreferences) {
        return sharedPreferences.getInt(KEY_DAYS_BEFORE, DEFAULT_DAYS_BEFORE);
    }

    public static void setDaysBefore(SharedPreferences sharedPreferences, int daysBefore) {
        if (daysBefore < 0) {
            throw new IllegalArgumentException("Days before must be non-negative");
        }
        sharedPreferences.edit().putInt(KEY_DAYS_BEFORE, daysBefore).apply();
    }

    public static String getNotificationStyle(SharedPreferences sharedPreferences) {
        return sharedPreferences.getString(KEY_NOTIFICATION_STYLE, NOTIFICATION_STYLE_SINGLE);
    }

    public static void setNotificationStyle(SharedPreferences sharedPreferences, String style) {
        if (!style.equals(NOTIFICATION_STYLE_SINGLE) && !style.equals(NOTIFICATION_STYLE_DUAL)) {
            throw new IllegalArgumentException("Invalid notification style: " + style);
        }
        sharedPreferences.edit().putString(KEY_NOTIFICATION_STYLE, style).apply();
    }

    public static List<ReminderEvent> getReminderEvents(List<BirthdayModel> birthdays, SharedPreferences sharedPreferences) {
        List<ReminderEvent> events = new ArrayList<>();
        if (!isEnabled(sharedPreferences)) {
            return events;
        }

        int daysBefore = getDaysBefore(sharedPreferences);
        String style = getNotificationStyle(sharedPreferences);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        for (BirthdayModel birthday : birthdays) {
            Calendar birthdayDate = Calendar.getInstance();
            birthdayDate.set(Calendar.MONTH, birthday.getMonth() - 1);
            birthdayDate.set(Calendar.DAY_OF_MONTH, birthday.getDayOfMonth());
            birthdayDate.set(Calendar.HOUR_OF_DAY, 0);
            birthdayDate.set(Calendar.MINUTE, 0);
            birthdayDate.set(Calendar.SECOND, 0);
            birthdayDate.set(Calendar.MILLISECOND, 0);

            if (birthday.hasYear()) {
                birthdayDate.set(Calendar.YEAR, birthday.getYear());
            } else {
                birthdayDate.set(Calendar.YEAR, today.get(Calendar.YEAR));
            }

            // Adjust to current year if birthday has already passed
            if (birthdayDate.before(today)) {
                birthdayDate.add(Calendar.YEAR, 1);
            }

            // Primary notification on the day before
            if (daysBefore >= 1) {
                Calendar primaryDate = (Calendar) birthdayDate.clone();
                primaryDate.add(Calendar.DAY_OF_MONTH, -daysBefore);
                if (!primaryDate.before(today)) {
                    events.add(new ReminderEvent(birthday, primaryDate, ReminderEvent.TYPE_PRIMARY));
                }
            }

            // Dual notification: second reminder if style is DUAL and daysBefore > 1
            if (NOTIFICATION_STYLE_DUAL.equals(style) && daysBefore > 1) {
                Calendar secondaryDate = (Calendar) birthdayDate.clone();
                secondaryDate.add(Calendar.DAY_OF_MONTH, -(daysBefore / 2));
                if (!secondaryDate.before(today) && !secondaryDate.equals(birthdayDate)) {
                    events.add(new ReminderEvent(birthday, secondaryDate, ReminderEvent.TYPE_SECONDARY));
                }
            }

            // Birthday day itself
            if (!birthdayDate.before(today)) {
                events.add(new ReminderEvent(birthday, birthdayDate, ReminderEvent.TYPE_BIRTHDAY));
            }
        }

        return events;
    }

    public static class ReminderEvent {
        public static final int TYPE_PRIMARY = 1;
        public static final int TYPE_SECONDARY = 2;
        public static final int TYPE_BIRTHDAY = 3;

        private BirthdayModel birthday;
        private Calendar date;
        private int type;

        public ReminderEvent(BirthdayModel birthday, Calendar date, int type) {
            this.birthday = birthday;
            this.date = date;
            this.type = type;
        }

        public BirthdayModel getBirthday() {
            return birthday;
        }

        public Calendar getDate() {
            return date;
        }

        public int getType() {
            return type;
        }

        public String getTypeLabel() {
            switch (type) {
                case TYPE_PRIMARY:
                    return "primary";
                case TYPE_SECONDARY:
                    return "secondary";
                case TYPE_BIRTHDAY:
                    return "birthday";
                default:
                    return "unknown";
            }
        }
    }
}
