package com.whatstools.statusSaver;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whatstools.R;

import java.util.ArrayList;

public class VideoFragment extends Fragment {
    private ArrayList<FileModel> FilePathStrings;
    private VideoGridRecycerAdapter adapter;
    private TextView datatext;
    private RelativeLayout nodata;
    private RecyclerView recyclerView;
    View view;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.view = inflater.inflate(R.layout.activity_video_display, container, false);
        this.recyclerView = this.view.findViewById(R.id.video_list);
        this.nodata = this.view.findViewById(R.id.nodata);
        this.datatext = this.view.findViewById(R.id.text);
        setRecyclerView();
        return this.view;
    }

    public void onResume() {
        super.onResume();
        setRecyclerView();
    }

    private void setRecyclerView() {
        this.FilePathStrings = StatusRepository.getRecentStatuses(true);
        this.recyclerView = this.view.findViewById(R.id.video_list);
        this.recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        this.recyclerView.setHasFixedSize(true);
        this.adapter = new VideoGridRecycerAdapter(getActivity(), this.FilePathStrings);
        this.recyclerView.setAdapter(this.adapter);
        if (this.adapter.getItemCount() > 0) {
            this.nodata.setVisibility(View.INVISIBLE);
        } else {
            this.nodata.setVisibility(View.VISIBLE);
            this.datatext.setText(hasStoragePermission()
                    ? "No status videos found. Open WhatsApp and view some statuses, then come back here."
                    : "Storage access is needed to show WhatsApp statuses. Please grant the permission and try again.");
        }
    }

    private boolean hasStoragePermission() {
        return getActivity() != null && ContextCompat.checkSelfPermission(getActivity(),
                "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }
}
