package com.masteri.bhe.block.entity;

import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.menu.ResearchMenu;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.research.Experiment;
import com.masteri.bhe.research.ResearchChamber;
import com.masteri.bhe.research.Step;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Runs experiments in the Research Chamber. Starting one rolls a random procedure for that failure (see Step);
 * each step has 30 seconds. Doing the current step moves on, and the last one produces the failure's Recording.
 * Doing the wrong thing, running out of time or breaking the chamber mid-experiment fails it: the containment
 * gives way, the captured black hole is lost (the box is gone) and a small blast goes off, with no block damage.
 */
public class ResearchCoreBlockEntity extends BlockEntity {
    /** The resonance lamp: a PULSE only takes while it's green, for 0.75 s of every 3 s. */
    public static final int PULSE_PERIOD = 60, PULSE_OPEN = 15;
    // Buttons 0..5 start that experiment.
    public static final int BUTTON_FIELD_DOWN = 10, BUTTON_FIELD_UP = 11, BUTTON_LOCK = 12, BUTTON_PULSE = 13,
            BUTTON_VENT = 14, BUTTON_CHARGE = 15, BUTTON_ABORT = 16;
    private static final int FIXED_DATA = 9;
    public static final int DATA_SIZE = FIXED_DATA + Experiment.MAX_STEPS;

    @Nullable private Experiment experiment;
    private int[] steps = new int[0];
    private int stepIndex, stepTicks, field = 50, fed, ticks;
    private ResearchChamber.Status status = ResearchChamber.Status.INCOMPLETE;

