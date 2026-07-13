package com.whatstools.birthday;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whatstools.R;

import java.util.Calendar;

public class BirthdayReminderActivity extends AppCompatActivity {
    private final Calendar selectedDate = Calendar.getInstance();
    private int selectedMonth = -1;
    private int selectedDay = -1;
    private int selectedHour = 9;
    private int selectedMinute = 0;
    private TextView txtSelectedDate;
    private TextView txtSelectedTime;
    private TextView txtNextReminder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_birthday_reminder);

        Button btnPickDate = findViewById(R.id.btnPickDate);
        Button btnPickTime = findViewById(R.id.btnPickTime);
        Button btnSaveReminder = findViewById(R.id.btnSaveReminder);
        Button btnCancelReminder = findViewById(R.id.btnCancelReminder);
        this.txtSelectedDate = findViewById(R.id.txtSelectedDate);
        this.txtSelectedTime = findViewById(R.id.txtSelectedTime);
        this.txtNextReminder = findViewById(R.id.txtNextReminder);

        restoreSavedReminder();

        btnPickDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH);
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                new DatePickerDialog(BirthdayReminderActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker datePicker, int year, int monthOfYear, int dayOfMonth) {
                        selectedMonth = monthOfYear;
                        selectedDay = dayOfMonth;
                        selectedDate.set(Calendar.MONTH, monthOfYear);
                        selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateLabel();
                        updateNextReminderLabel();
                    }
                }, year, month, day).show();
            }
        });

        btnPickTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new TimePickerDialog(BirthdayReminderActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker timePicker, int hourOfDay, int minute) {
                        selectedHour = hourOfDay;
                        selectedMinute = minute;
                        updateTimeLabel();
                        updateNextReminderLabel();
                    }
                }, selectedHour, selectedMinute, DateFormat.is24HourFormat(BirthdayReminderActivity.this)).show();
            }
        });

        btnSaveReminder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedMonth < 0 || selectedDay < 0) {
                    Toast.makeText(BirthdayReminderActivity.this, R.string.birthdayReminderNotSet, Toast.LENGTH_SHORT).show();
                    return;
                }
                BirthdayReminderManager.saveReminder(BirthdayReminderActivity.this, selectedMonth, selectedDay, selectedHour, selectedMinute);
                Toast.makeText(BirthdayReminderActivity.this, R.string.birthdayReminderSaved, Toast.LENGTH_SHORT).show();
                updateNextReminderLabel();
            }
        });

        btnCancelReminder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BirthdayReminderManager.clearReminder(BirthdayReminderActivity.this);
                txtNextReminder.setText("");
                Toast.makeText(BirthdayReminderActivity.this, "Reminder removed", Toast.LENGTH_SHORT).show();
            }
        });

        updateDateLabel();
        updateTimeLabel();
        updateNextReminderLabel();
    }

    private void restoreSavedReminder() {
        android.content.SharedPreferences preferences = BirthdayReminderManager.prefs(this);
        if (!preferences.getBoolean(BirthdayReminderManager.KEY_ENABLED, false)) {
            return;
        }
        selectedMonth = preferences.getInt(BirthdayReminderManager.KEY_MONTH, -1);
        selectedDay = preferences.getInt(BirthdayReminderManager.KEY_DAY, -1);
        selectedHour = preferences.getInt(BirthdayReminderManager.KEY_HOUR, 9);
        selectedMinute = preferences.getInt(BirthdayReminderManager.KEY_MINUTE, 0);
        updateDateLabel();
        updateTimeLabel();
    }

    private void updateDateLabel() {
        if (selectedMonth < 0 || selectedDay < 0) {
            txtSelectedDate.setText("Selected date: none");
            return;
        }
        String monthName = android.text.format.DateFormat.format("MMMM", birthdayCalendarForPreview()).toString();
        txtSelectedDate.setText("Selected date: " + monthName + " " + selectedDay);
    }

    private void updateTimeLabel() {
        txtSelectedTime.setText(String.format("Selected time: %02d:%02d", selectedHour, selectedMinute));
    }

    private void updateNextReminderLabel() {
        if (selectedMonth < 0 || selectedDay < 0) {
            txtNextReminder.setText("");
            return;
        }
        Calendar trigger = BirthdayReminderManager.buildNextTrigger(this);
        txtNextReminder.setText("Next reminder: " + DateFormat.format("EEE, MMM d, yyyy 'at' hh:mm a", trigger));
    }

    private Calendar birthdayCalendarForPreview() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MONTH, selectedMonth);
        calendar.set(Calendar.DAY_OF_MONTH, selectedDay);
        return calendar;
    }
}
