package com.videomontage.ui.editor;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.videomontage.app.R;
import com.videomontage.core.Timecode;
import com.videomontage.decoder.MediaProbe;
import com.videomontage.decoder.WaveformExtractor;
import com.videomontage.editor.model.AudioClip;
import com.videomontage.editor.model.ClipTiming;
import com.videomontage.editor.model.ImageClip;
import com.videomontage.editor.model.Project;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.model.Transform;
import com.videomontage.editor.model.VideoClip;
import com.videomontage.editor.ops.TimelineOps;
import com.videomontage.export.ExportManager;
import com.videomontage.nativecore.NativeEngine;
import com.videomontage.preview.PreviewController;
import com.videomontage.project.ProjectRepository;
import com.videomontage.storage.MediaImporter;
import com.videomontage.storage.StoragePaths;
import com.videomontage.timeline.TimelineEngine;
import com.videomontage.utils.ViewFx;
import com.videomontage.widgets.PreviewView;
import com.videomontage.widgets.TimelineView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EditorActivity extends Activity {

    public static final String EXTRA_PROJECT_ID = "project_id";

    private ProjectRepository repository;
    private Project project;
    private TimelineEngine engine;
    private PreviewController preview;
    private TimelineView timelineView;
    private PreviewView previewView;
    private TextView timecode;
    private TextView durationLabel;
    private View transport;
    private ImageButton playPause;
    private static final int REQ_PICK_MEDIA = 42;

    private final ExportManager exportManager = new ExportManager();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        repository = new ProjectRepository(this);

        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        project = repository.load(projectId);
        if (project == null) {
            finish();
            return;
        }

        engine = new TimelineEngine(project.timeline);
        preview = new PreviewController();

        bindViews();
        wireTimeline();
        wirePreview();
        wireTransport();
        updateTimecode(0);
        updateDuration(engine.timeline());

        ViewFx.fadeSlideIn(transport, 160);
        ViewFx.fadeSlideIn(timelineView, 240);
    }

    private void bindViews() {
        previewView = findViewById(R.id.preview);
        timelineView = findViewById(R.id.timeline);
        timecode = findViewById(R.id.timecode);
        durationLabel = findViewById(R.id.durationLabel);
        transport = findViewById(R.id.transport);
        playPause = findViewById(R.id.playPause);
        ((TextView) findViewById(R.id.projectName)).setText(project.name);
    }

    private void wireTimeline() {
        timelineView.setTimeline(engine.timeline());
        timelineView.setListener(new TimelineView.Listener() {
            @Override public void onClipSelected(String clipId) {
                engine.select(clipId);
                timelineView.setSelectedClip(clipId);
            }

            @Override public void onClipMoved(String clipId, long newPositionMs) {
                engine.moveClip(clipId, newPositionMs, null, 0.12f);
            }

            @Override public void onClipTrimmed(String clipId, TimelineOps.Edge edge, long deltaMs) {
                engine.trimClip(clipId, edge, deltaMs);
            }

            @Override public void onScrubbed(long positionMs) {
                engine.setPlayhead(positionMs);
                preview.seek(positionMs);
                updateTimecode(positionMs);
            }

            @Override public void onSnapGuide(Long atMs) {
            }
        });

        engine.addListener(new TimelineEngine.Listener() {
            @Override public void onTimelineChanged(Timeline next) {
                timelineView.setTimeline(next);
                preview.setTimeline(next);
                updateDuration(next);
                persist();
            }

            @Override public void onSelectionChanged(String clipId) {
                timelineView.setSelectedClip(clipId);
            }
        });
    }

    private void wirePreview() {
        if (!NativeEngine.ensureLoaded()) {
            String why = NativeEngine.loadErrorMessage();
            Toast.makeText(this,
                    "Native engine not loaded — preview disabled."
                    + (why != null ? "\n" + why : ""),
                    Toast.LENGTH_LONG).show();
        }
        try {
            previewView.setCoordinator(preview.renderer());
        } catch (Throwable t) {
            android.util.Log.e("EditorActivity", "preview init failed", t);
        }
        previewView.setGestureListener(new PreviewView.GestureListener() {
            @Override public void onSingleTap() {
                if (transport.getAlpha() < 1f) ViewFx.showSoft(transport);
                else ViewFx.hideSoft(transport);
            }

            @Override public void onZoomChanged(float zoom, float focusX, float focusY) {
            }
        });
        preview.setHost(new PreviewController.Host() {
            @Override public void onPlaybackStateChanged(final boolean playing) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        playPause.setImageResource(playing
                                ? R.drawable.ic_pause : R.drawable.ic_play);
                    }
                });
            }

            @Override public void onPositionChanged(final long positionMs) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        timelineView.setPlayhead(positionMs, true);
                        updateTimecode(positionMs);
                    }
                });
            }

            @Override public void onPlaybackEnded() {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        playPause.setImageResource(R.drawable.ic_play);
                    }
                });
            }
        });
        preview.setTimeline(engine.timeline());
    }

    private void wireTransport() {
        playPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { preview.togglePlayPause(); }
        });
        findViewById(R.id.addClip).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ViewFx.pressFeedback(v);
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[] {"video/*", "audio/*", "image/*"});
                startActivityForResult(pick, REQ_PICK_MEDIA);
            }
        });
        findViewById(R.id.split).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ViewFx.pressFeedback(v);
                String sel = engine.selectedClipId();
                if (sel != null) engine.splitAtPlayhead(sel);
            }
        });
        findViewById(R.id.undo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { engine.undo(); }
        });
        findViewById(R.id.redo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { engine.redo(); }
        });
        findViewById(R.id.exportButton).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startExport(); }
        });
        findViewById(R.id.backButton).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_MEDIA && resultCode == RESULT_OK && data != null) {
            onMediaPicked(data.getData());
        }
    }

    private void onMediaPicked(final Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final MediaImporter.Result imported = MediaImporter.importUri(
                            EditorActivity.this, uri);
                    final MediaProbe.Info info = MediaProbe.inspect(imported.path);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            addImportedClip(imported, info);
                        }
                    });
                } catch (java.io.IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(EditorActivity.this,
                                    R.string.import_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void addImportedClip(MediaImporter.Result imported, MediaProbe.Info info) {
        long startAt = engine.playheadMs();
        Timeline timeline = engine.timeline();
        if (imported.isVideo) {
            Track track = firstOrNewTrack(timeline, Track.Kind.VIDEO);
            long duration = info.durationMs > 0 ? info.durationMs : 5000;
            VideoClip clip = new VideoClip(
                    new ClipTiming(startAt, duration, 0, 1f),
                    Transform.identity(), Collections.<com.videomontage.editor.model.Effect>emptyList(), 1f,
                    imported.displayName, imported.path, info.durationMs, info.hasAudio);
            ensureTrack(timeline, track);
            engine.insertClip(track.id, clip);
            captureThumbnail(imported.path);
        } else if (imported.isImage) {
            Track track = firstOrNewTrack(timeline, Track.Kind.VIDEO);
            ImageClip clip = new ImageClip(
                    new ClipTiming(startAt, 3000, 0, 1f),
                    Transform.identity(), Collections.<com.videomontage.editor.model.Effect>emptyList(),
                    imported.displayName, imported.path);
            ensureTrack(timeline, track);
            engine.insertClip(track.id, clip);
        } else {
            Track track = firstOrNewTrack(timeline, Track.Kind.AUDIO);
            long duration = Math.max(info.durationMs, 1000);
            float[] peaks = WaveformExtractor.extract(imported.path);
            AudioClip clip = new AudioClip(
                    new ClipTiming(startAt, duration, 0, 1f), 1f,
                    imported.displayName, imported.path, peaks, 0, 0);
            ensureTrack(timeline, track);
            engine.insertClip(track.id, clip);
        }
        engine.select(null);
        preview.seek(startAt);
        engine.setPlayhead(startAt);
        updateTimecode(startAt);
    }

    private Track firstOrNewTrack(Timeline timeline, Track.Kind kind) {
        for (Track t : timeline.tracks) if (t.kind == kind) return t;
        return new Track(kind);
    }

    private void ensureTrack(Timeline timeline, Track track) {
        if (timeline.track(track.id) == null) {
            engine.addTrack(track);
        }
    }

    private void captureThumbnail(final String path) {
        io.execute(new Runnable() {
            @Override public void run() {
                Bitmap bmp = MediaProbe.thumbnail(path, 0, 480);
                if (bmp == null) return;
                try {
                    File out = new File(StoragePaths.thumbnailsDir(EditorActivity.this),
                            project.id + ".jpg");
                    FileOutputStream fos = new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                    fos.close();
                    project = project.withThumbnail(out.getAbsolutePath());
                    repository.save(project);
                } catch (java.io.IOException ignored) {
                }
            }
        });
    }

    private void persist() {
        final Project snapshot = project.touched(engine.timeline());
        project = snapshot;
        io.execute(new Runnable() {
            @Override public void run() { repository.save(snapshot); }
        });
    }

    private void startExport() {
        final File out = new File(StoragePaths.exportsDir(this),
                project.name.replaceAll("\\s+", "_") + ".mp4");
        Toast.makeText(this, R.string.export_started, Toast.LENGTH_SHORT).show();
        exportManager.export(engine.timeline(), out.getAbsolutePath(),
                new ExportManager.Callback() {
            @Override public void onProgress(float fraction) { }

            @Override public void onFinished(final String outputPath) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(EditorActivity.this,
                                getString(R.string.export_done, outputPath),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override public void onFailed(final String reason) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(EditorActivity.this, reason, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void updateTimecode(long ms) {
        timecode.setText(Timecode.format(ms, engine.timeline().frameRate));
    }

    private void updateDuration(Timeline timeline) {
        durationLabel.setText(Timecode.format(timeline.durationMs(), timeline.frameRate));
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.playback().pause();
    }

    @Override
    protected void onDestroy() {
        preview.release();
        io.shutdownNow();
        super.onDestroy();
    }
}
