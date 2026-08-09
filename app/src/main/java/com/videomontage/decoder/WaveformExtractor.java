package com.videomontage.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/** Decodes an audio track once at import time and reduces it to normalized
 *  peak buckets for the timeline lane. Runs on a background thread; the
 *  result is immutable and cached with the clip. */
public final class WaveformExtractor {

    private static final int BUCKETS_PER_SECOND = 100;
    private static final long READ_TIMEOUT_US = 10_000;

    private WaveformExtractor() {}

    /** Returns ~BUCKETS_PER_SECOND peaks per second of audio, or an empty
     *  array when the source has no decodable audio. */
    public static float[] extract(String path) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(path);
            int track = findAudioTrack(extractor);
            if (track < 0) return new float[0];
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            long durationUs = format.getLong(MediaFormat.KEY_DURATION);
            int bucketCount = Math.max(1,
                    (int) (durationUs / 1_000_000f * BUCKETS_PER_SECOND));
            float[] peaks = new float[bucketCount];

            codec = MediaCodec.createDecoderByType(
                    format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEos = false;
            while (true) {
                if (!inputEos) {
                    int inIdx = codec.dequeueInputBuffer(READ_TIMEOUT_US);
                    if (inIdx >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(inIdx);
                        int size = extractor.readSampleData(buf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, READ_TIMEOUT_US);
                if (outIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    ByteBuffer pcm = codec.getOutputBuffer(outIdx);
                    if (pcm != null && info.size > 0) {
                        foldIntoPeaks(pcm, info.presentationTimeUs, durationUs, peaks);
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                    break;
                }
            }
            normalize(peaks);
            return peaks;
        } catch (IOException | RuntimeException e) {
            return new float[0];
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignored) {}
                codec.release();
            }
            extractor.release();
        }
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private static void foldIntoPeaks(ByteBuffer pcm, long ptsUs, long durationUs,
                                      float[] peaks) {
        ShortBuffer shorts = pcm.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int bucketLength = Math.max(1, shorts.remaining() / 4);
        int baseBucket = (int) (ptsUs / (double) durationUs * peaks.length);
        for (int i = 0; i < shorts.remaining(); i++) {
            float v = Math.abs(shorts.get(i) / 32768f);
            int bucket = Math.min(peaks.length - 1, baseBucket + i / bucketLength / 4);
            if (v > peaks[bucket]) peaks[bucket] = v;
        }
    }

    private static void normalize(float[] peaks) {
        float max = 0f;
        for (float p : peaks) max = Math.max(max, p);
        if (max <= 0f) return;
        for (int i = 0; i < peaks.length; i++) peaks[i] /= max;
    }
}
