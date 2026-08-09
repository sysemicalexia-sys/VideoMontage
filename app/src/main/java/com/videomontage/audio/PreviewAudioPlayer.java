package com.videomontage.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.videomontage.editor.model.AudioClip;
import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.model.VideoClip;
import com.videomontage.timeline.PlaybackClock;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class PreviewAudioPlayer {

    private static final int SAMPLE_RATE = 44100;

    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;
    private volatile long playheadMs;
    private PlaybackClock clock;
    private volatile Timeline timeline = Timeline.empty();

    public void setTimeline(Timeline timeline) { this.timeline = timeline; }
    public void setClock(PlaybackClock clock) { this.clock = clock; }
    public void setPlayhead(long ms) { this.playheadMs = ms; }

    private long playhead() {
        PlaybackClock c = clock;
        return c != null ? c.positionMs() : playheadMs;
    }

    public synchronized void start() {
        if (running) return;
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, SAMPLE_RATE / 5 * 4), AudioTrack.MODE_STREAM);
        track.play();
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { pumpLoop(); }
        }, "audio-pump");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
        if (track != null) {
            try { track.pause(); track.flush(); track.release(); } catch (RuntimeException ignored) {}
            track = null;
        }
    }

    private Clip activeClipAt(long t) {
        Timeline tl = timeline;
        for (Track tr : tl.tracks) {
            if (tr.muted || tr.kind != Track.Kind.AUDIO) continue;
            for (Clip c : tr.clips)
                if (t >= c.timing.positionMs && t < c.timing.endMs()) return c;
        }
        for (Track tr : tl.tracks) {
            if (tr.muted) continue;
            for (Clip c : tr.clips) {
                if (c instanceof VideoClip && ((VideoClip) c).hasEmbeddedAudio
                        && t >= c.timing.positionMs && t < c.timing.endMs()) return c;
            }
        }
        return null;
    }

    private static String srcOf(Clip c) {
        if (c instanceof AudioClip) return ((AudioClip) c).sourcePath;
        if (c instanceof VideoClip) return ((VideoClip) c).sourcePath;
        return null;
    }

    private void pumpLoop() {
        MediaExtractor ex = null;
        MediaCodec codec = null;
        String openId = null;

        while (running) {
            long t = playhead();
            Clip clip = activeClipAt(t);
            String id = clip == null ? null : clip.id;

            if (id == null ? openId != null : !id.equals(openId)) {
                teardown(ex, codec);
                ex = null; codec = null; openId = null;
                if (clip != null) {
                    Object[] opened = open(clip, t);
                    if (opened != null) { ex = (MediaExtractor) opened[0]; codec = (MediaCodec) opened[1]; openId = id; }
                    else openId = id;
                }
            }

            if (codec == null) {
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
                continue;
            }

            if (!pumpOne(clip, ex, codec)) {
                teardown(ex, codec);
                ex = null; codec = null; openId = null;
            }
        }
        teardown(ex, codec);
    }

    private Object[] open(Clip clip, long t) {
        try {
            MediaExtractor ex = new MediaExtractor();
            ex.setDataSource(srcOf(clip));
            int idx = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { idx = i; break; }
            }
            if (idx < 0) { ex.release(); return null; }
            ex.selectTrack(idx);
            MediaFormat fmt = ex.getTrackFormat(idx);
            MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            ex.seekTo(clip.timing.sourceTimeAt(t) * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            return new Object[] { ex, codec };
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private boolean pumpOne(Clip clip, MediaExtractor ex, MediaCodec codec) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            int inIdx = codec.dequeueInputBuffer(10_000);
            if (inIdx >= 0) {
                ByteBuffer buf = codec.getInputBuffer(inIdx);
                int size = ex.readSampleData(buf, 0);
                if (size < 0) {
                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    codec.queueInputBuffer(inIdx, 0, size, ex.getSampleTime(), 0);
                    ex.advance();
                }
            }
            int outIdx = codec.dequeueOutputBuffer(info, 10_000);
            if (outIdx >= 0) {
                ByteBuffer pcm = codec.getOutputBuffer(outIdx);
                if (pcm != null && info.size > 0 && track != null
                        && (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                    byte[] chunk = new byte[info.size];
                    pcm.get(chunk);
                    applyVolume(chunk, clip.volume);
                    track.write(chunk, 0, chunk.length);
                }
                codec.releaseOutputBuffer(outIdx, false);
                return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void teardown(MediaExtractor ex, MediaCodec codec) {
        if (codec != null) {
            try { codec.stop(); } catch (RuntimeException ignored) {}
            codec.release();
        }
        if (ex != null) ex.release();
    }

    private static void applyVolume(byte[] pcm, float volume) {
        if (volume == 1f) return;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            short s = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff));
            s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s * volume));
            pcm[i] = (byte) (s & 0xff);
            pcm[i + 1] = (byte) ((s >> 8) & 0xff);
        }
    }
}
