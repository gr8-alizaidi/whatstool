package com.whatstools;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.startapp.android.publish.ads.banner.Banner;
import com.startapp.android.publish.adsCommon.StartAppAd;
import com.whatstools.asciiFaces.AsciiFacesMainActivity;
import com.whatstools.captionStatusShare.Captionitem;
import com.whatstools.cleaner.WACleanMainActivity;
import com.whatstools.directChat.ChatDirect;
import com.whatstools.emojiText.Texttoemoji;
import com.whatstools.fackChat.MainFackChat;
import com.whatstools.gallery.MainWhatsGalleryActivity;
import com.whatstools.shakeShortcut.ShakeMain;
import com.whatstools.statusSaver.StatusSaverMainActivity;
import com.whatstools.textRepeater.MainTextRepeater;
import com.whatstools.screenlimit.ScreenLimitSettingsActivity;
import com.whatstools.walkChat.WalkMainActivity;
import com.whatstools.whatsWebScan.WebActivity;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_RECENT_TOOLS = "recent_tools";
    private static final String KEY_RECENT_TOOLS = "recent_tools_ids";
    private static final int MAX_RECENT_TOOLS = 3;

    private static final String TOOL_WEB = "web";
    private static final String TOOL_WALK = "walk";
    private static final String TOOL_STATUS = "status";
    private static final String TOOL_DIRECT = "direct";
    private static final String TOOL_FAKE = "fake";
    private static final String TOOL_CLEANER = "cleaner";
    private static final String TOOL_ASCII = "ascii";
    private static final String TOOL_REPEAT = "repeat";
    private static final String TOOL_CAPTION = "caption";
    private static final String TOOL_EMOJI = "emoji";
    private static final String TOOL_SHORTCUT = "shortcut";
    private static final String TOOL_GALLERY = "gallery";
    private static final String TOOL_SCREEN_LIMIT = "screen_limit";

    public static int countAds;
    LinearLayout linearRateUs;
    LinearLayout whatsWeb;
    LinearLayout linearWhatsApAsciFace;
    LinearLayout linearWPCaptionStatus;
    LinearLayout linearWPCleaner;
    LinearLayout linearWPDirectChat;
    LinearLayout linearWPEmojis;
    LinearLayout linearWPFakeChat;
    LinearLayout linearWPAppGallery;
    LinearLayout linearWPAppShortcut;
    LinearLayout linearWpAppStatusSaver;
    LinearLayout linearWPTextRepeter;
    LinearLayout linearWPWalk;
    LinearLayout linearScreenLimit;
    LinearLayout recentToolsContainer;
    TextView recentTool1;
    TextView recentTool2;
    TextView recentTool3;
    private InterstitialAd mInterstitialAdMob;
    public int varCounter;
    int isCallFor;


    //Initialisation Method of this view
    @SuppressLint({"ObsoleteSdkInt"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showGoogleInterstitial();

        if (!Internetconnection.checkConnection(this)) {
            Banner banner = findViewById(R.id.startAppBanner);
            banner.hideBanner();
        }
        if (VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
            Log.e("Packagename", getPackageName());
            startActivityForResult(intent, 1234);
        }
//        if (!(ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_NETWORK_STATE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.SET_WALLPAPER") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.INTERNET") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.SYSTEM_ALERT_WINDOW") == 0) && VERSION.SDK_INT >= 23) {
//            requestPermissions(new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.ACCESS_NETWORK_STATE", "android.permission.SET_WALLPAPER", "android.permission.INTERNET", "android.permission.SYSTEM_ALERT_WINDOW"}, 0);
//        }
        this.whatsWeb = findViewById(R.id.whtsWeb);
        this.linearWPAppShortcut = findViewById(R.id.shortcut);
        this.linearWPAppGallery = findViewById(R.id.Gallery);
        this.linearWPWalk = findViewById(R.id.walkchat);
        this.linearWPCleaner = findViewById(R.id.cleaner);
        this.linearWPDirectChat = findViewById(R.id.directChat);
        this.linearWpAppStatusSaver = findViewById(R.id.StatusSaver);
        this.linearWPEmojis = findViewById(R.id.Textemoji);
        this.linearWPCaptionStatus = findViewById(R.id.caption);
        this.linearWPTextRepeter = findViewById(R.id.RepeatText);
        this.linearWPFakeChat = findViewById(R.id.FackChat);
        this.linearWhatsApAsciFace = findViewById(R.id.ascifaces);
        this.linearRateUs = findViewById(R.id.rateus);
        this.linearScreenLimit = findViewById(R.id.screenLimit);
        this.recentToolsContainer = findViewById(R.id.recentToolsContainer);
        this.recentTool1 = findViewById(R.id.recentTool1);
        this.recentTool2 = findViewById(R.id.recentTool2);
        this.recentTool3 = findViewById(R.id.recentTool3);

        this.linearWPAppShortcut.setOnClickListener(new btnWhatsappShortcutListner());
        this.whatsWeb.setOnClickListener(new btnWhatsWebClick());
        this.linearWPAppGallery.setOnClickListener(new btnWpAppGallryLiatner());
        this.linearWPWalk.setOnClickListener(new btnWpWalkChatListner());
        this.linearWPCleaner.setOnClickListener(new btnWpCleanerListner());
        this.linearWPDirectChat.setOnClickListener(new btnWpDirectChatListner());
        this.linearWpAppStatusSaver.setOnClickListener(new btnWpStatusSaverListner());
        this.linearWPEmojis.setOnClickListener(new btnWpEmojiListner());
        this.linearWPCaptionStatus.setOnClickListener(new btnWpCaptionStatusListner());
        this.linearWPTextRepeter.setOnClickListener(new btnWpTextRepeterLitstner());
        this.linearScreenLimit.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.openTool(TOOL_SCREEN_LIMIT);
            }
        });

        //Fake Chat Method
        this.linearWPFakeChat.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.openTool(TOOL_FAKE);
            }
        });

        //Create ASCI Faced Method
        this.linearWhatsApAsciFace.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.openTool(TOOL_ASCII);
            }
        });
        //Rate Us method
        this.linearRateUs.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.RateApp();
            }
        });

        bindRecentToolClick(this.recentTool1);
        bindRecentToolClick(this.recentTool2);
        bindRecentToolClick(this.recentTool3);
        updateRecentToolsUi();
    }


    //Click event of Button Whatsapp Shortcut
    private class btnWhatsappShortcutListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_SHORTCUT);
        }
    }

    //Click event of Button Whatsapp Web
    private class btnWhatsWebClick implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_WEB);
        }
    }

    //Click event of Button gallery
    private class btnWpAppGallryLiatner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_GALLERY);
        }
    }

    //Click event of Walk and chat Button
    private class btnWpWalkChatListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_WALK);
        }
    }

    //Click event of cleaner Button
    private class btnWpCleanerListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_CLEANER);
        }
    }


    //Click event of Direct Chat Without saving number Button
    private class btnWpDirectChatListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_DIRECT);
        }
    }

    //Click event of WP Status Saver Button
    private class btnWpStatusSaverListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_STATUS);
        }
    }

    //Click event of Emoji Creator Button
    private class btnWpEmojiListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_EMOJI);
        }
    }


    //Click event of Caption or Status Button
    private class btnWpCaptionStatusListner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_CAPTION);
        }
    }


    //Click event of Text Repeater Button
    private class btnWpTextRepeterLitstner implements OnClickListener {
        public void onClick(View v) {
            MainActivity.this.openTool(TOOL_REPEAT);
        }
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    //Rate Us Method
    private void RateApp() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.main_rate_dialog, null);
        dialogBuilder.setView(dialogView);
        Button rate_us = dialogView.findViewById(R.id.btn_rate_us);
        Button cancle = dialogView.findViewById(R.id.btn_cancle);
        final AlertDialog b = dialogBuilder.create();
        rate_us.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                try {
                    MainActivity.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://play.google.com/store/apps/details?id=" + MainActivity.this.getPackageName())));
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://play.google.com/store/apps/details?id=" + MainActivity.this.getPackageName())));
                }
                b.cancel();
            }
        });
        cancle.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                b.cancel();
            }
        });
        b.show();
    }

    @Override
    protected void onStart() {
        manageAd();
        super.onStart();
    }

    public void manageAd() {
        SharedPreferences sp = getSharedPreferences("WhatzWebScan", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        varCounter = sp.getInt("counter", 0);
        Log.e("VAR", "" + varCounter);
        if (varCounter == 0) {
            requestNewGoogleInterstitial();
            editor.putInt("counter", varCounter + 1);
        } else if (varCounter >= 2) {
            editor.putInt("counter", 0);
        } else {
            editor.putInt("counter", varCounter + 1);
        }
        editor.apply();
    }

    private void bindRecentToolClick(final TextView textView) {
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = textView.getTag();
                if (tag instanceof String) {
                    openTool((String) tag);
                }
            }
        });
    }

    private void openTool(String toolId) {
        if (TOOL_SCREEN_LIMIT.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, ScreenLimitSettingsActivity.class));
            addRecentTool(toolId);
            return;
        }

        if (varCounter == 0) {
            if (mInterstitialAdMob != null && mInterstitialAdMob.isLoaded()) {
                rememberPendingTool(toolId);
                mInterstitialAdMob.show();
                return;
            }
        }

        launchTool(toolId);
        addRecentTool(toolId);
        StartAppAd.showAd(MainActivity.this);
    }

    private void rememberPendingTool(String toolId) {
        if (TOOL_WALK.equals(toolId)) {
            isCallFor = 1;
        } else if (TOOL_WEB.equals(toolId)) {
            isCallFor = 0;
        } else if (TOOL_STATUS.equals(toolId)) {
            isCallFor = 2;
        } else if (TOOL_DIRECT.equals(toolId)) {
            isCallFor = 3;
        } else if (TOOL_FAKE.equals(toolId)) {
            isCallFor = 4;
        } else if (TOOL_CLEANER.equals(toolId)) {
            isCallFor = 5;
        } else if (TOOL_ASCII.equals(toolId)) {
            isCallFor = 6;
        } else if (TOOL_REPEAT.equals(toolId)) {
            isCallFor = 7;
        } else if (TOOL_CAPTION.equals(toolId)) {
            isCallFor = 8;
        } else if (TOOL_EMOJI.equals(toolId)) {
            isCallFor = 9;
        } else if (TOOL_SHORTCUT.equals(toolId)) {
            isCallFor = 10;
        } else if (TOOL_GALLERY.equals(toolId)) {
            isCallFor = 11;
        }
    }

    private void launchTool(String toolId) {
        if (TOOL_WEB.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, WebActivity.class));
        } else if (TOOL_WALK.equals(toolId)) {
            Toast.makeText(getApplicationContext(),"Please turn on Accessibility Service for Whats Tools",Toast.LENGTH_LONG).show();
            startActivity(new Intent(MainActivity.this, WalkMainActivity.class));
        } else if (TOOL_STATUS.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, StatusSaverMainActivity.class));
        } else if (TOOL_DIRECT.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, ChatDirect.class));
        } else if (TOOL_FAKE.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, MainFackChat.class));
        } else if (TOOL_CLEANER.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, WACleanMainActivity.class));
        } else if (TOOL_ASCII.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, AsciiFacesMainActivity.class));
        } else if (TOOL_REPEAT.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, MainTextRepeater.class));
        } else if (TOOL_CAPTION.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, Captionitem.class));
        } else if (TOOL_EMOJI.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, Texttoemoji.class));
        } else if (TOOL_SHORTCUT.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, ShakeMain.class));
        } else if (TOOL_GALLERY.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, MainWhatsGalleryActivity.class));
        } else if (TOOL_SCREEN_LIMIT.equals(toolId)) {
            startActivity(new Intent(MainActivity.this, ScreenLimitSettingsActivity.class));
        }
    }

    private void addRecentTool(String toolId) {
        SharedPreferences sp = getSharedPreferences(PREF_RECENT_TOOLS, MODE_PRIVATE);
        String recentValue = sp.getString(KEY_RECENT_TOOLS, "");
        String[] parts = TextUtils.isEmpty(recentValue) ? new String[0] : recentValue.split(",");
        java.util.LinkedList<String> recentTools = new java.util.LinkedList<>();
        for (String part : parts) {
            if (!TextUtils.isEmpty(part) && !toolId.equals(part)) {
                recentTools.add(part);
            }
        }
        recentTools.addFirst(toolId);
        while (recentTools.size() > MAX_RECENT_TOOLS) {
            recentTools.removeLast();
        }
        StringBuilder builder = new StringBuilder();
        for (String recentTool : recentTools) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(recentTool);
        }
        sp.edit().putString(KEY_RECENT_TOOLS, builder.toString()).apply();
        updateRecentToolsUi();
    }

    private void updateRecentToolsUi() {
        SharedPreferences sp = getSharedPreferences(PREF_RECENT_TOOLS, MODE_PRIVATE);
        String recentValue = sp.getString(KEY_RECENT_TOOLS, "");
        String[] parts = TextUtils.isEmpty(recentValue) ? new String[0] : recentValue.split(",");
        TextView[] views = new TextView[]{recentTool1, recentTool2, recentTool3};
        for (int i = 0; i < views.length; i++) {
            TextView view = views[i];
            if (i < parts.length) {
                String toolId = parts[i];
                view.setText(getToolLabel(toolId));
                view.setTag(toolId);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setTag(null);
                view.setVisibility(View.GONE);
            }
        }
        recentToolsContainer.setVisibility(parts.length > 0 ? View.VISIBLE : View.GONE);
    }

    private String getToolLabel(String toolId) {
        if (TOOL_WEB.equals(toolId)) {
            return "Whats Web";
        } else if (TOOL_WALK.equals(toolId)) {
            return "Walk & Chat";
        } else if (TOOL_STATUS.equals(toolId)) {
            return "Status Saver";
        } else if (TOOL_DIRECT.equals(toolId)) {
            return "Direct Chat";
        } else if (TOOL_FAKE.equals(toolId)) {
            return "Fake Chat";
        } else if (TOOL_CLEANER.equals(toolId)) {
            return "Cleaner";
        } else if (TOOL_ASCII.equals(toolId)) {
            return "ASCII Faces";
        } else if (TOOL_REPEAT.equals(toolId)) {
            return "Text Repeater";
        } else if (TOOL_CAPTION.equals(toolId)) {
            return "Caption";
        } else if (TOOL_EMOJI.equals(toolId)) {
            return "Emoji";
        } else if (TOOL_SHORTCUT.equals(toolId)) {
            return "Shortcut";
        } else if (TOOL_GALLERY.equals(toolId)) {
            return "Gallery";
        } else if (TOOL_SCREEN_LIMIT.equals(toolId)) {
            return "Screen Limit";
        }
        return "Tool";
    }

    //GOOGLE AD
    public void showGoogleInterstitial() {
        this.mInterstitialAdMob = new com.google.android.gms.ads.InterstitialAd(this);
        this.mInterstitialAdMob.setAdUnitId(getString(R.string.interstitial_id));
        this.mInterstitialAdMob.setAdListener(new GoogleAdListner());
    }

    private void requestNewGoogleInterstitial() {
        this.mInterstitialAdMob.loadAd(new AdRequest.Builder().addTestDevice("437639BF640142DFB48A98851705A70F").build());
    }

    private class GoogleAdListner extends com.google.android.gms.ads.AdListener {
        @SuppressLint("WrongConstant")
        public void onAdClosed() {
            if (isCallFor == 0) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, WebActivity.class));
            } else if (isCallFor == 1) {
                Toast.makeText(getApplicationContext(),"Please turn on Accessibility Service for Whats Tools",Toast.LENGTH_LONG).show();
                MainActivity.this.startActivity(new Intent(MainActivity.this, WalkMainActivity.class));
            } else if (isCallFor == 2) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, StatusSaverMainActivity.class));
            } else if (isCallFor == 3) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, ChatDirect.class));
            } else if (isCallFor == 4) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, MainFackChat.class));
            } else if (isCallFor == 5) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, WACleanMainActivity.class));
            } else if (isCallFor == 6) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, AsciiFacesMainActivity.class));
            } else if (isCallFor == 7) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, MainTextRepeater.class));
            } else if (isCallFor == 8) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, Captionitem.class));
            } else if (isCallFor == 9) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, Texttoemoji.class));
            } else if (isCallFor == 10) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, ShakeMain.class));
            } else if (isCallFor == 11) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, MainWhatsGalleryActivity.class));
            }
            MainActivity.this.addRecentTool(toolIdForCall(isCallFor));
        }

        @Override
        public void onAdFailedToLoad(int i) {
            super.onAdFailedToLoad(i);
        }
    }

    private String toolIdForCall(int callFor) {
        if (callFor == 0) {
            return TOOL_WEB;
        } else if (callFor == 1) {
            return TOOL_WALK;
        } else if (callFor == 2) {
            return TOOL_STATUS;
        } else if (callFor == 3) {
            return TOOL_DIRECT;
        } else if (callFor == 4) {
            return TOOL_FAKE;
        } else if (callFor == 5) {
            return TOOL_CLEANER;
        } else if (callFor == 6) {
            return TOOL_ASCII;
        } else if (callFor == 7) {
            return TOOL_REPEAT;
        } else if (callFor == 8) {
            return TOOL_CAPTION;
        } else if (callFor == 9) {
            return TOOL_EMOJI;
        } else if (callFor == 10) {
            return TOOL_SHORTCUT;
        } else if (callFor == 11) {
            return TOOL_GALLERY;
        }
        return "";
    }

}
