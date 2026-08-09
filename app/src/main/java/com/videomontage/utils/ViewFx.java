package com.videomontage.utils;

import android.view.View;
import android.view.animation.OvershootInterpolator;

/** The app's shared micro-motion vocabulary. Every interactive element
 *  uses these, so nothing ever pops in without motion. */
public final class ViewFx {

    private ViewFx() {}

    public static final int ENTER_MS = 280;
    public static final int EXIT_MS = 180;

    public static void pressFeedback(final View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(new Runnable() {
            @Override public void run() {
                v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new OvershootInterpolator(2.2f)).start();
            }
        }).start();
    }

    public static void fadeSlideIn(final View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(v.getResources().getDisplayMetrics().density * 18);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs).setDuration(ENTER_MS).start();
    }

    public static void showSoft(View v) {
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1f) return;
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(ENTER_MS).start();
    }

    public static void hideSoft(final View v) {
        v.animate().alpha(0f).setDuration(EXIT_MS).withEndAction(new Runnable() {
            @Override public void run() { v.setVisibility(View.GONE); }
        }).start();
    }
}
