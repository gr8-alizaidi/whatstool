package com.whatstools.statusSaver;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.github.clans.fab.FloatingActionButton;
import com.startapp.android.publish.adsCommon.StartAppAd;
import com.whatstools.MainActivity;
import com.whatstools.R;

import java.io.File;
import java.util.ArrayList;

public class ImageViewer extends AppCompatActivity implements ViewPager.OnPageChangeListener {
    int position;
    ArrayList<FileModel> saveimages = ImageGridRecycerAdapter.fileModelArrayList;


    //Button click event of download image
    private class btnImgDownloadListner implements OnClickListener {
        public void onClick(View view) {
            saveCurrentImage();
            ImageViewer.this.AdsCount();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_viewer);
        getSupportActionBar().setTitle("Image");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        FloatingActionButton img_download = findViewById(R.id.img_download);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.position = extras.getInt("Position");
            Log.d("Position get in Images", this.position + "");
            ViewPager viewPager = findViewById(R.id.view_pager);
            viewPager.setAdapter(new ImageAdapter(this));
            viewPager.setCurrentItem(this.position);
            viewPager.addOnPageChangeListener(this);
        }
        img_download.setOnClickListener(new btnImgDownloadListner());
    }

    private void saveCurrentImage() {
        if (this.saveimages == null || this.position >= this.saveimages.size()) {
            Toast.makeText(getApplicationContext(), "Something went wrong", Toast.LENGTH_SHORT).show();
            return;
        }
        File source = new File(this.saveimages.get(this.position).getImageFilePath());
        if (StatusRepository.saveStatus(this, source, false)) {
            Toast.makeText(getApplicationContext(), "Successfully downloaded", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Something went wrong", Toast.LENGTH_SHORT).show();
        }
    }

    private void AdsCount() {
        if (MainActivity.countAds >= 3) {
            LoadAds();
            MainActivity.countAds = 0;
            return;
        }
        MainActivity.countAds++;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void LoadAds() {
        StartAppAd.showAd(this);
    }

    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        this.position = position;
    }

    public void onPageSelected(int position) {
    }

    public void onPageScrollStateChanged(int state) {
    }
}
