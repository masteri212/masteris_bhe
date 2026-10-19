package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.EmergencyVent;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.reactor.VentingLance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The floating black hole over the Reactor Core and the beams from the four stabilizer emitters.
 *
 * Event horizon: a black sphere about 3 blocks across at the ignition minimum, growing with mass and in meltdowns.
 * Photon ring: a glowing ring around the horizon that always faces the camera (the lensed back of the disc).
 * Accretion disc: flat, filling the ring's hole from the horizon out to the ring; spins with the reactor's rpm.
 * Stabilizer beams: from each emitter to the core while the reactor runs; the stabilizer offset pushes them sideways.
 *
 * Failures: the disc shudders and a green jump-start window shows during a stall, the ring's inner wall glows with
 * stress, starved beams sputter, pre-meltdown throbs like a heartbeat, dark-energy arcs strike the terrain, phase 1
 * swells the black hole and breaks up the beams, and phase 2 swallows the disc before the core implodes.
 */
public class ReactorCoreRenderer implements BlockEntityRenderer<ReactorCoreBlockEntity> {
    /** The biggest the black hole may draw (meltdown phase 2), leaving a little room for the disc's edge. */
    private static final float MAX_HORIZON = (float) ReactorStructure.RING_INNER_RADIUS - 1.5F;
    private static final float DISC_GAP = 0.25F;     // dark gap between the horizon and the disc's hot inner edge
    private static final float DISC_OUTER = (float) ReactorStructure.RING_INNER_RADIUS - 0.3F; // just short of the ring
    private static final int DISC_SEGMENTS = 128, DISC_BANDS = 16, RING_SEGMENTS = 64;
    private static final float BEAM_START = ReactorStructure.EMITTER_DISTANCE - 0.5F; // emitter front face
    private static final float MAX_OFFSET_VISUAL = 15F;

    public ReactorCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ReactorCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        renderLance(core, pose, buffers, partialTick);
        ReactorState state = core.getState();
        ReactorCoreBlockEntity.Anim anim = core.getAnim();
        boolean poweringDown = anim == ReactorCoreBlockEntity.Anim.POWER_DOWN || anim == ReactorCoreBlockEntity.Anim.SCRAM_POWER_DOWN
                || anim == ReactorCoreBlockEntity.Anim.VENT || anim == ReactorCoreBlockEntity.Anim.VENT_EMERGENCY;
        // A broken structure stops counting as formed once the reactor is offline, but a shutdown or vent that's
        // still playing must finish (venting out of meltdown phase 1, the ring is usually half eaten).
        if (!core.isFormed() && !poweringDown) return;
        if (state == ReactorState.OFFLINE && !poweringDown) return; // no black hole until it's ignited
        float time = core.getClientTicks() + partialTick;
        float p = core.getAnimProgress(partialTick);
        float danger = core.getDangerProgress(partialTick);
        // While it powers down the state is already offline; keep the shutdown look until the animation ends.
        ReactorCoreBlockEntity.Fault fault = core.getFault();
        Look look = Look.of(state == ReactorState.OFFLINE ? ReactorState.SHUTTING_DOWN : state, time, core.isScrammed(), fault);

        // Animation: how much of each part is showing (1 = all of it).
        float hole = 1, holeScale = look.horizonScale, discReach = 1, discAlpha = 1, ringAlpha = 1;
        float beamAlpha = 1, beamWidth = 1, flash = 0, flashSize = 4;
        float[] beamReach = {1, 1, 1, 1};
        float[] flashColor = Look.WHITE_VIOLET;
        boolean beams = look.beams;
        float shockProgress = -1, shock2Progress = -1;
        float[] shockColor = Look.WHITE_VIOLET, shock2Color = Look.VIOLET;
        float shudder = 0, redBand = 0;
        float extraSpin = 0;                 // degrees added to the disc's turn (spin-up / spin-down swirl)
        float emitterFlare = 0;              // glow on the four emitter faces
        float[] emitterFlareColor = Look.LAVENDER;
        float headFlare = 0;                 // glow riding the tip of each growing beam
        float star = 0, starSize = 0;        // the white-hot singularity before it turns black
        float ventFlow = 0;                  // venting: the black hole's energy streaming up into the lance
        float blast = -1;                    // emergency vent: the implosion's blast ring, rolling out past the ring
        float[] starColor = Look.WHITE_VIOLET;

        // ---- steady per-state looks
        switch (state) {
            case DISC_STALL -> shudder = 0.08F;
            case IGNITION_FAULT -> shudder = 0.05F;
            case SHUTDOWN_FAULT -> {
                shudder = 0.05F;
                if (fault == ReactorCoreBlockEntity.Fault.SINGULARITY_REBOUND) {
                    // The black hole strains to close and keeps bouncing back open.
                    float beat = (float) Math.exp(-(time % 16F) / 16F * 5);
                    hole *= 1 + 0.12F * beat;
                    ringAlpha *= 1 + 1.5F * beat;
                    discReach = 0.35F;
                }
            }
            case PRE_MELTDOWN -> {
                // Heartbeat: the black hole throbs, faster and faster as the countdown runs out.
                float period = Mth.lerp(danger, 30F, 8F);
                float beat = (float) Math.exp(-(time % period) / period * 6);
                hole *= 1 + 0.06F * beat;
                discAlpha *= 0.85F + 0.3F * beat;
                redBand = 0.5F * beat;
                shudder = 0.03F + 0.08F * danger;
            }
            case MELTDOWN_1 -> {
                // The stabilizer beams are breaking up: each one snaps off partway, then reconnects.
                for (int i = 0; i < 4; i++) {
                    float n = noise(i, (int) (time / 6), 7);
                    if (n > 0.35F) beamReach[i] = 0.25F + 0.6F * noise(i, (int) (time / 6), 11);
                }
                redBand = 0.35F;
                shudder = 0.12F;
            }
            case MELTDOWN_2 -> {
                // The disc is swallowed as the black hole grows, then the core implodes before it goes.
                discReach = 1 - 0.5F * danger;
                float implode = phase(danger, 0.85F, 0.13F);
                hole *= 1 - 0.95F * easeIn(implode);
                ringAlpha *= 1 + 3 * implode;
                if (danger > 0.97F) {
                    flash = phase(danger, 0.97F, 0.03F);
                    flashSize = 10;
                }
                shudder = 0.2F;
            }
            default -> {}
        }
        if (core.isClientStarving()) {
            beamWidth *= 0.5F;
            beamAlpha *= 0.7F;
        }

