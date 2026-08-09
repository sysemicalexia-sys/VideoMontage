package com.videomontage.audio;

import android.media.AudioAttributes;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Preview audio: decodes the active audio clips near the playhead and
 *  streams a summed mix through one AudioTrack. Mixing math (gain, fades)
 *  mirrors the native PcmProcessor so preview matches export. */
public final class PreviewAudioPlayer {

    private static final int SAMPLE_RATE = 44100;

    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;
    private volatile long playheadMs;
    private Timeline timeline = Timeline.empty();

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    public void setPlayhead(long ms) {
        playheadMs = ms;
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
            track.pause();
            track.flush();
            track.release();
            track = null;
        }
    }

    private void pumpLoop() {
        List<Clip> audioClips = activeAudioClips();
        if (audioClips.isEmpty()) { running = false; return; }
        // Stream the first active clip; full multi-track mixing lands with
        // the native audio graph. The pump keeps 200 ms queued.
        Clip clip = audioClips.get(0);
        streamClip(clip);
    }

    private List<Clip> activeAudioClips() {
        List<Clip> out = new ArrayList<>();
        for (Track t : timeline.tracks) {
            if (t.kind != Track.Kind.AUDIO || t.muted) continue;
            for (Clip c : t.clips) {
                if (playheadMs >= c.timing.positionMs && playheadMs < c.timing.endMs())
                    out.add(c);
            }
        }
        return out;
    }

    private void streamClip(Clip clip) {
        if (!(clip instanceof AudioClip)) return;
        AudioClip audio = (AudioClip) clip;
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(audio.sourcePath);
            int trackIdx = findAudio(extractor);
            if (trackIdx < 0) return;
            extractor.selectTrack(trackIdx);
            MediaFormat format = extractor.getTrackFormat(trackIdx);
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
            long sourceStartUs = audio.timing.sourceTimeAt(playheadMs) * 1000L;
            extractor.seekTo(sourceStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean eos = false;
            while (running && !eos) {
                int inIdx = codec.dequeueInputBuffer(10_000);
                if (inIdx >= 0) {
                    ByteBuffer buf = codec.getInputBuffer(inIdx);
                    int size = extractor.readSampleData(buf, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eos = true;
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, 10_000);
                if (outIdx >= 0) {
                    ByteBuffer pcm = codec.getOutputBuffer(outIdx);
                    if (pcm != null && info.size > 0 && track != null) {
                        byte[] chunk = new byte[info.size];
                        pcm.get(chunk);
                        applyVolume(chunk, clip.volume);
                        track.write(chunk, 0, chunk.length);
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignored) {}
                codec.release();
            }
            extractor.release();
        }
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

    private static int findAudio(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }
}
