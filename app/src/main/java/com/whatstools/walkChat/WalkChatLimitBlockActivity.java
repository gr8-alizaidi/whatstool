package com.whatstools.walkChat;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.whatstools.R;

public class WalkChatLimitBlockActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_chat_limit_block);
        TextView infoText = findViewById(R.id.txtWalkChatBlockedInfo);
        Button closeButton = findViewById(R.id.btnWalkChatClose);

        infoText.setText(R.string.walkChatLimitBlockMessage);
        closeButton.setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
