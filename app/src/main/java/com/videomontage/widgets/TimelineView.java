package com.videomontage.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import com.videomontage.core.Timecode;
import com.videomontage.editor.model.AudioClip;
import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.ops.TimelineOps;
import com.videomontage.theme.Colors;
import com.videomontage.utils.Spring;

/** The timeline. Custom-drawn end to end: ruler, lanes, clips, waveforms,
 *  trim handles, playhead. Zoom and playhead ride springs, scroll rides an
 *  OverScroller — the three motion systems never fight because each owns
 *  exactly one property. */
public final class TimelineView extends View {

    public interface Listener {
        void onClipSelected(String clipId);
        void onClipMoved(String clipId, long newPositionMs);
        void onClipTrimmed(String clipId, TimelineOps.Edge edge, long deltaMs);
        void onScrubbed(long positionMs);
        /** Fired while a snap guide should be visible, null when cleared. */
        void onSnapGuide(Long atMs);
    }

    private static final float MIN_PX_PER_MS = 0.02f;
    private static final float MAX_PX_PER_MS = 1.2f;
    private static final long SNAP_THRESHOLD_PX = 40;

    private final float dp = getResources().getDisplayMetrics().density;
    private final float rulerH = 28 * dp;
    private final float laneH = 54 * dp;
    private final float laneGap = 6 * dp;
    private final float clipRadius = 8 * dp;
    private final float handleW = 14 * dp;

    // Motion systems — one owner per property.
    private final Spring pxPerMs = new Spring(0.12f, 240f, 26f);
    private final Spring playheadX = new Spring(0f, 380f, 34f);
    private final OverScroller scroller;

    private Timeline timeline = Timeline.empty();
    private Listener listener;
    private String selectedClipId;
    private long playheadMs;
    private float contentOffsetPx = 12 * dp;

    // Drag state machine.
    private enum Drag { NONE, SCROLL, MOVE_CLIP, TRIM_START, TRIM_END, SCRUB }
    private Drag drag = Drag.NONE;
    private Clip dragClip;
    private float dragStartX;
    private long dragClipStartPosMs;
    private float lastTouchX;

    // Pre-allocated drawing state — onDraw never allocates.
    private final Paint lanePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final Path wavePath = new Path();
    private Long snapGuideMs;

    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleGestures;
    private boolean needsSpringFrame;

