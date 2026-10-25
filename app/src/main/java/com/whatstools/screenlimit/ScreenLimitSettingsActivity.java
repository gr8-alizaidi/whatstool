package com.whatstools.screenlimit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whatstools.R;
import com.whatstools.shake_Detector.appPreferences;

public class ScreenLimitSettingsActivity extends AppCompatActivity {
    private CheckBox enableLimit;
    private EditText hoursInput;
    private EditText minutesInput;
    private TextView usageText;

    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_limit_settings);

        this.enableLimit = findViewById(R.id.chkEnableLimit);
        this.hoursInput = findViewById(R.id.inputHours);
        this.minutesInput = findViewById(R.id.inputMinutes);
        this.usageText = findViewById(R.id.txtUsage);
        Button saveButton = findViewById(R.id.btnSaveLimit);
        Button openBlockScreen = findViewById(R.id.btnOpenBlockScreen);

        final android.content.SharedPreferences prefs = ScreenLimitManager.prefs(this);
        ScreenLimitManager.resetIfNewDay(prefs);
        this.enableLimit.setChecked(ScreenLimitManager.isEnabled(prefs));

        int totalMinutes = prefs.getInt(ScreenLimitManager.KEY_LIMIT_MINUTES, 0);
        this.hoursInput.setText(String.valueOf(totalMinutes / 60));
        this.minutesInput.setText(String.valueOf(totalMinutes % 60));
        refreshUsageText(prefs);

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int hours = parseSafe(ScreenLimitSettingsActivity.this.hoursInput.getText().toString());
                int minutes = parseSafe(ScreenLimitSettingsActivity.this.minutesInput.getText().toString());
                if (hours < 0 || minutes < 0 || minutes > 59) {
                    Toast.makeText(ScreenLimitSettingsActivity.this, R.string.screenLimitDescription, Toast.LENGTH_SHORT).show();
                    return;
                }
                int totalMinutes = (hours * 60) + minutes;
                prefs.edit()
                        .putBoolean(ScreenLimitManager.KEY_ENABLED, ScreenLimitSettingsActivity.this.enableLimit.isChecked())
                        .putInt(ScreenLimitManager.KEY_LIMIT_MINUTES, totalMinutes)
                        .putBoolean(ScreenLimitManager.KEY_BLOCKED, false)
                        .apply();
                Toast.makeText(ScreenLimitSettingsActivity.this, "Limit saved", Toast.LENGTH_SHORT).show();
                refreshUsageText(prefs);
            }
        });

        openBlockScreen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ScreenLimitSettingsActivity.this, WhatsAppLimitBlockActivity.class));
            }
        });
    }

    private void applyTheme() {
        appPreferences prefs = new appPreferences(this);
        if (prefs.isDarkModeEnabled()) {
            setTheme(R.style.AppThemeDark);
        } else {
            setTheme(R.style.AppThemeLight);
        }
    }

    private int parseSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception unused) {
            return -1;
        }
    }

    private void refreshUsageText(android.content.SharedPreferences prefs) {
        long used = ScreenLimitManager.getTodayUsageMillis(prefs);
        long limit = ScreenLimitManager.getLimitMillis(prefs);
        String text = "Used today: " + formatDuration(used) + " / " + formatDuration(limit);
        this.usageText.setText(text);
    }

    private String formatDuration(long millis) {
        long totalMinutes = millis / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m";
    }
}
