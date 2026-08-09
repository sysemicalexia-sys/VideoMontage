package com.videomontage.utils;

/** Critically-damped spring integrator for view properties the framework
 *  animators don't reach (playhead, zoom, custom view values). Step it
 *  from a vsync callback; it never overshoots and always settles. */
public final class Spring {

    private float value;
    private float target;
    private float velocity;
    private final float stiffness;
    private final float damping;

    public Spring(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public static Spring standard(float initial) {
        return new Spring(initial, 320f, 30f);
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    public float value() { return value; }

    public float target() { return target; }

    public boolean isAtRest() {
        return Math.abs(velocity) < 0.01f && Math.abs(target - value) < 0.01f;
    }

    /** Advances by dt seconds. Returns the new value. */
    public float step(float dt) {
        if (isAtRest()) return target;
        float force = -stiffness * (value - target) - damping * velocity;
        velocity += force * dt;
        value += velocity * dt;
        return value;
    }
}
