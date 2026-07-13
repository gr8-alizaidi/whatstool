package com.whatstools.birthday;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.whatstools.R;

import java.util.Calendar;

public class BirthdayReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "birthday_reminders";
    private static final int NOTIFICATION_ID = 7012;

    @Override
    public void onReceive(Context context, Intent intent) {
        ensureChannel(context);

        android.content.SharedPreferences preferences = BirthdayReminderManager.prefs(context);
        int month = preferences.getInt(BirthdayReminderManager.KEY_MONTH, Calendar.JANUARY);
        int day = preferences.getInt(BirthdayReminderManager.KEY_DAY, 1);
        Calendar nextTrigger = BirthdayReminderManager.buildNextTrigger(context);
        String title = "Birthday reminder";
        Calendar reminderDate = Calendar.getInstance();
        reminderDate.set(Calendar.MONTH, month);
        reminderDate.set(Calendar.DAY_OF_MONTH, day);
        String monthName = android.text.format.DateFormat.format("MMMM", reminderDate).toString();
        String body = "Time to remind your contact about their birthday on " + monthName + " " + day + ".";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        BirthdayReminderManager.scheduleNext(context);
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Birthday reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifications for saved birthday reminders");
        manager.createNotificationChannel(channel);
    }
}
