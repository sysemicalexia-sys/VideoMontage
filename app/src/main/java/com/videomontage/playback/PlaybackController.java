package com.videomontage.playback;

import com.videomontage.audio.PreviewAudioPlayer;
import com.videomontage.editor.model.Timeline;
import com.videomontage.timeline.PlaybackClock;

/** Couples the clock to audio: play/pause/seek stay in lockstep, and the
 *  clock remains the single source of truth for position. */
public final class PlaybackController {

    private final PlaybackClock clock = new PlaybackClock();
    private final PreviewAudioPlayer audio = new PreviewAudioPlayer();

    public PlaybackController() {
        audio.setClock(clock);
    }

    public PlaybackClock clock() { return clock; }

    public void setTimeline(Timeline timeline) { audio.setTimeline(timeline); }

    public void play() {
        clock.play();
        audio.setPlayhead(clock.positionMs());
        audio.start();
    }

    public void pause() {
        clock.pause();
        audio.stop();
    }

    public void toggle() {
        if (clock.isPlaying()) pause(); else play();
    }

    public void seek(long tMs) {
        boolean wasPlaying = clock.isPlaying();
        if (wasPlaying) audio.stop();
        clock.seek(tMs);
        audio.setPlayhead(tMs);
        if (wasPlaying) audio.start();
    }

    public void release() {
        audio.stop();
    }
}
