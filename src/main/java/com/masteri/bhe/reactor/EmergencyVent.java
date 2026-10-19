package com.masteri.bhe.reactor;

/**
 * The emergency vent's timeline (venting out of meltdown phase 1). The lance fires; a standoff with the swollen
 * black hole until {@link #SURGE}; then the drain, the hole rebounding every {@link #BEAT}, until it implodes at
 * {@link #IMPLODE}. Fractions of {@link #TICKS}: the renderer's animation, the camera shake and the server's sounds
 * all follow the same numbers.
 */
public final class EmergencyVent {
    public static final int TICKS = 240;
    /** The lance beam fires a little less long than the animation runs (the implosion's blast rolls on). */
    public static final int BEAM_TICKS = 220;
    public static final float SURGE = 0.28F, IMPLODE = 0.84F, BEAT = 0.07F;
    /** The drain's rebounds start a little after the surge and stop a little before the implosion. */
    public static final float FIRST_BEAT = SURGE + 0.02F, LAST_BEAT = IMPLODE - 0.02F;

    /** Which rebound {@code p} is in (-1 outside the drain). */
    public static int beatIndex(float p) {
        return p < FIRST_BEAT || p >= LAST_BEAT ? -1 : (int) ((p - FIRST_BEAT) / BEAT);
    }

    /** 1 right at a rebound, fading fast until the next. */
    public static float beat(float p) {
        if (beatIndex(p) < 0) return 0;
        return (float) Math.exp(-((p - FIRST_BEAT) % BEAT) / BEAT * 6);
    }

    private EmergencyVent() {}
}
