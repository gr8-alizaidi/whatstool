package com.whatstools.statusSaver;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.startapp.android.publish.ads.banner.Mrec;
import com.startapp.android.publish.adsCommon.StartAppAd;
import com.whatstools.Internetconnection;
import com.whatstools.R;

import java.util.ArrayList;

public class StatusSaverMainActivity extends AppCompatActivity implements OnClickListener {
    private static final int REQUEST_STORAGE = 100;

    ImageView recent_stories;
    ImageView saved_stories;

    @SuppressLint({"WrongViewCast"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_statussaver);
        getSupportActionBar().setTitle("Status Saver");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        requestStoragePermissionIfNeeded();
        if (!Internetconnection.checkConnection(this)) {
            Mrec banner = findViewById(R.id.startAppBanner);
            banner.hideBanner();
        }
        this.recent_stories = findViewById(R.id.recent_story);
        this.saved_stories = findViewById(R.id.saved_stories);
        this.recent_stories.setOnClickListener(this);
        this.saved_stories.setOnClickListener(this);
    }

    /**
     * The status saver only needs storage access. READ_EXTERNAL_STORAGE covers
     * every OS version while we target API 30 (on Android 13+ the system maps
     * it to the granular media permissions); WRITE is only meaningful up to
     * Android 9 — from Q onward saving goes through MediaStore instead.
     */
    private String[] getRequiredPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        permissions.add("android.permission.READ_EXTERNAL_STORAGE");
        if (VERSION.SDK_INT <= 28) {
            permissions.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        return permissions.toArray(new String[0]);
    }

    private boolean hasStoragePermission() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestStoragePermissionIfNeeded() {
        if (VERSION.SDK_INT >= 23 && !hasStoragePermission()) {
            requestPermissions(getRequiredPermissions(), REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE || hasStoragePermission()) {
            return;
        }
        boolean canAskAgain = false;
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                canAskAgain = true;
                break;
            }
        }
        if (canAskAgain) {
            Toast.makeText(this, "Storage access is needed to find and save WhatsApp statuses.", Toast.LENGTH_LONG).show();
        } else {
            // "Don't ask again" — the request dialog can no longer be shown.
            new AlertDialog.Builder(this)
                    .setMessage("Storage access is needed to find and save WhatsApp statuses. Please enable it in app settings.")
                    .setPositiveButton("Open Settings", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Not now", null)
                    .show();
        }
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.recent_story:
                LoadAdsRcent();
                return;
            case R.id.saved_stories:
                LoadAdsRSave();
                return;
            default:
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void LoadAdsRcent() {
        startActivity(new Intent(this, RecentStoriesActivity.class));
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
        StartAppAd.showAd(this);

    }

    private void LoadAdsRSave() {
        Intent intent = new Intent(this, SavedStoriesActivity.class);
        intent.putExtra("callingactivity", "maincall");
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
        StartAppAd.showAd(this);
    }
}
