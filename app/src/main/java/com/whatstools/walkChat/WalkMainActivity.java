package com.whatstools.walkChat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.startapp.android.publish.ads.banner.Mrec;
import com.whatstools.Internetconnection;
import com.whatstools.R;

public class WalkMainActivity extends AppCompatActivity {
    public static boolean isWalk = true;
    RelativeLayout Back;
    ImageView BtnWalk;
    RelativeLayout OpenWhatsApp;
    private CountDownTimer walkChatTimer;
    private SharedPreferences sharedPreferences;
    private TextView timerTextView;



    //Click event of Button Back
    private class btnBackPressed implements OnClickListener {

        public void onClick(View v) {
            WalkMainActivity.this.onBackPressed();
        }
    }


    //Click event of Button Open Whatsapp
    private class btnOpenWhatsappListner implements OnClickListener {

        public void onClick(View v) {
            WalkMainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage("com.whatsapp"));
        }
    }

    //Click event of Button Walk
    private class btnWalkListner implements OnClickListener {

        public void onClick(View v) {
            if (WalkMainActivity.isWalk) {
                WalkMainActivity.isWalk = false;
                WalkMainActivity.this.BtnWalk.setImageResource(R.drawable.offs);
                stopWalkChatTimer();
                return;
            }
            WalkMainActivity.isWalk = true;
            WalkMainActivity.this.BtnWalk.setImageResource(R.drawable.ons);
            startWalkChatTimer();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_walk);

        sharedPreferences = WalkChatLimitManager.prefs(this);

        if (!Internetconnection.checkConnection(this)) {
            Mrec banner = findViewById(R.id.startAppBanner);
            banner.hideBanner();
        }

        if (!(ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_NETWORK_STATE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.SET_WALLPAPER") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.INTERNET") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.SYSTEM_ALERT_WINDOW") == 0) && VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.ACCESS_NETWORK_STATE", "android.permission.SET_WALLPAPER", "android.permission.INTERNET", "android.permission.SYSTEM_ALERT_WINDOW"}, 0);
        }
        if (VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
            Log.e("Packagename", getPackageName());
            startActivityForResult(intent, 1234);
        }
        this.BtnWalk = findViewById(R.id.btnWalk);
        if (!accessibilityPermission(getApplicationContext(), BasicAccessibilityService.class)) {
            startActivityForResult(new Intent("android.settings.ACCESSIBILITY_SETTINGS"), 5000);
        }
        this.Back = findViewById(R.id.back);
        this.OpenWhatsApp = findViewById(R.id.openWhatsapp);
        this.Back.setOnClickListener(new btnBackPressed());
        this.OpenWhatsApp.setOnClickListener(new btnOpenWhatsappListner());
        this.BtnWalk.setOnClickListener(new btnWalkListner());

        if (isWalk && WalkChatLimitManager.isSessionActive(sharedPreferences)) {
            startWalkChatTimer();
        }
    }

    public static boolean accessibilityPermission(Context context, Class<?> cls) {
        ComponentName componentName = new ComponentName(context, cls);
        String string = Secure.getString(context.getContentResolver(), "enabled_accessibility_services");
        if (string == null) {
            return false;
        }
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter(':');
        simpleStringSplitter.setString(string);
        while (simpleStringSplitter.hasNext()) {
            ComponentName unflattenFromString = ComponentName.unflattenFromString(simpleStringSplitter.next());
            if (unflattenFromString != null && unflattenFromString.equals(componentName)) {
                return true;
            }
        }
        return false;
    }

    public void onBackPressed() {
        stopWalkChatTimer();
        super.onBackPressed();
        finish();
    }

    private void startWalkChatTimer() {
        if (walkChatTimer != null) {
            walkChatTimer.cancel();
        }
        WalkChatLimitManager.startSession(sharedPreferences);
        long remainingMillis = WalkChatLimitManager.getRemainingTimeMillis(sharedPreferences);
        walkChatTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (WalkChatLimitManager.isSessionLimitReached(sharedPreferences)) {
                    closeWalkChatFeature();
                    cancel();
                }
            }

            @Override
            public void onFinish() {
                closeWalkChatFeature();
            }
        };
        walkChatTimer.start();
    }

    private void stopWalkChatTimer() {
        if (walkChatTimer != null) {
            walkChatTimer.cancel();
            walkChatTimer = null;
        }
        WalkChatLimitManager.endSession(sharedPreferences);
    }

    private void closeWalkChatFeature() {
        if (walkChatTimer != null) {
            walkChatTimer.cancel();
        }
        WalkMainActivity.isWalk = false;
        BtnWalk.setImageResource(R.drawable.offs);
        WalkChatLimitManager.endSession(sharedPreferences);
    }
}