    /** Synced to an open panel. Slots 9 and up are the procedure's steps (-1 past the end). */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i >= FIXED_DATA) return i - FIXED_DATA < steps.length ? steps[i - FIXED_DATA] : -1;
            return switch (i) {
                case 0 -> experiment == null ? 0 : experiment.ordinal() + 1;
                case 1 -> stepIndex;
                case 2 -> steps.length;
                case 3 -> (stepTicks + 19) / 20;
                case 4 -> field;
                case 5 -> fed;
                case 6 -> pulseOpen() ? 1 : 0;
                case 7 -> status.ordinal();
                case 8 -> level != null && level.hasNeighborSignal(worldPosition) ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int i, int value) {}
        @Override public int getCount() { return DATA_SIZE; }
    };

    public ResearchCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_CORE.get(), pos, state);
    }

    public ContainerData getData() {
        return data;
    }

    private Direction facing() {
        return getBlockState().getValue(FacingBlock.FACING);
    }

    private BlockPos center() {
        return ResearchChamber.center(worldPosition, facing());
    }

    private boolean pulseOpen() {
        return ticks % PULSE_PERIOD < PULSE_OPEN;
    }

    @Nullable
    private Step.Type currentType() {
        return experiment == null || stepIndex >= steps.length ? null : Step.type(steps[stepIndex]);
    }

    public void open(Player player) {
        BlockPos pos = worldPosition;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ResearchMenu(id, inventory, pos, this),
                Component.translatable("container.masteris_bhe.research_core")), buf -> buf.writeBlockPos(pos));
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick() {
        if (level == null) return;
        ticks++;
        if (ticks % 10 == 0) status = ResearchChamber.check(level, worldPosition, facing());
        if (experiment == null) return;
        if (status != ResearchChamber.Status.READY) {
            fail("chamber");
            return;
        }
        if (--stepTicks <= 0) {
            fail("timeout");
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        Step.Type type = currentType();
        if ((type == Step.Type.POWER_ON && powered) || (type == Step.Type.POWER_OFF && !powered)) advance();

        if (ticks % 4 == 0 && level instanceof ServerLevel server) {
            Vec3 c = Vec3.atCenterOf(center());
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0.02);
            if (pulseOpen()) server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.3, 0.3, 0.3, 0.01);
        }
    }

    // ------------------------------------------------------------------ controls

    /** A button on the panel. */
    public void button(Player player, int id) {
        if (id >= 0 && id < Experiment.values().length) {
            start(player, Experiment.byId(id));
            return;
        }
        if (experiment == null) return;
        switch (id) {
            case BUTTON_FIELD_DOWN -> field = Math.max(0, field - 10);
            case BUTTON_FIELD_UP -> field = Math.min(100, field + 10);
            case BUTTON_LOCK -> expect(Step.Type.FIELD, field == Step.param(steps[stepIndex]));
            case BUTTON_PULSE -> expect(Step.Type.PULSE, pulseOpen());
            case BUTTON_VENT -> expect(Step.Type.VENT, true);
            case BUTTON_CHARGE -> expect(Step.Type.CHARGE, true);
            case BUTTON_ABORT -> {
                experiment = null;
                tell(player, "aborted", ChatFormatting.GRAY);
                sound(SoundEvents.BEACON_DEACTIVATE, 1.2F);
            }
            default -> {}
        }
        setChanged();
    }

    private void start(Player player, Experiment which) {
        if (experiment != null) {
            tell(player, "running", ChatFormatting.YELLOW);
            return;
        }
        status = ResearchChamber.check(level, worldPosition, facing());
        if (status == ResearchChamber.Status.INCOMPLETE) {
            tell(player, "incomplete", ChatFormatting.RED);
            return;
        }
        if (status == ResearchChamber.Status.NO_SINGULARITY) {
            tell(player, "no_singularity", ChatFormatting.RED);
            return;
        }
        experiment = which;
        steps = Step.generate(which.steps, level.random);
        stepIndex = 0;
        stepTicks = com.masteri.bhe.config.ReactorConfig.RESEARCH_STEP_SECONDS.get() * 20;
        fed = 0;
        field = 50;
        sound(SoundEvents.BEACON_ACTIVATE, 1.4F);
        player.displayClientMessage(Component.translatable("message.masteris_bhe.research.started",
                Component.translatable(which.translationKey())).withStyle(ChatFormatting.AQUA), false);
        setChanged();
    }

    /** The control for {@code type} was used: right if that's the current step and {@code ok}; otherwise it fails. */
    private void expect(Step.Type type, boolean ok) {
        if (currentType() == type && ok) advance();
        else fail("wrong");
    }

    /** Using an item on the core: feeds it in if the current step wants it. Returns whether it was taken. */
    public boolean feed(Player player, ItemStack stack) {
        if (currentType() != Step.Type.FEED || stack.isEmpty()) return false;
        Step.Feed want = Step.feed(steps[stepIndex]);
        if (!stack.is(want.item())) return false;
        int take = Math.min(stack.getCount(), want.count() - fed);
        if (!player.getAbilities().instabuild) stack.shrink(take);
        fed += take;
        sound(SoundEvents.ITEM_PICKUP, 0.6F);
        if (fed >= want.count()) advance();
        setChanged();
        return true;
    }

    /** The Configurator was used on this chamber's containment box. Returns false if no experiment is running. */
    public boolean tune() {
        if (experiment == null) return false;
        expect(Step.Type.TUNE, true);
        return true;
    }

    /** Whether {@code pos} is this chamber's containment box spot. */
    public boolean isCenter(BlockPos pos) {
        return center().equals(pos);
    }

    private void advance() {
        stepIndex++;
        stepTicks = com.masteri.bhe.config.ReactorConfig.RESEARCH_STEP_SECONDS.get() * 20;
        fed = 0;
        if (stepIndex >= steps.length) {
            succeed();
        } else {
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F + 0.1F * stepIndex);
        }
        setChanged();
    }

    private void succeed() {
        Experiment done = experiment;
        experiment = null;
        if (!(level instanceof ServerLevel server) || done == null) return;
        Vec3 c = Vec3.atCenterOf(center());
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.5, 0.5, 0.5, 0.15);
        sound(SoundEvents.BEACON_POWER_SELECT, 0.8F);
        sound(SoundEvents.PLAYER_LEVELUP, 1.0F);
        Vec3 out = Vec3.atCenterOf(worldPosition.relative(facing()));
        level.addFreshEntity(new ItemEntity(level, out.x, out.y, out.z, new ItemStack(done.recording()), 0, 0.2, 0));
        broadcast(Component.translatable("message.masteris_bhe.research.success",
                Component.translatable(done.translationKey())).withStyle(ChatFormatting.GREEN));
    }

    /** The containment gives way: the captured black hole is lost. */
    private void fail(String reason) {
        experiment = null;
        setChanged();
        if (!(level instanceof ServerLevel server)) return;
        BlockPos center = center();
        Vec3 c = Vec3.atCenterOf(center);
        if (level.getBlockState(center).is(ModBlocks.CONTAINMENT_BOX.get())) {
            level.setBlock(center, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.explode(null, c.x, c.y, c.z, 2F, Level.ExplosionInteraction.NONE);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.6, 0.6, 0.6, 0.4);
        sound(SoundEvents.GLASS_BREAK, 0.6F);
        broadcast(Component.translatable("message.masteris_bhe.research.failed." + reason).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ helpers

    private void sound(SoundEvent sound, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1F, pitch);
    }

    private void tell(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("message.masteris_bhe.research." + key).withStyle(color), true);
    }

    /** To everyone near the chamber (some steps finish without anyone pressing anything). */
    private void broadcast(Component message) {
        if (level == null) return;
        Vec3 here = Vec3.atCenterOf(worldPosition);
        for (Player player : level.players()) {
            if (player.distanceToSqr(here) < 24 * 24) player.displayClientMessage(message, false);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (experiment != null) {
            tag.putInt("Experiment", experiment.ordinal());
            tag.putIntArray("Steps", steps);
            tag.putInt("Step", stepIndex);
            tag.putInt("StepTicks", stepTicks);
            tag.putInt("Fed", fed);
        }
        tag.putInt("Field", field);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        experiment = tag.contains("Experiment") ? Experiment.byId(tag.getInt("Experiment")) : null;
        steps = tag.getIntArray("Steps");
        stepIndex = tag.getInt("Step");
        stepTicks = tag.getInt("StepTicks");
        fed = tag.getInt("Fed");
        field = tag.contains("Field") ? tag.getInt("Field") : 50;
        if (experiment != null && steps.length == 0) experiment = null;
    }
}