        // ---- one-off animations
        switch (anim) {
            case STARTUP -> {
                // 1. Charge: the four emitters glow brighter and brighter while sparks stream in toward the core.
                emitterFlare = easeIn(phase(p, 0, 0.2F)) * (1 - phase(p, 0.35F, 0.15F));
                // 2. Fire: one after another the beams shoot in, a hot flare riding each tip.
                for (int i = 0; i < 4; i++) beamReach[i] = easeOut(phase(p, 0.18F + 0.04F * i, 0.16F));
                headFlare = phase(p, 0.18F, 0.05F) * (1 - phase(p, 0.36F, 0.06F));
                // 3. Ignite: where they meet a white star swells, then collapses into the black hole with a flash.
                star = phase(p, 0.34F, 0.04F) * (1 - phase(p, 0.44F, 0.06F));
                starSize = 0.4F + 2.6F * easeOut(phase(p, 0.34F, 0.1F));
                hole = easeOutBack(phase(p, 0.44F, 0.16F));
                ringAlpha = hole * (1 + 2 * (1 - phase(p, 0.5F, 0.2F)));  // the photon ring flares as it opens
                flash = spike(p, 0.45F, 0.08F) * 1.3F;
                flashSize = 7;
                shockProgress = phase(p, 0.45F, 0.35F);
                shock2Progress = phase(p, 0.55F, 0.4F);
                // 4. Spin-up: the disc spirals out from the black hole, swirling fast and settling to its real speed.
                discReach = easeOut(phase(p, 0.55F, 0.45F));
                discAlpha = discReach;
                extraSpin = (1 - easeOut(phase(p, 0.55F, 0.45F))) * 540;
            }
            case SCRAM -> {
                // The beams overload white-hot, then snap and whip back into the emitters, which flare red.
                // Two red shockwaves rip across the hole, arcs burst out (see the block entity) and the disc glitches.
                beams = p < 0.4F;
                beamWidth = p < 0.12F ? 2.6F : 1.2F;
                beamAlpha = p < 0.12F ? 1.6F : 1.0F;
                float snap = 1 - easeIn(phase(p, 0.12F, 0.25F));
                for (int i = 0; i < 4; i++) beamReach[i] = snap;
                emitterFlare = phase(p, 0.1F, 0.08F) * (1 - phase(p, 0.45F, 0.5F)) * 1.2F;
                emitterFlareColor = Look.RED;
                shockProgress = phase(p, 0.05F, 0.6F);
                shockColor = Look.RED;
                shock2Progress = phase(p, 0.3F, 0.6F);
                shock2Color = Look.MAGENTA;
                discAlpha = 0.45F + 0.55F * (noise(7, (int) (time * 2), 1) > 0.5F ? 1 : 0.3F);
                flash = Math.max(0, 1 - p / 0.25F) * 1.2F;
                flashColor = Look.RED;
                shudder += 0.2F * (1 - p);
            }
            case POWER_DOWN, SCRAM_POWER_DOWN -> {
                // 1. The beams pull back into the emitters, which glow as they power down.
                boolean scrammed = anim == ReactorCoreBlockEntity.Anim.SCRAM_POWER_DOWN;
                beams = !scrammed && p < 0.3F;
                for (int i = 0; i < 4; i++) beamReach[i] = 1 - easeIn(phase(p, 0.03F * i, 0.25F));
                emitterFlare = scrammed ? 0 : spike(p, 0.25F, 0.15F) * 0.8F;
                // 2. The disc spirals into the black hole, swirling faster and faster as it drains.
                discReach = 1 - easeIn(phase(p, 0.1F, 0.55F));
                discAlpha = 1 - phase(p, 0.3F, 0.4F);
                extraSpin = easeIn(phase(p, 0.1F, 0.6F)) * 900;
                // 3. The black hole shrinks to a point, its photon ring burning brighter as it goes...
                hole = 1 - easeIn(phase(p, 0.55F, 0.3F));
                ringAlpha = hole * (1 + 3 * phase(p, 0.55F, 0.3F));
                // 4. ...and pops: a small white star, a flash and one last shockwave.
                star = spike(p, 0.88F, 0.07F);
                starSize = 1.5F;
                flash = spike(p, 0.88F, 0.06F);
                flashSize = 5;
                shockProgress = phase(p, 0.88F, 0.12F);
            }
            case VENT -> {
                boolean emergency = core.isVentEmergency();
                float[] hot = emergency ? Look.RED : Look.WHITE_VIOLET;
                // 1. The lance fires: the stabilizer beams let go, a flash and a ring of light across the hole.
                beams = p < 0.15F;
                for (int i = 0; i < 4; i++) beamReach[i] = 1 - easeIn(phase(p, 0.02F * i, 0.12F));
                flash = spike(p, 0.03F, 0.06F) * 1.2F;
                flashSize = 6;
                flashColor = hot;
                shockProgress = phase(p, 0.02F, 0.45F);
                shockColor = hot;
                // 2. The black hole drains up the beam: it shrinks steadily (from its swollen size after a meltdown),
                //    trembling, its photon ring burning brighter the smaller it gets, while the disc spirals in.
                float drain = phase(p, 0.06F, 0.78F);
                if (emergency) holeScale = 1.5F;
                hole = Mth.lerp(easeInOut(drain), 1, 0.08F) * (1 + 0.05F * (1 - drain) * Mth.sin(time * 2.3F));
                ringAlpha = 1 + 3 * drain;
                discReach = 1 - easeIn(phase(p, 0.06F, 0.6F));
                discAlpha = 1 - phase(p, 0.4F, 0.4F);
                extraSpin = easeIn(drain) * 1440;
                shudder = 0.12F * (1 - drain);
                ventFlow = Math.min(1, p / 0.06F) * (1 - phase(p, 0.86F, 0.08F));
                // 3. The last of it pops: a star, a flash and one last ring.
                hole *= 1 - easeIn(phase(p, 0.84F, 0.06F));
                star = spike(p, 0.9F, 0.06F);
                starSize = 1.5F;
                starColor = hot;
                flash = Math.max(flash, spike(p, 0.9F, 0.05F));
                shock2Progress = phase(p, 0.9F, 0.1F);
                shock2Color = hot;
            }
            case VENT_EMERGENCY -> {
                // Timeline (see EmergencyVent): fire, a standoff, the surge, the drain with rebounds, the implosion.
                float surge = EmergencyVent.SURGE, implode = EmergencyVent.IMPLODE;
                holeScale = 1.5F;
                // 1. The lance fires into a meltdown: the beams overload and snap, a red flash and shockwave.
                beams = p < 0.06F;
                beamWidth = 2.4F;
                beamAlpha = 1.6F;
                for (int i = 0; i < 4; i++) beamReach[i] = 1 - easeIn(phase(p, 0.02F + 0.006F * i, 0.03F));
                emitterFlare = spike(p, 0.04F, 0.04F) * 1.4F;
                emitterFlareColor = Look.RED;
                flash = spike(p, 0.012F, 0.03F) * 1.6F;
                flashSize = 10;
                flashColor = Look.RED;
                shockProgress = phase(p, 0.005F, 0.2F);
                shockColor = Look.RED;
                redBand = 0.6F * (1 - phase(p, 0.6F, 0.2F));
                // 2. Standoff: the black hole holds, swollen and throbbing, straining against the lance. Only thin
                //    flickers of light make it up into the beam.
                float standoff = phase(p, 0.02F, 0.05F) * (1 - phase(p, surge, 0.02F));
                float throb = (float) Math.exp(-(time % 12F) / 12F * 5);
                float swell = 1.2F + 0.1F * easeOut(phase(p, 0.02F, 0.1F));
                ventFlow = standoff * 0.3F * (noise(5, (int) (time / 2), 3) > 0.4F ? 1 : 0.2F);
                shudder = 0.18F * standoff + 0.12F * throb * standoff;
                // 3. The surge: a second flash and ring, and the column bursts open. Then the drain: it shrinks,
                //    rebounding against the pull again and again, each time with a jolt, a flash and a ring of
                //    light, while the disc is torn apart and swirls in.
                float drain = phase(p, surge, implode - surge);
                if (p >= surge) {
                    ventFlow = Math.min(1, (p - surge) / 0.02F) * (1 - phase(p, 0.9F, 0.05F));
                    flash = Math.max(flash, spike(p, surge + 0.005F, 0.02F) * 1.4F);
                    if (p < surge + 0.03F) {
                        flashSize = 9;
                        flashColor = Look.WHITE_VIOLET;
                    }
                    shock2Progress = phase(p, surge, 0.15F);
                    shock2Color = Look.WHITE_VIOLET;
                }
                float beat = EmergencyVent.beat(p);
                hole = Mth.lerp(easeInOut(drain), swell * (1 + 0.06F * throb * standoff), 0.06F) * (1 + 0.22F * beat);
                ringAlpha = (1.5F + 3 * drain) * (1 + 1.5F * beat) * (1 + 0.6F * throb * standoff);
                if (beat > 0.05F) {
                    shock2Progress = 1 - beat;
                    shock2Color = Look.MAGENTA;
                    flash = Math.max(flash, beat * 0.7F);
                    flashSize = 8;
                    flashColor = Look.RED;
                }
                discReach = 1 - easeIn(phase(p, surge, 0.4F));
                discAlpha = (1 - phase(p, surge + 0.25F, 0.2F)) * (noise(3, (int) (time * 2), 5) > 0.35F ? 1 : 0.25F);
                extraSpin = easeIn(drain) * 2160;
                shudder += 0.3F * (p >= surge ? 1 - drain : 0) + 0.25F * beat;
                // 4. It gives way and implodes: a white-hot star, a huge flash and a blast ring rolling out past
                //    the containment ring.
                hole *= 1 - easeIn(phase(p, implode - 0.04F, 0.04F));
                star = spike(p, implode, 0.04F) * 1.4F;
                starSize = 4;
                starColor = Look.WHITE_HOT;
                float boom = spike(p, implode, 0.05F);
                if (boom * 1.8F > flash) {
                    flash = boom * 1.8F;
                    flashSize = 16;
                    flashColor = Look.WHITE_VIOLET;
                }
                blast = phase(p, implode - 0.01F, 0.13F);
            }
            case STALL -> {
                // The disc seizes: a gold shockwave and a jolt.
                shockProgress = phase(p, 0, 0.8F);
                shockColor = Look.GOLD;
                flash = Math.max(0, 1 - p / 0.3F) * 0.7F;
                flashColor = Look.GOLD;
                shudder += 0.25F * (1 - p);
            }
            case ALARM -> {
                // Pre-meltdown begins: a red flash and a red shockwave.
                shockProgress = phase(p, 0, 0.8F);
                shockColor = Look.RED;
                flash = Math.max(0, 1 - p / 0.4F);
                flashColor = Look.RED;
            }
            case BREACH -> {
                // The ring breaks: a huge flash, two shockwaves and a storm of arcs (see the block entity).
                shockProgress = phase(p, 0, 0.6F);
                shockColor = Look.WHITE_VIOLET;
                flash = Math.max(0, 1 - p / 0.35F) * 1.2F;
                flashSize = 8;
                shudder += 0.3F * (1 - p);
            }
            case MELTDOWN_1 -> {
                // Phase 1: the black hole swells with an overshoot and a white-violet blast.
                holeScale = Mth.lerp(easeOutBack(phase(p, 0, 0.6F)), 1.15F, 1.5F);
                shockProgress = phase(p, 0.1F, 0.7F);
                flash = spike(p, 0.12F, 0.12F);
                flashSize = 7;
            }
            case MELTDOWN_2 -> {
                // Phase 2: the black hole grows to swallow the middle of the ring; a dark wave rolls out.
                holeScale = Mth.lerp(easeOut(phase(p, 0, 0.8F)), 1.5F, 3.0F);
                shockProgress = phase(p, 0, 0.9F);
                shockColor = Look.DIM;
            }
            case FAULT -> {
                // A fault strikes: a gold shockwave and a jolt.
                shockProgress = phase(p, 0, 0.8F);
                shockColor = Look.GOLD;
                flash = Math.max(0, 1 - p / 0.3F) * 0.8F;
                flashColor = Look.GOLD;
                shudder += 0.15F * (1 - p);
            }
            case NONE -> {}
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (shudder > 0) {
            pose.translate(shudder * (Mth.sin(time * 7.3F) + 0.5F * Mth.sin(time * 11.1F)),
                    shudder * 0.5F * Mth.sin(time * 9.7F + 1),
                    shudder * (Mth.sin(time * 6.1F + 2) + 0.5F * Mth.sin(time * 13.3F)));
        }
        // Size follows the reactor's mass (synced from the server); meltdowns swell it further.
        boolean venting = anim == ReactorCoreBlockEntity.Anim.VENT || anim == ReactorCoreBlockEntity.Anim.VENT_EMERGENCY;
        float baseHorizon = venting ? core.getVentHoleRadius() : core.getClientHoleRadius(); // the vent burns mass at once
        float horizon = Math.min(MAX_HORIZON, baseHorizon * holeScale) * hole;

        // Event horizon (opaque black, drawn first so the glow layers sit around it)
        if (horizon > 0.02F) renderSphere(pose, buffers.getBuffer(RenderType.debugQuads()), horizon);

        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());

