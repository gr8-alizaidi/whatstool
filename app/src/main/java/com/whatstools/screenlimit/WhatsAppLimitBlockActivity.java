package com.whatstools.screenlimit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.whatstools.R;

public class WhatsAppLimitBlockActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_limit_block);
        TextView usageText = findViewById(R.id.txtBlockedInfo);
        TextView resetText = findViewById(R.id.txtBlockedReset);
        Button settingsButton = findViewById(R.id.btnOpenLimitSettings);

        android.content.SharedPreferences prefs = ScreenLimitManager.prefs(this);
        long used = ScreenLimitManager.getTodayUsageMillis(prefs);
        long limit = ScreenLimitManager.getLimitMillis(prefs);
        usageText.setText("Used today: " + formatDuration(used) + " / " + formatDuration(limit));
        resetText.setText(R.string.screenLimitNextReset);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(WhatsAppLimitBlockActivity.this, ScreenLimitSettingsActivity.class));
            }
        });
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private String formatDuration(long millis) {
        long totalMinutes = millis / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m";
    }
}