    public TimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, gestureListener);
        scaleGestures = new ScaleGestureDetector(context, scaleListener);

        lanePaint.setColor(Colors.GRAPHITE);
        clipSelectedPaint.setColor(Colors.ACCENT);
        clipSelectedPaint.setStyle(Paint.Style.STROKE);
        clipSelectedPaint.setStrokeWidth(2 * dp);
        textPaint.setColor(Colors.TEXT_PRIMARY);
        textPaint.setTextSize(11 * dp);
        rulerPaint.setColor(Colors.TEXT_TERTIARY);
        rulerPaint.setTextSize(9.5f * dp);
        rulerPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        tickPaint.setColor(Colors.HAIRLINE);
        playheadPaint.setColor(Colors.ACCENT);
        playheadPaint.setStrokeWidth(2 * dp);
        handlePaint.setColor(Colors.TEXT_PRIMARY);
        wavePaint.setColor(0x66FFFFFF);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(1 * dp);
        guidePaint.setColor(Colors.VIOLET);
        guidePaint.setStrokeWidth(1.5f * dp);
        setWillNotDraw(false);
    }

    public void setListener(Listener l) { listener = l; }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
        invalidate();
    }

    public void setSelectedClip(String clipId) {
        selectedClipId = clipId;
        invalidate();
    }

    /** External position updates (during playback) go through the spring so
     *  the playhead glides instead of stepping. */
    public void setPlayhead(long ms, boolean animate) {
        playheadMs = ms;
        float x = msToX(ms);
        if (animate) playheadX.setTarget(x);
        else playheadX.snapTo(x);
        kickSpringFrames();
        invalidate();
    }

    public long playheadMs() { return playheadMs; }

    // ---- coordinate mapping -------------------------------------------------

    private float msToX(long ms) {
        return contentOffsetPx + ms * pxPerMs.value() - scrollX();
    }

    private long xToMs(float x) {
        return Math.max(0, (long) ((x + scrollX() - contentOffsetPx) / pxPerMs.value()));
    }

    private int scrollX() {
        return scroller.getCurrX();
    }

    private float laneTop(int lane) {
        return rulerH + lane * (laneH + laneGap);
    }

    private int laneCount() {
        return Math.max(2, timeline.tracks.size());
    }

    // ---- input ---------------------------------------------------------------

    private final ScaleGestureDetector.OnScaleGestureListener scaleListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override public boolean onScale(ScaleGestureDetector detector) {
            float current = pxPerMs.value();
            float next = Math.max(MIN_PX_PER_MS, Math.min(MAX_PX_PER_MS,
                    current * detector.getScaleFactor()));
            // Zoom around the gesture focus: keep the ms under the finger fixed.
            long focusMs = xToMs(detector.getFocusX());
            pxPerMs.snapTo(next);
            int targetScroll = (int) (focusMs * next + contentOffsetPx - detector.getFocusX());
            scroller.startScroll(scrollX(), 0, targetScroll - scrollX(), 0, 0);
            playheadX.snapTo(msToX(playheadMs));
            invalidate();
            return true;
        }
    };

    private final GestureDetector.OnGestureListener gestureListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            if (drag == Drag.SCROLL) {
                scroller.startScroll(scrollX(), 0, (int) dx, 0, 0);
                playheadX.snapTo(msToX(playheadMs));
                invalidate();
            }
            return true;
        }

        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (drag == Drag.SCROLL) {
                scroller.fling(scrollX(), 0, (int) -vx, 0, 0, maxScrollX(), 0, 0);
                kickSpringFrames();
            }
            return true;
        }
    };

    private int maxScrollX() {
        long duration = Math.max(timeline.durationMs() + 5000, 30_000);
        return (int) (duration * pxPerMs.value() + contentOffsetPx * 2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestures.onTouchEvent(event);
        if (!scaleGestures.isInProgress()) gestures.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                dragStartX = event.getX();
                scroller.forceFinished(true);
                drag = hitTest(event.getX(), event.getY());
                if (drag == Drag.MOVE_CLIP && dragClip != null) {
                    dragClipStartPosMs = dragClip.timing.positionMs;
                    if (listener != null) listener.onClipSelected(dragClip.id);
                } else if (drag == Drag.SCRUB) {
                    scrubTo(event.getX());
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                lastTouchX = event.getX();
                switch (drag) {
                    case SCRUB:
                        scrubTo(event.getX());
                        break;
                    case MOVE_CLIP:
                        if (dragClip != null && listener != null) {
                            long deltaMs = (long) ((event.getX() - dragStartX) / pxPerMs.value());
                            long target = Math.max(0, dragClipStartPosMs + deltaMs);
                            Long snapped = snapCandidate(target);
                            listener.onClipMoved(dragClip.id,
                                    snapped != null ? snapped : target);
                        }
                        break;
                    case TRIM_START:
                    case TRIM_END:
                        if (dragClip != null && listener != null) {
                            long deltaMs = (long) (dx / pxPerMs.value());
                            listener.onClipTrimmed(dragClip.id,
                                    drag == Drag.TRIM_START
                                            ? TimelineOps.Edge.START : TimelineOps.Edge.END,
                                    deltaMs);
                        }
                        break;
                    default:
                        if (Math.abs(event.getX() - dragStartX) > 6 * dp) drag = Drag.SCROLL;
                        break;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag == Drag.SCROLL && dragClip == null
                        && Math.abs(event.getX() - dragStartX) < 6 * dp) {
                    scrubTo(event.getX()); // tap on empty space seeks
                }
                drag = Drag.NONE;
                dragClip = null;
                if (listener != null) listener.onSnapGuide(null);
                snapGuideMs = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void scrubTo(float x) {
        long ms = xToMs(x);
        playheadMs = ms;
        playheadX.setTarget(msToX(ms));
        kickSpringFrames();
        if (listener != null) listener.onScrubbed(ms);
        invalidate();
    }

    /** Nearest snap point within threshold, or null. */
    private Long snapCandidate(long candidateMs) {
        float thresholdMs = SNAP_THRESHOLD_PX / pxPerMs.value();
        long best = Long.MAX_VALUE;
        long nearest = candidateMs;
        if (Math.abs(candidateMs - playheadMs) < best) { best = Math.abs(candidateMs - playheadMs); nearest = playheadMs; }
        if (Math.abs(candidateMs) < best) { best = Math.abs(candidateMs); nearest = 0; }
        for (Track t : timeline.tracks) {
            for (Clip c : t.clips) {
                long d1 = Math.abs(candidateMs - c.timing.positionMs);
                if (d1 < best) { best = d1; nearest = c.timing.positionMs; }
                long d2 = Math.abs(candidateMs - c.timing.endMs());
                if (d2 < best) { best = d2; nearest = c.timing.endMs(); }
            }
        }
        if (best <= thresholdMs) {
            snapGuideMs = nearest;
            if (listener != null) listener.onSnapGuide(nearest);
            return nearest;
        }
        snapGuideMs = null;
        if (listener != null) listener.onSnapGuide(null);
        return null;
    }

    private Drag hitTest(float x, float y) {
        dragClip = null;
        if (y < rulerH) return Drag.SCRUB;
        for (int lane = 0; lane < timeline.tracks.size(); lane++) {
            float top = laneTop(lane);
            if (y < top || y > top + laneH) continue;
            Track track = timeline.tracks.get(lane);
            for (Clip c : track.clips) {
                float left = msToX(c.timing.positionMs);
                float right = msToX(c.timing.endMs());
                if (x < left || x > right) continue;
                dragClip = c;
                boolean selected = c.id.equals(selectedClipId);
                if (selected && x - left < handleW * 1.6f) return Drag.TRIM_START;
                if (selected && right - x < handleW * 1.6f) return Drag.TRIM_END;
                return Drag.MOVE_CLIP;
            }
            return Drag.SCROLL;
        }
        dragClip = null;
        return Drag.SCROLL;
    }

    // ---- drawing -------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Colors.INK);
        drawRuler(canvas);
        drawLanes(canvas);
        drawPlayhead(canvas);
        if (snapGuideMs != null) {
            float gx = msToX(snapGuideMs);
            canvas.drawLine(gx, 0, gx, getHeight(), guidePaint);
        }
    }

    private void drawRuler(Canvas canvas) {
        float ppm = pxPerMs.value();
        // Adaptive tick spacing: smallest "nice" interval >= 70 px.
        long[] nice = {100, 250, 500, 1000, 2000, 5000, 10000, 30000, 60000};
        long interval = nice[nice.length - 1];
        for (long n : nice) {
            if (n * ppm >= 70 * dp / dp) { interval = n; break; }
        }
        long firstMs = Math.max(0, xToMs(0) / interval * interval);
        long lastMs = xToMs(getWidth()) + interval;
        for (long t = firstMs; t <= lastMs; t += interval) {
            float x = msToX(t);
            canvas.drawLine(x, rulerH - 6 * dp, x, rulerH, tickPaint);
            canvas.drawText(Timecode.format(t, timeline.frameRate),
                    x + 4 * dp, rulerH - 9 * dp, rulerPaint);
        }
        canvas.drawLine(0, rulerH - 0.5f * dp, getWidth(), rulerH - 0.5f * dp, tickPaint);
    }

    private void drawLanes(Canvas canvas) {
        for (int lane = 0; lane < laneCount(); lane++) {
            float top = laneTop(lane);
            rect.set(0, top, getWidth(), top + laneH);
            canvas.drawRoundRect(rect, clipRadius, clipRadius, lanePaint);
            if (lane >= timeline.tracks.size()) continue;
            Track track = timeline.tracks.get(lane);
            int baseColor = Colors.forTrack(track.kind);
            for (Clip c : track.clips) drawClip(canvas, c, top, baseColor);
        }
    }

    private void drawClip(Canvas canvas, Clip clip, float top, int baseColor) {
        float left = msToX(clip.timing.positionMs);
        float right = msToX(clip.timing.endMs());
        if (right < 0 || left > getWidth()) return; // viewport cull

        rect.set(left + 1 * dp, top + 2 * dp, right - 1 * dp, top + laneH - 2 * dp);
        boolean selected = clip.id.equals(selectedClipId);

        // One small shader per visible clip — the depth cue is worth it,
        // and culled clips never reach this line.
        clipPaint.setShader(new LinearGradient(left, top, left, top + laneH,
                lighten(baseColor, selected ? 0.28f : 0.12f),
                darken(baseColor, 0.18f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, clipRadius, clipRadius, clipPaint);
        clipPaint.setShader(null);

        if (clip instanceof AudioClip) drawWaveform(canvas, (AudioClip) clip, rect);

        if (rect.width() > 36 * dp) {
            int save = canvas.save();
            canvas.clipRect(rect);
            canvas.drawText(clip.label, rect.left + 8 * dp,
                    rect.top + 15 * dp, textPaint);
            canvas.restoreToCount(save);
        }

        if (selected) {
            rect2.set(rect);
            canvas.drawRoundRect(rect2, clipRadius, clipRadius, clipSelectedPaint);
            drawHandle(canvas, rect.left, rect.top, rect.bottom, true);
            drawHandle(canvas, rect.right, rect.top, rect.bottom, false);
        }
    }

    private void drawHandle(Canvas canvas, float cx, float top, float bottom, boolean left) {
        rect.set(cx - handleW / 2, top + 3 * dp, cx + handleW / 2, bottom - 3 * dp);
        canvas.drawRoundRect(rect, 4 * dp, 4 * dp, handlePaint);
        float gripCx = cx + (left ? 1.5f * dp : -1.5f * dp);
        wavePaint.setColor(0x99000000);
        canvas.drawLine(gripCx, top + (bottom - top) / 2 - 6 * dp,
                gripCx, top + (bottom - top) / 2 + 6 * dp, wavePaint);
        wavePaint.setColor(0x66FFFFFF);
    }

    private void drawWaveform(Canvas canvas, AudioClip clip, RectF bounds) {
        float[] peaks = clip.waveformPeaks;
        if (peaks == null || peaks.length == 0) return;
        wavePath.rewind();
        float midY = bounds.centerY();
        float halfH = bounds.height() / 2 - 8 * dp;
        int count = Math.max(2, (int) (bounds.width() / (3 * dp)));
        for (int i = 0; i < count; i++) {
            int peakIdx = (int) ((i / (float) count) * peaks.length);
            float amp = Math.max(0.06f, peaks[Math.min(peaks.length - 1, peakIdx)]) * halfH;
            float x = bounds.left + (i / (float) count) * bounds.width();
            wavePath.moveTo(x, midY - amp);
            wavePath.lineTo(x, midY + amp);
        }
        int save = canvas.save();
        canvas.clipRect(bounds);
        canvas.drawPath(wavePath, wavePaint);
        canvas.restoreToCount(save);
    }

    private void drawPlayhead(Canvas canvas) {
        float x = playheadX.value();
        canvas.drawLine(x, 0, x, getHeight(), playheadPaint);
        // Handle cap: a small diamond at the ruler.
        wavePath.rewind();
        wavePath.moveTo(x, rulerH);
        wavePath.lineTo(x - 6 * dp, rulerH - 10 * dp);
        wavePath.lineTo(x + 6 * dp, rulerH - 10 * dp);
        wavePath.close();
        handlePaint.setColor(Colors.ACCENT);
        canvas.drawPath(wavePath, handlePaint);
        handlePaint.setColor(Colors.TEXT_PRIMARY);
    }

    private static int lighten(int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + amount);
        return Color.HSVToColor(hsv);
    }

    private static int darken(int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] - amount);
        return Color.HSVToColor(hsv);
    }

    // ---- spring + scroller frames ---------------------------------------------

    private void kickSpringFrames() {
        if (needsSpringFrame) return;
        needsSpringFrame = true;
        postOnAnimation(stepper);
    }

    private long lastFrameUptimeMs;
    private final Runnable stepper = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastFrameUptimeMs) / 1000f);
            lastFrameUptimeMs = now;

            boolean active = false;
            if (!pxPerMs.isAtRest()) { pxPerMs.step(dt); active = true; }
            if (!playheadX.isAtRest()) { playheadX.step(dt); active = true; }
            if (scroller.computeScrollOffset()) active = true;

            if (active) {
                invalidate();
                postOnAnimation(this);
            } else {
                needsSpringFrame = false;
            }
        }
    };

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (rulerH + laneCount() * (laneH + laneGap) + laneGap);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }
}
