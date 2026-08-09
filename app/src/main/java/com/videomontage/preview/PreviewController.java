package com.videomontage.preview;

import com.videomontage.editor.model.Timeline;
import com.videomontage.playback.PlaybackController;
import com.videomontage.render.RenderCoordinator;

/** Glues playback to rendering for the editor screen. The preview view
 *  owns the surface; this owns what to show on it and when. */
public final class PreviewController {

    public interface Host {
        void onPlaybackStateChanged(boolean playing);
        void onPositionChanged(long positionMs);
        void onPlaybackEnded();
    }

    private final PlaybackController playback = new PlaybackController();
    private final RenderCoordinator renderer = new RenderCoordinator();
    private Host host;

    public PreviewController() {
        renderer.setClock(playback.clock());
        playback.clock().setListener(new com.videomontage.timeline.PlaybackClock.Listener() {
            @Override public void onPositionChanged(long positionMs) {
                if (host != null) host.onPositionChanged(positionMs);
            }

            @Override public void onPlaybackEnded() {
                if (host != null) {
                    host.onPlaybackStateChanged(false);
                    host.onPlaybackEnded();
                }
            }
        });
    }

    public void setHost(Host host) { this.host = host; }

    public PlaybackController playback() { return playback; }
    public RenderCoordinator renderer() { return renderer; }

    public void setTimeline(Timeline timeline) {
        renderer.setTimeline(timeline);
    }

    public void togglePlayPause() {
        playback.toggle();
        if (host != null) host.onPlaybackStateChanged(playback.clock().isPlaying());
    }

    public void seek(long tMs) {
        playback.seek(tMs);
        renderer.renderSeek(tMs);
    }

    public void release() {
        playback.release();
        renderer.shutdown();
    }
}