        // Accretion disc, flat in the ring's hole and wobbling when unstable (kept small so it stays clear of the ring)
        pose.pushPose();
        // Precession: the disc leans toward the south-east (or north-west), up to 15 degrees at the tipping point.
        float lean = (float) Math.toRadians(core.getWobble() * 0.15);
        if (lean != 0) pose.mulPose(new org.joml.Quaternionf().rotateAxis(lean, 0.7071F, 0, -0.7071F));
        float wobble = look.wobble * Mth.sin(time * 0.35F);
        pose.mulPose(Axis.XP.rotationDegrees(wobble));
        pose.mulPose(Axis.ZP.rotationDegrees(wobble * 0.6F));
        pose.mulPose(Axis.YP.rotationDegrees(core.getDiscAngle(partialTick) + Math.copySign(extraSpin, core.getClientSpin())));
        float discInner = horizon + DISC_GAP;
        renderDisc(pose, glow, look.fade(discAlpha), discInner, Mth.lerp(discReach, discInner, DISC_OUTER), time);
        pose.popPose();

        // Disc stall with every breaker overcharged: ready to jump-start, a green ring around the black hole.
        if (core.isJumpReady()) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float a = 0.7F + 0.3F * Mth.sin(time * 1.2F);
            renderRing(pose, glow, horizon + 0.4F, horizon + 2.2F, Look.GREEN, a, 0);
            renderRing(pose, glow, horizon + 0.4F, horizon, Look.GREEN, a, 0);
            pose.popPose();
        }

        // Ring stress: the inner wall of the containment ring glows, orange to white-hot. Pre-meltdown adds a red pulse.
        float heat = (float) Mth.clamp((core.getStress() / 100 - 0.3) / 0.7, 0, 1);
        if (heat > 0) {
            float[] hot = lerp(Look.ORANGE, Look.WHITE_HOT, heat * heat);
            renderGlowBand(pose, glow, (float) ReactorStructure.RING_INNER_RADIUS - 0.05F, 2.3F, hot,
                    heat * (0.55F + 0.25F * Mth.sin(time * 0.4F)));
        }
        if (redBand > 0) {
            renderGlowBand(pose, glow, (float) ReactorStructure.RING_INNER_RADIUS - 0.1F, 2.5F, Look.RED, redBand);
        }

        // Shockwave across the hole
        if (shockProgress > 0 && shockProgress < 1) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float r = Mth.lerp(easeOut(shockProgress), baseHorizon, DISC_OUTER);
            float a = (1 - shockProgress) * 0.9F;
            renderRing(pose, glow, r, r + 1.2F, shockColor, a, 0);
            renderRing(pose, glow, r, Math.max(0, r - 0.8F), shockColor, a, 0);
            pose.popPose();
        }

        if (shock2Progress > 0 && shock2Progress < 1) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float r = Mth.lerp(easeOut(shock2Progress), baseHorizon, DISC_OUTER);
            float a = (1 - shock2Progress) * 0.7F;
            renderRing(pose, glow, r, r + 2.0F, shock2Color, a, 0);
            renderRing(pose, glow, r, Math.max(0, r - 1.2F), shock2Color, a, 0);
            pose.popPose();
        }

        if (ventFlow > 0) renderVentFlow(core, pose, glow, horizon, ventFlow, time);

        // The emergency vent's implosion: a blast ring rolling out far past the containment ring.
        if (blast > 0 && blast < 1) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float r = Mth.lerp(easeOut(blast), 1, 40);
            float a = (1 - blast) * 1.2F;
            renderRing(pose, glow, r, r + 3.5F, Look.WHITE_VIOLET, a, 0);
            renderRing(pose, glow, r, Math.max(0, r - 2.5F), Look.RED, a * 0.8F, 0);
            pose.popPose();
        }

        // Flares: the emitters charging or burning out, and the white-hot singularity at ignition and collapse
        if (emitterFlare > 0) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (!core.hasEmitter(dir)) continue;
                Vector3f at = new Vector3f(dir.getStepX(), 0, dir.getStepZ()).mul(BEAM_START);
                float size = 0.6F + 1.4F * Math.min(1, emitterFlare) + 0.2F * Mth.sin(time * 1.3F + dir.ordinal());
                renderFlare(pose, glow, at, size, emitterFlareColor, Math.min(1, emitterFlare));
            }
        }
        if (headFlare > 0) {
            float lateral = Mth.clamp(core.getOffset() / MAX_OFFSET_VISUAL, -1, 1) * horizon * 1.2F;
            int i = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                float reach = beamReach[i++];
                if (!core.hasEmitter(dir)) continue; // its emitter is gone: nothing to fire from
                if (reach <= 0 || reach >= 1) continue;
                Direction side = dir.getClockWise();
                Vector3f start = new Vector3f(dir.getStepX(), 0, dir.getStepZ()).mul(BEAM_START);
                Vector3f tip = new Vector3f(start).lerp(new Vector3f(side.getStepX(), 0, side.getStepZ()).mul(lateral), reach);
                renderFlare(pose, glow, tip, 1.2F, Look.WHITE_VIOLET, headFlare);
            }
        }
        if (star > 0) renderFlare(pose, glow, new Vector3f(), starSize, starColor, Math.min(1, star));

        // Photon ring, billboarded toward the camera, plus the animation flash
        pose.pushPose();
        faceCamera(pose);
        if (horizon > 0.02F) {
            renderRing(pose, glow, horizon * 1.02F, horizon * 1.35F, look.inner, look.alpha * ringAlpha, 0.0F);
            renderRing(pose, glow, horizon * 1.35F, horizon * 1.9F, look.outer, look.alpha * 0.35F * ringAlpha, 0.0F);
        }
        if (flash > 0) {
            float r = Math.max(horizon, 0.3F);
            renderRing(pose, glow, r, r + flashSize * Math.min(1, flash), flashColor, flash, 0.0F);
        }
        pose.popPose();

        // Stabilizer beams
        if (beams) {
            Look beamLook = look.fade(beamAlpha);
            // Negative offset pushes the beams to the other side of the black hole.
            float lateral = Mth.clamp(core.getOffset() / MAX_OFFSET_VISUAL, -1, 1) * horizon * 1.2F;
            int i = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                float reach = beamReach[i++];
                if (!core.hasEmitter(dir)) continue; // its emitter is gone: nothing to fire from
                if (reach <= 0) continue;
                if (look.flicker && Mth.sin(time * 1.7F + i * 2.3F) * Mth.sin(time * 0.9F + i) > 0.35F) continue;
                if (core.isClientStarving() && noise(i, (int) (time / 2), 3) > 0.6F) continue; // starved: sputtering
                float beamLateral = lateral, width = beamWidth;
                Look thisLook = beamLook;
                if (core.isFaulted(dir)) {
                    switch (fault) {
                        // Out of line: the beam wanders across the black hole and misses.
                        case STABILIZER_DESYNC -> beamLateral = lateral + Mth.sin(time * 0.6F + i * 1.7F) * Math.max(horizon, 1) * 2.6F;
                        // Burned off: thin and sputtering.
                        case MASS_FLASH -> {
                            if (noise(i, (int) (time / 2), 5) > 0.45F) continue;
                            width *= 0.4F;
                        }
                        // Cracked: orange and badly sputtering.
                        case CRACKED_LENS -> {
                            if (noise(i, (int) time, 9) > 0.35F) continue;
                            thisLook = beamLook.withOuter(Look.ORANGE);
                        }
                        default -> {}
                    }
                }
                renderBeam(pose, glow, dir, beamLateral, thisLook, time + i * 7, reach, width);
            }
            // Axial stabilizers: straight down from over the ring into the black hole.
            if (core.isAxialFormed()) {
                int k = 0;
                for (BlockPos emitter : ReactorStructure.axialEmitterOffsets()) {
                    if (!core.hasAxialEmitter(emitter)) continue;
                    Vector3f start = new Vector3f(emitter.getX(), emitter.getY() - 0.5F, emitter.getZ());
                    renderAxialBeam(pose, glow, start, horizon, beamLook, time + k++ * 11, beamWidth * 0.8F);
                }
            }
        }

        // Faulted emitters glow: red when out of line, white when their mass flashed off, flickering orange when cracked.
        if (state.isFault() && fault != ReactorCoreBlockEntity.Fault.SINGULARITY_REBOUND) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (!core.isFaulted(dir) || !core.hasEmitter(dir)) continue;
                float[] color = switch (fault) {
                    case STABILIZER_DESYNC -> Look.RED;
                    case CRACKED_LENS -> Look.ORANGE;
                    default -> Look.WHITE_VIOLET;
                };
                float a = fault == ReactorCoreBlockEntity.Fault.CRACKED_LENS
                        ? (noise(dir.ordinal(), (int) time, 4) > 0.5F ? 1 : 0.3F)
                        : 0.6F + 0.4F * Mth.sin(time * 0.5F + dir.ordinal());
                Vector3f at = new Vector3f(dir.getStepX(), 0, dir.getStepZ()).mul(BEAM_START);
                renderFlare(pose, glow, at, 1.4F, color, a);
            }
        }

        // Dark-energy arcs (fired and aimed by the block entity, which ray-traces them into the world)
        renderArcs(core, pose, glow, state == ReactorState.MELTDOWN_2 ? Look.RED : Look.LAVENDER);
        pose.popPose();
    }

    /** Jagged bolts from the black hole to whatever they hit, re-jittered every tick so they crackle. */
    private static void renderArcs(ReactorCoreBlockEntity core, PoseStack pose, VertexConsumer vc, float[] glowColor) {
        PoseStack.Pose p = pose.last();
        BlockPosCenter c = new BlockPosCenter(core);
        int now = core.getClientTicks();
        for (int k = 0; k < ReactorCoreBlockEntity.MAX_ARCS; k++) {
            if (core.getArcFrom(k) == null || core.getArcTo(k) == null) continue;
            int age = now - core.getArcBorn(k);
            float alpha = 1 - age / (float) ReactorCoreBlockEntity.ARC_LIFE;
            if (alpha <= 0) continue;
            Vector3f a = c.local(core.getArcFrom(k)), b = c.local(core.getArcTo(k));
            Vector3f dir = new Vector3f(b).sub(a);
            float length = dir.length();
            if (length < 0.5F) continue;
            dir.div(length);
            Vector3f side = new Vector3f(dir).cross(0, 1, 0);
            if (side.lengthSquared() < 1e-4F) side.set(1, 0, 0);
            side.normalize();
            Vector3f up = new Vector3f(dir).cross(side).normalize();
            int segments = 10;
            float amp = Math.min(1.5F, 0.15F * length);
            Vector3f prev = a;
            int core1 = argb(alpha, 1.0F, 0.95F, 1.0F), outer = argb(alpha * 0.4F, glowColor[0], glowColor[1], glowColor[2]);
            for (int i = 1; i <= segments; i++) {
                float t = i / (float) segments;
                Vector3f next = new Vector3f(a).lerp(b, t);
                if (i < segments) {
                    float bend = amp * Mth.sin(Mth.PI * t);
                    next.add(new Vector3f(side).mul((noise(k * 31 + i, now, 1) - 0.5F) * 2 * bend))
                        .add(new Vector3f(up).mul((noise(k * 31 + i, now, 2) - 0.5F) * 2 * bend));
                }
                for (Vector3f axis : new Vector3f[] {side, up}) {
                    beamPlane(p, vc, prev, next, axis, 0.08F, core1);
                    beamPlane(p, vc, prev, next, axis, 0.4F, outer);
                }
                prev = next;
            }
        }
    }

    /** Converts world positions to the renderer's local space (centered on the core). */
    private record BlockPosCenter(double x, double y, double z) {
        BlockPosCenter(ReactorCoreBlockEntity core) {
            this(core.getBlockPos().getX() + 0.5, core.getBlockPos().getY() + 0.5, core.getBlockPos().getZ() + 0.5);
        }

        Vector3f local(net.minecraft.world.phys.Vec3 v) {
            return new Vector3f((float) (v.x - x), (float) (v.y - y), (float) (v.z - z));
        }
    }

    /** A glowing vertical band on the inside of the ring: brightest at core height, fading up and down. */
    private static void renderGlowBand(PoseStack pose, VertexConsumer vc, float radius, float halfHeight, float[] color, float alpha) {
        PoseStack.Pose p = pose.last();
        int mid = argb(alpha, color[0], color[1], color[2]), edge = argb(0, color[0], color[1], color[2]);
        for (int s = 0; s < DISC_SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / DISC_SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / DISC_SEGMENTS;
            float x0 = radius * Mth.cos(a0), z0 = radius * Mth.sin(a0), x1 = radius * Mth.cos(a1), z1 = radius * Mth.sin(a1);
            for (float sign : new float[] {1, -1}) {
                float y = halfHeight * sign;
                // Both windings.
                vc.addVertex(p, x0, 0, z0).setColor(mid);
                vc.addVertex(p, x1, 0, z1).setColor(mid);
                vc.addVertex(p, x1, y, z1).setColor(edge);
                vc.addVertex(p, x0, y, z0).setColor(edge);
                vc.addVertex(p, x0, y, z0).setColor(edge);
                vc.addVertex(p, x1, y, z1).setColor(edge);
                vc.addVertex(p, x1, 0, z1).setColor(mid);
                vc.addVertex(p, x0, 0, z0).setColor(mid);
            }
        }
    }

    /** Cheap deterministic 0..1 noise. */
    private static float noise(int a, int b, int c) {
        return (Mth.getSeed(a, b, c) >>> 16 & 0xFFFF) / 65535F;
    }

    // ------------------------------------------------------------------ easing

    /** 0..1 progress through a sub-phase of the animation that starts at {@code from} and lasts {@code length}. */
    private static float phase(float p, float from, float length) {
        return Mth.clamp((p - from) / length, 0, 1);
    }

    /** A short bump: 1 at {@code at}, falling to 0 over {@code width} either side. */
    private static float spike(float p, float at, float width) {
        return Math.max(0, 1 - Math.abs(p - at) / width);
    }

    private static float easeOut(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float easeInOut(float x) {
        return x < 0.5F ? 2 * x * x : 1 - (-2 * x + 2) * (-2 * x + 2) / 2;
    }

    private static float easeIn(float x) {
        return x * x * x;
    }

    /** Overshoots a little before settling, so the black hole "pops" open. */
    private static float easeOutBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1, t = x - 1;
        return 1 + c3 * t * t * t + c1 * t * t;
    }

    // ------------------------------------------------------------------ geometry

    private static void renderSphere(PoseStack pose, VertexConsumer vc, float radius) {
        PoseStack.Pose p = pose.last();
        int stacks = 12, slices = 20;
        for (int i = 0; i < stacks; i++) {
            float a0 = Mth.PI * i / stacks - Mth.HALF_PI, a1 = Mth.PI * (i + 1) / stacks - Mth.HALF_PI;
            for (int j = 0; j < slices; j++) {
                float b0 = Mth.TWO_PI * j / slices, b1 = Mth.TWO_PI * (j + 1) / slices;
                sphereVertex(p, vc, radius, a0, b0);
                sphereVertex(p, vc, radius, a1, b0);
                sphereVertex(p, vc, radius, a1, b1);
                sphereVertex(p, vc, radius, a0, b1);
            }
        }
    }

    private static void sphereVertex(PoseStack.Pose p, VertexConsumer vc, float r, float lat, float lon) {
        float c = Mth.cos(lat);
        vc.addVertex(p, r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)).setColor(0, 0, 0, 255);
    }

    /** Flat annulus in the XZ plane with a hot inner edge fading out, and spiral streaks so the spin is visible. */
    private static void renderDisc(PoseStack pose, VertexConsumer vc, Look look, float inner, float outer, float time) {
        PoseStack.Pose p = pose.last();
        if (outer - inner < 0.3F || look.alpha <= 0) return;
        for (int band = 0; band < DISC_BANDS; band++) {
            float t0 = band / (float) DISC_BANDS, t1 = (band + 1) / (float) DISC_BANDS;
            float r0 = Mth.lerp(t0, inner, outer), r1 = Mth.lerp(t1, inner, outer);
            for (int s = 0; s < DISC_SEGMENTS; s++) {
                float a0 = Mth.TWO_PI * s / DISC_SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / DISC_SEGMENTS;
                int c00 = discColor(look, t0, a0, time), c01 = discColor(look, t0, a1, time);
                int c10 = discColor(look, t1, a0, time), c11 = discColor(look, t1, a1, time);
                // Both windings: the lightning render type culls back faces.
                discVertex(p, vc, r0, a0, c00);
                discVertex(p, vc, r1, a0, c10);
                discVertex(p, vc, r1, a1, c11);
                discVertex(p, vc, r0, a1, c01);
                discVertex(p, vc, r0, a1, c01);
                discVertex(p, vc, r1, a1, c11);
                discVertex(p, vc, r1, a0, c10);
                discVertex(p, vc, r0, a0, c00);
            }
        }
    }

    private static int discColor(Look look, float t, float angle, float time) {
        // Three spiral arms; the spiral leans with the radius so it reads as matter falling in.
        float streak = 0.7F + 0.3F * Mth.sin(3 * angle + t * 9F);
        float pulse = look.pulse > 0 ? 1 - look.pulse * (0.5F + 0.5F * Mth.sin(time * 0.25F)) : 1;
        float fade = (1 - t * t) * (0.3F + 0.7F * (1 - t)); // hot and bright inside, fading to nothing at the ring
        float[] c = lerp(look.inner, look.outer, Mth.sqrt(t));
        float a = look.alpha * fade * streak * pulse;
        return argb(a, c[0], c[1], c[2]);
    }

    private static void discVertex(PoseStack.Pose p, VertexConsumer vc, float r, float angle, int argb) {
        vc.addVertex(p, r * Mth.cos(angle), 0, r * Mth.sin(angle)).setColor(argb);
    }

    /** Annulus in the XY plane, for the billboarded photon ring. */
    private static void renderRing(PoseStack pose, VertexConsumer vc, float r0, float r1, float[] color, float alpha, float z) {
        PoseStack.Pose p = pose.last();
        int in = argb(alpha, color[0], color[1], color[2]), out = argb(0, color[0], color[1], color[2]);
        for (int s = 0; s < RING_SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / RING_SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / RING_SEGMENTS;
            vc.addVertex(p, r0 * Mth.cos(a0), r0 * Mth.sin(a0), z).setColor(in);
            vc.addVertex(p, r0 * Mth.cos(a1), r0 * Mth.sin(a1), z).setColor(in);
            vc.addVertex(p, r1 * Mth.cos(a1), r1 * Mth.sin(a1), z).setColor(out);
            vc.addVertex(p, r1 * Mth.cos(a0), r1 * Mth.sin(a0), z).setColor(out);
            vc.addVertex(p, r0 * Mth.cos(a0), r0 * Mth.sin(a0), z).setColor(in);
            vc.addVertex(p, r1 * Mth.cos(a0), r1 * Mth.sin(a0), z).setColor(out);
            vc.addVertex(p, r1 * Mth.cos(a1), r1 * Mth.sin(a1), z).setColor(out);
            vc.addVertex(p, r0 * Mth.cos(a1), r0 * Mth.sin(a1), z).setColor(in);
        }
    }

    /**
     * The Venting Lance: a lens glow at its tip while the reactor runs (pulsing red once armed), and when it fires,
     * a beam as wide as the hole it bores, straight up past the top of the world, with shock rings at the tip.
     */
    private static void renderLance(ReactorCoreBlockEntity core, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        int ventTicks = core.getVentTicks();
        boolean running = core.isLanceFormed() && core.isFormed() && core.getState() != ReactorState.OFFLINE;
        if (ventTicks <= 0 && !running) return;
        float time = core.getClientTicks() + partialTick;
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        Vector3f tip = new Vector3f(0, core.getLanceTop() + 1, 0);
        if (ventTicks > 0) {
            boolean emergency = core.isVentEmergency();
            float age = VentingLance.beamTicks(emergency) - ventTicks + partialTick;
            float env = Math.min(1, age / 5) * Math.min(1, (ventTicks - partialTick) / 20F);
            float radius = VentingLance.beamRadius(emergency) + 0.5F;
            float width = radius * 2 * (1 + 0.08F * Mth.sin(time * 0.9F));
            float top = core.getLevel() == null ? 400 : core.getLevel().getMaxBuildHeight() - core.getBlockPos().getY() + 64;
            Vector3f start = new Vector3f(0, core.getLanceTop() + 1, 0), end = new Vector3f(0, top, 0);
            float[] outer = emergency ? Look.RED : Look.VIOLET;
            int coreColor = argb(env, 1.0F, 0.95F, 1.0F), glowColor = argb(env * 0.35F, outer[0], outer[1], outer[2]);
            PoseStack.Pose p = pose.last();
            for (int k = 0; k < 4; k++) {
                float a = Mth.PI / 4 * k;
                Vector3f axis = new Vector3f(Mth.cos(a), 0, Mth.sin(a));
                beamPlane(p, glow, start, end, axis, width * 0.6F, coreColor);
                beamPlane(p, glow, start, end, axis, width * 2.2F, glowColor);
            }
            // Shock rings pumping out of the tip.
            pose.pushPose();
            pose.translate(0, core.getLanceTop() + 1, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            for (int ring = 0; ring < 2; ring++) {
                float k = ((age + ring * 5) % 10) / 10F;
                float r = radius + 10 * k;
                renderRing(pose, glow, r, r + 1.5F, outer, (1 - k) * env, 0);
            }
            pose.popPose();
            renderFlare(pose, glow, tip, (radius + 2) * env, Look.WHITE_VIOLET, env);
        } else {
            boolean armed = core.isVentArmed();
            float a = armed ? 0.6F + 0.4F * Mth.sin(time * 1.5F) : 0.5F + 0.15F * Mth.sin(time * 0.2F);
            renderFlare(pose, glow, tip, armed ? 2.2F : 1.2F, armed ? Look.RED : Look.LAVENDER, a);
        }
        pose.popPose();
    }

    /**
     * Venting, seen from the core: a column of light carrying the black hole up into the lance, bright packets
     * racing up it, and sparks spiraling in from around the hole and up into the column.
     */
    private static void renderVentFlow(ReactorCoreBlockEntity core, PoseStack pose, VertexConsumer glow, float horizon,
                                       float flow, float time) {
        float[] outer = core.isVentEmergency() ? Look.RED : Look.VIOLET;
        float top = core.isLanceFormed() ? core.getLanceTop() + 0.5F : 12;
        float bottom = horizon * 0.5F;
        if (top <= bottom) return;
        boolean emergency = core.isVentEmergency();
        float width = Math.max(0.35F, horizon * 1.1F) * (emergency ? 1.8F : 1) * (1 + (emergency ? 0.25F : 0.1F) * Mth.sin(time * 1.3F));
        Vector3f start = new Vector3f(0, bottom, 0), end = new Vector3f(0, top, 0);
        int coreColor = argb(flow, 1.0F, 0.95F, 1.0F), glowColor = argb(flow * 0.4F, outer[0], outer[1], outer[2]);
        PoseStack.Pose p = pose.last();
        for (int k = 0; k < 4; k++) {
            float a = Mth.PI / 4 * k;
            Vector3f axis = new Vector3f(Mth.cos(a), 0, Mth.sin(a));
            beamPlane(p, glow, start, end, axis, width * 0.5F, coreColor);
            beamPlane(p, glow, start, end, axis, width * 2.0F, glowColor);
        }
        // Packets of light racing up the column.
        float length = top - bottom;
        for (int k = 0; k < 6; k++) {
            float y = bottom + ((time * 0.8F + k * length / 6) % length);
            renderFlare(pose, glow, new Vector3f(0, y, 0), width * 0.9F, Look.WHITE_VIOLET, flow * 0.8F);
        }
        // Sparks spiraling in from around the hole, then rising into the column.
        int sparks = 28;
        for (int k = 0; k < sparks; k++) {
            float f = (time * 0.025F + k / (float) sparks) % 1;
            float radius = Mth.lerp(f * f, horizon * 3.5F + 3, horizon * 0.4F);
            float angle = k * 2.39996F + f * Mth.TWO_PI * 1.5F + time * 0.05F;
            float y = (float) (horizon * 0.2F + Math.pow(f, 3) * Math.min(length, 8));
            Vector3f at = new Vector3f(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius);
            renderFlare(pose, glow, at, 0.35F + 0.25F * f, outer, flow * Mth.sin(Mth.PI * f));
        }
    }

    /**
     * Turns the pose so its XY plane faces the camera's position. It deliberately ignores which way the camera is
     * looking: billboards turned by the camera's rotation spin as you turn your head. The pose matrix maps to
     * camera-relative space, so the camera is the origin there; its inverse gives the camera in local space.
     */
    private static void faceCamera(PoseStack pose) {
        Vector3f toCamera = new Matrix4f(pose.last().pose()).invert().transformPosition(new Vector3f());
        if (toCamera.lengthSquared() < 1e-6F) return;
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), toCamera.normalize()));
    }

    /** A soft glowing dot at {@code at} that always faces the camera: bright in the middle, fading out. */
    private static void renderFlare(PoseStack pose, VertexConsumer vc, Vector3f at, float size, float[] color, float alpha) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        faceCamera(pose);
        renderRing(pose, vc, 0, size, color, alpha, 0);
        renderRing(pose, vc, 0, size * 0.35F, Look.WHITE_HOT, alpha, 0); // white-hot core
        pose.popPose();
    }

    /** A beam from the emitter on the {@code dir} side to the core, as two crossed planes plus a soft outer glow. */
    private static void renderBeam(PoseStack pose, VertexConsumer vc, Direction dir, float lateral, Look look, float time,
                                   float reach, float widthScale) {
        Direction side = dir.getClockWise();
        Vector3f start = new Vector3f(dir.getStepX(), 0, dir.getStepZ()).mul(BEAM_START);
        Vector3f end = new Vector3f(side.getStepX(), 0, side.getStepZ()).mul(lateral);
        end = new Vector3f(start).lerp(end, reach); // grows from the emitter toward the core
        Vector3f across = new Vector3f(side.getStepX(), 0, side.getStepZ());
        Vector3f up = new Vector3f(0, 1, 0);
        float width = look.beamWidth * widthScale * (0.85F + 0.15F * Mth.sin(time * 0.4F));
        int coreColor = argb(look.alpha, 0.85F, 0.75F, 1.0F);
        int glowColor = argb(look.alpha * 0.3F, look.outer[0], look.outer[1], look.outer[2]);
        PoseStack.Pose p = pose.last();
        for (Vector3f axis : new Vector3f[] {up, across}) {
            beamPlane(p, vc, start, end, axis, width, coreColor);
            beamPlane(p, vc, start, end, axis, width * 3.5F, glowColor);
        }
    }

    /** A beam from an axial emitter to the edge of the black hole, as two crossed planes plus a soft outer glow. */
    private static void renderAxialBeam(PoseStack pose, VertexConsumer vc, Vector3f start, float horizon, Look look,
                                        float time, float widthScale) {
        Vector3f toCore = new Vector3f(start).negate().normalize();
        Vector3f end = new Vector3f(toCore).mul(-horizon); // stops at the horizon
        Vector3f across = new Vector3f(toCore).cross(0, 1, 0).normalize();
        Vector3f other = new Vector3f(toCore).cross(across).normalize();
        float width = look.beamWidth * widthScale * (0.85F + 0.15F * Mth.sin(time * 0.4F));
        int coreColor = argb(look.alpha, 0.85F, 0.75F, 1.0F);
        int glowColor = argb(look.alpha * 0.3F, look.outer[0], look.outer[1], look.outer[2]);
        PoseStack.Pose p = pose.last();
        for (Vector3f axis : new Vector3f[] {across, other}) {
            beamPlane(p, vc, start, end, axis, width, coreColor);
            beamPlane(p, vc, start, end, axis, width * 3.5F, glowColor);
        }
    }

    private static void beamPlane(PoseStack.Pose p, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f axis, float width, int color) {
        float hx = axis.x * width / 2, hy = axis.y * width / 2, hz = axis.z * width / 2;
        int edge = color & 0x00FFFFFF;
        // Bright center line, transparent edges, both windings.
        quad(p, vc, a.x, a.y, a.z, b.x, b.y, b.z, hx, hy, hz, color, edge);
        quad(p, vc, a.x, a.y, a.z, b.x, b.y, b.z, -hx, -hy, -hz, color, edge);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float hx, float hy, float hz, int center, int edge) {
        vc.addVertex(p, ax, ay, az).setColor(center);
        vc.addVertex(p, bx, by, bz).setColor(center);
        vc.addVertex(p, bx + hx, by + hy, bz + hz).setColor(edge);
        vc.addVertex(p, ax + hx, ay + hy, az + hz).setColor(edge);
        vc.addVertex(p, ax + hx, ay + hy, az + hz).setColor(edge);
        vc.addVertex(p, bx + hx, by + hy, bz + hz).setColor(edge);
        vc.addVertex(p, bx, by, bz).setColor(center);
        vc.addVertex(p, ax, ay, az).setColor(center);
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    private static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ------------------------------------------------------------------ per-state look

    private record Look(float[] inner, float[] outer, float alpha, float pulse, float horizonScale,
                        float wobble, boolean beams, boolean flicker, float beamWidth) {
        static final float[] WHITE_VIOLET = {1.0F, 0.9F, 1.0F}, VIOLET = {0.55F, 0.2F, 1.0F}, LAVENDER = {0.85F, 0.7F, 1.0F},
                MAGENTA = {0.8F, 0.15F, 0.75F}, GOLD = {1.0F, 0.7F, 0.25F}, RED = {0.9F, 0.1F, 0.2F}, DIM = {0.35F, 0.15F, 0.6F},
                GREEN = {0.3F, 1.0F, 0.45F}, ORANGE = {1.0F, 0.45F, 0.1F}, WHITE_HOT = {1.0F, 0.95F, 0.8F};

        Look fade(float factor) {
            return factor == 1 ? this : new Look(inner, outer, alpha * factor, pulse, horizonScale, wobble, beams, flicker, beamWidth);
        }

        Look withOuter(float[] color) {
            return new Look(inner, color, alpha, pulse, horizonScale, wobble, beams, flicker, beamWidth);
        }

        static Look of(ReactorState state, float time, boolean scrammed, ReactorCoreBlockEntity.Fault fault) {
            return switch (state) {
                // Faults: the black hole is open but held badly. A rebound has no beams left, just a throbbing core.
                case IGNITION_FAULT -> new Look(LAVENDER, GOLD, 0.8F, 0.35F, 1, 2F, true, false, 0.22F);
                case SHUTDOWN_FAULT -> fault == ReactorCoreBlockEntity.Fault.SINGULARITY_REBOUND
                        ? new Look(WHITE_VIOLET, VIOLET, 0.55F, 0.6F, 1.2F, 0.5F, false, false, 0)
                        : new Look(LAVENDER, ORANGE, 0.7F, 0.2F, 1, 1.5F, true, false, 0.2F);
                case OFFLINE -> throw new IllegalStateException("an offline reactor has no black hole");
                // After a SCRAM the beams are cut and the disc spins down red-hot.
                case SHUTTING_DOWN -> scrammed
                        ? new Look(RED, MAGENTA, 0.65F, 0.4F, 1, 1.5F, false, false, 0)
                        : new Look(LAVENDER, VIOLET, 0.6F, 0, 1, 0, true, false, 0.18F);
                case STABLE -> new Look(LAVENDER, VIOLET, 0.9F, 0, 1, 0, true, false, 0.3F);
                case WARNING -> new Look(LAVENDER, MAGENTA, 0.9F, 0.15F, 1, 1F, true, false, 0.24F);
                case DISC_STALL -> new Look(GOLD, MAGENTA, 0.85F, 0.3F, 1, 3F, true, true, 0.24F);
                case PRE_MELTDOWN -> new Look(WHITE_VIOLET, LAVENDER, 1.0F, 0.25F, 1.15F, 1.5F, true, true, 0.34F);
                case MELTDOWN_1 -> new Look(WHITE_VIOLET, RED, 1.0F, 0.35F, 1.5F, 2F, true, true, 0.2F);
                // The core becomes a black sphere; only a thin, failing disc is left around it.
                case MELTDOWN_2 -> new Look(RED, DIM, 0.5F, 0.5F, 3.0F + 0.2F * Mth.sin(time * 0.2F), 1F, false, false, 0);
            };
        }
    }

    // ------------------------------------------------------------------ culling

    @Override
    public boolean shouldRenderOffScreen(ReactorCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(ReactorCoreBlockEntity core) {
        if (core.getVentTicks() > 0) return AABB.INFINITE; // the vent beam goes up past the top of the world
        return new AABB(core.getBlockPos()).inflate(ReactorStructure.STABILIZER_DISTANCE + 1, 16, ReactorStructure.STABILIZER_DISTANCE + 1); // arcs reach high
    }
}
