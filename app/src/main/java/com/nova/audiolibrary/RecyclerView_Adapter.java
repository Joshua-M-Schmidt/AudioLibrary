package com.nova.audiolibrary;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * Created by Valdio Veliu on 16-07-08.
 */
public class RecyclerView_Adapter extends RecyclerView.Adapter<ViewHolder> {

    List<Audio> list;
    Context context;

    public RecyclerView_Adapter(List<Audio> list, Context context) {
        this.list = list;
        this.context = context;

    }



    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //Inflate the layout, initialize the View Holder
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.audio, parent, false);
        ViewHolder holder = new ViewHolder(v);
        return holder;

    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        //Use the provided View Holder on the onCreateViewHolder method to populate the current row on the RecyclerView
        holder.title.setText(list.get(position).getTitle());
        int time = list.get(position).getLenght();
        int playTime = list.get(position).getTime();
        float progressNumber = (float)playTime/(float)time*100f;
        holder.progress.setProgress((int)progressNumber);
        holder.percent.setText((int)progressNumber+"%");
        holder.duration.setText(MainActivity.milliSecondsToTimer(playTime)+"/"+MainActivity.milliSecondsToTimer(time));
    }

    @Override
    public int getItemCount() {
        //returns the number of elements the RecyclerView will display
        return list.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

}

class ViewHolder extends RecyclerView.ViewHolder {

    TextView title;
    TextView duration;
    ImageView play_pause;
    TextView percent;
    ProgressBar progress;

    ViewHolder(View itemView) {
        super(itemView);
        progress = itemView.findViewById(R.id.progressBar);
        percent = itemView.findViewById(R.id.percent);
        title =  itemView.findViewById(R.id.title);
        play_pause =  itemView.findViewById(R.id.play_pause);
        duration = itemView.findViewById(R.id.duration);
    }
}