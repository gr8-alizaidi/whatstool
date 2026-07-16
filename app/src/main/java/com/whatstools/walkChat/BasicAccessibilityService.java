package com.whatstools.walkChat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;

import com.whatstools.R;
import com.whatstools.screenlimit.ScreenLimitManager;
import com.whatstools.screenlimit.WhatsAppLimitBlockActivity;

public class BasicAccessibilityService extends AccessibilityService {
    public static Context context;
    public static View view;
    private final AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo();
    ActivityManager objActivityMang;
    LayoutInflater layoutInflater;
    private LayoutParams layoutParams;
    private View view1;
    private WindowManager windowManager;
    private String lastPackageName;
    private long whatsappSessionStart;
    private SharedPreferences sharedPreferences;
    private Handler walkChatTimerHandler;
    private Runnable walkChatTimerRunnable;

    @SuppressLint({"ClickableViewAccessibility"})
    public void onServiceConnected() {
        this.windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        this.sharedPreferences = ScreenLimitManager.prefs(this);
        ScreenLimitManager.resetIfNewDay(this.sharedPreferences);
        this.walkChatTimerHandler = new Handler(Looper.getMainLooper());
        this.accessibilityServiceInfo.eventTypes = -1;
        this.accessibilityServiceInfo.feedbackType = 16;
        this.accessibilityServiceInfo.notificationTimeout = 100;
        setServiceInfo(this.accessibilityServiceInfo);
        context = this;
        this.objActivityMang = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        this.layoutInflater = (LayoutInflater) getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.view1 = this.layoutInflater.inflate(R.layout.service_transnlat_text, null);
        this.layoutParams = new LayoutParams(50, 50, 2003, 40, -3);
        this.layoutParams.gravity = 51;
        this.layoutParams.x = 0;
        this.layoutParams.y = 0;
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo();
        accessibilityServiceInfo.eventTypes = 32;
        accessibilityServiceInfo.feedbackType = 16;
        accessibilityServiceInfo.flags = 2;
        setServiceInfo(accessibilityServiceInfo);
        startWalkChatTimerMonitor();
    }

    private void maybeStartBlocker() {
        if (!ScreenLimitManager.isEnabled(this.sharedPreferences)) {
            return;
        }
        if (!ScreenLimitManager.isBlocked(this.sharedPreferences)) {
            ScreenLimitManager.setBlocked(this.sharedPreferences, true);
        }
        Intent intent = new Intent(this, WhatsAppLimitBlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private boolean isWhatsAppPackage(String packageName) {
        return "com.whatsapp".equals(packageName) || "com.whatsapp.w4b".equals(packageName);
    }

    private void stopTrackingWhatsAppSession(long now) {
        if (this.whatsappSessionStart > 0) {
            ScreenLimitManager.addUsage(this.sharedPreferences, now - this.whatsappSessionStart);
            this.whatsappSessionStart = 0;
        }
    }

    private ActivityInfo m18198a(ComponentName componentName) {
        try {
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    @SuppressLint({"NewApi"})
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        Object obj = null;
        try {
            if (accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && accessibilityEvent.getPackageName() != null && accessibilityEvent.getClassName() != null) {
                ComponentName componentName = new ComponentName(accessibilityEvent.getPackageName().toString(), accessibilityEvent.getClassName().toString());
                if (m18198a(componentName) != null) {
                    obj = 1;
                }
                if (obj == null) {
                    return;
                }
                String packageName = componentName.getPackageName();
                long now = System.currentTimeMillis();
                if (this.lastPackageName != null && !this.lastPackageName.equals(packageName) && !isWhatsAppPackage(packageName)) {
                    stopTrackingWhatsAppSession(now);
                }
                this.lastPackageName = packageName;
                if (isWhatsAppPackage(packageName)) {
                    if (ScreenLimitManager.isBlocked(this.sharedPreferences)) {
                        maybeStartBlocker();
                        return;
                    }
                    long limitMillis = ScreenLimitManager.getLimitMillis(this.sharedPreferences);
                    long usedMillis = ScreenLimitManager.getTodayUsageMillis(this.sharedPreferences);
                    if (ScreenLimitManager.isEnabled(this.sharedPreferences) && limitMillis > 0 && usedMillis >= limitMillis) {
                        maybeStartBlocker();
                        return;
                    }
                    if (this.whatsappSessionStart == 0) {
                        this.whatsappSessionStart = now;
                    }
                    if (WalkMainActivity.isWalk) {
                        view = CameraOverlay.methOverlayCheck(this);
                        if (view != null) {
                            view.setAlpha(0.5f);
                        }
                    }
                } else if (view != null) {
                    stopTrackingWhatsAppSession(now);
                    CameraOverlay.methWinManager();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void startWalkChatTimerMonitor() {
        if (this.walkChatTimerRunnable == null) {
            this.walkChatTimerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (WalkChatTimerManager.isWalkChatActive(BasicAccessibilityService.this.sharedPreferences)) {
                        if (WalkChatTimerManager.hasExceededLimit(BasicAccessibilityService.this.sharedPreferences)) {
                            forceCloseWalkChat();
                        } else {
                            BasicAccessibilityService.this.walkChatTimerHandler.postDelayed(this, 5000);
                        }
                    } else {
                        BasicAccessibilityService.this.walkChatTimerHandler.postDelayed(this, 5000);
                    }
                }
            };
        }
        this.walkChatTimerHandler.post(this.walkChatTimerRunnable);
    }

    private void forceCloseWalkChat() {
        if (view != null) {
            CameraOverlay.methWinManager();
            view = null;
        }
        WalkMainActivity.isWalk = false;
        WalkChatTimerManager.stopSession(this.sharedPreferences);
    }

    public void onInterrupt() {
        Log.e("Service", "Interupted");
    }

    public void onDestroy() {
        stopTrackingWhatsAppSession(System.currentTimeMillis());
        if (this.walkChatTimerHandler != null && this.walkChatTimerRunnable != null) {
            this.walkChatTimerHandler.removeCallbacks(this.walkChatTimerRunnable);
        }
        super.onDestroy();
    }
}
