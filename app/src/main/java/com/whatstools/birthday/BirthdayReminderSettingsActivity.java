package com.whatstools.birthday;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.Toast;

public class BirthdayReminderSettingsActivity extends AppCompatActivity {
    private CheckBox enabledCheckBox;
    private EditText daysBeforeEditText;
    private Spinner notificationStyleSpinner;
    private Button saveButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_birthday_reminder_settings);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        enabledCheckBox = findViewById(R.id.birthday_reminder_enabled_checkbox);
        daysBeforeEditText = findViewById(R.id.birthday_reminder_days_before_edittext);
        notificationStyleSpinner = findViewById(R.id.birthday_reminder_notification_style_spinner);
        saveButton = findViewById(R.id.birthday_reminder_save_button);

        setupNotificationStyleSpinner();
        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void setupNotificationStyleSpinner() {
        String[] styles = {ReminderScheduler.NOTIFICATION_STYLE_SINGLE, ReminderScheduler.NOTIFICATION_STYLE_DUAL};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, styles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationStyleSpinner.setAdapter(adapter);
    }

    private void loadSettings() {
        boolean isEnabled = ReminderScheduler.isEnabled(sharedPreferences);
        int daysBefore = ReminderScheduler.getDaysBefore(sharedPreferences);
        String notificationStyle = ReminderScheduler.getNotificationStyle(sharedPreferences);

        enabledCheckBox.setChecked(isEnabled);
        daysBeforeEditText.setText(String.valueOf(daysBefore));

        int spinnerPosition = notificationStyle.equals(ReminderScheduler.NOTIFICATION_STYLE_DUAL) ? 1 : 0;
        notificationStyleSpinner.setSelection(spinnerPosition);
    }

    private void saveSettings() {
        try {
            boolean isEnabled = enabledCheckBox.isChecked();
            int daysBefore = Integer.parseInt(daysBeforeEditText.getText().toString());
            String notificationStyle = (String) notificationStyleSpinner.getSelectedItem();

            ReminderScheduler.setEnabled(sharedPreferences, isEnabled);
            ReminderScheduler.setDaysBefore(sharedPreferences, daysBefore);
            ReminderScheduler.setNotificationStyle(sharedPreferences, notificationStyle);

            Toast.makeText(this, "Birthday reminder settings saved", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid number for days before", Toast.LENGTH_SHORT).show();
        }
    }
}
