package com.whatstools.statusSaver;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whatstools.R;

import java.util.ArrayList;

public class ImagesSavedFragment extends Fragment {
    public static ArrayList<FileModel> FilePathStrings;
    private SavedImageGridRecycerAdapter adapter;
    private TextView datatext;
    private RelativeLayout nodata;
    private RecyclerView recyclerView;
    private View views;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.views = inflater.inflate(R.layout.image_display, container, false);
        this.nodata = this.views.findViewById(R.id.nodata);
        this.datatext = this.views.findViewById(R.id.text);
        setRecyclerView();
        return this.views;
    }

    public void onResume() {
        super.onResume();
        setRecyclerView();
    }

    public void setRecyclerView() {
        FilePathStrings = StatusRepository.getSavedStatuses(false);
        this.recyclerView = this.views.findViewById(R.id.imgGridRecyclerView);
        this.recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        this.recyclerView.setHasFixedSize(true);
        this.adapter = new SavedImageGridRecycerAdapter(getActivity(), FilePathStrings);
        this.recyclerView.setAdapter(this.adapter);
        if (this.adapter.getItemCount() > 0) {
            this.nodata.setVisibility(View.INVISIBLE);
        } else {
            this.nodata.setVisibility(View.VISIBLE);
            this.datatext.setText("You have no saved images yet.");
        }
    }
}
