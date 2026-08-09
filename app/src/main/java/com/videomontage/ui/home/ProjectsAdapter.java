package com.videomontage.ui.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.videomontage.app.R;
import com.videomontage.core.Timecode;
import com.videomontage.editor.model.Project;

import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ProjectsAdapter extends BaseAdapter {

    public interface Callback {
        void onOpen(Project project);
        void onDelete(Project project);
    }

    private final LayoutInflater inflater;
    private final Callback callback;
    private final List<Project> projects = new ArrayList<>();

    public ProjectsAdapter(LayoutInflater inflater, Callback callback) {
        this.inflater = inflater;
        this.callback = callback;
    }

    public void submit(List<Project> next) {
        projects.clear();
        projects.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return projects.size(); }

    @Override
    public Project getItem(int position) { return projects.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        Holder h;
        if (convert == null) {
            convert = inflater.inflate(R.layout.item_project, parent, false);
            h = new Holder();
            h.thumbnail = convert.findViewById(R.id.thumbnail);
            h.name = convert.findViewById(R.id.name);
            h.meta = convert.findViewById(R.id.meta);
            clipCardToOutline(convert);
            convert.setTag(h);
        } else {
            h = (Holder) convert.getTag();
        }

        final Project p = projects.get(position);
        h.name.setText(p.name);
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(new Date(p.modifiedAtMs));
        h.meta.setText(p.timeline.durationMs() > 0
                ? Timecode.formatShort(p.timeline.durationMs()) + "  ·  " + date
                : date);
        if (p.thumbnailPath != null) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            Bitmap bmp = BitmapFactory.decodeFile(p.thumbnailPath, opts);
            if (bmp != null) h.thumbnail.setImageBitmap(bmp);
            else h.thumbnail.setImageResource(R.drawable.bg_thumb_placeholder);
        } else {
            h.thumbnail.setImageResource(R.drawable.bg_thumb_placeholder);
        }
        convert.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { callback.onOpen(p); }
        });
        convert.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                callback.onDelete(p);
                return true;
            }
        });
        return convert;
    }

    private static Method sClipToOutline;
    private static void clipCardToOutline(View card) {
        try {
            if (sClipToOutline == null) {
                sClipToOutline = View.class.getMethod("setClipToOutline", boolean.class);
            }
            sClipToOutline.invoke(card, true);
        } catch (Throwable ignored) {
        }
    }

    private static final class Holder {
        ImageView thumbnail;
        TextView name;
        TextView meta;
    }
}
