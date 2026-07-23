package com.whatstools.statusSaver;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.clans.fab.FloatingActionButton;
import com.whatstools.R;

import java.io.File;

public class VideoPlayer extends AppCompatActivity {
    VideoView myVideoView;
    String videoPath;


    private class btnDownloadListner implements OnClickListener {
        public void onClick(View view) {
            saveCurrentVideo();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recent_video_player);
        getSupportActionBar().setTitle("Video");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        this.myVideoView = findViewById(R.id.myvideoview);
        FloatingActionButton download = findViewById(R.id.download);
        this.videoPath = getIntent().getExtras() != null ? getIntent().getExtras().getString("Vplay") : null;
        if (this.videoPath == null) {
            Toast.makeText(getApplicationContext(), "Something went wrong", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        this.myVideoView.setVideoPath(this.videoPath);
        this.myVideoView.requestFocus();
        this.myVideoView.start();
        setMediaController();
        download.setOnClickListener(new btnDownloadListner());
    }

    private void setMediaController() {
        this.myVideoView.setMediaController(new MediaController(this));
    }

    private void saveCurrentVideo() {
        if (StatusRepository.saveStatus(this, new File(this.videoPath), true)) {
            Toast.makeText(getApplicationContext(), "Successfully downloaded", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Something went wrong", Toast.LENGTH_SHORT).show();
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
}
