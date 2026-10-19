package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.ControlConsoleBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.util.Tickers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Console: a 4 wide, 3 tall monitor wall. Placing one block builds the whole wall, centered on where you
 * placed it (one column to the left of it, two to the right, two rows up). The stats are drawn on its face and the
 * buttons are pressed by using the face itself. Sneak-use opens the older console GUI.
 *
 * Each part knows its column (0 at the left as you face the screen) and row (0 at the bottom); the bottom-left part
 * is the origin, which holds the block entity and drops the item. Breaking any part takes the whole wall down.
 */
public class ControlConsoleBlock extends FacingBlock implements EntityBlock {
    public static final MapCodec<ControlConsoleBlock> CONSOLE_CODEC = simpleCodec(ControlConsoleBlock::new);
    public static final int COLUMNS = 4, ROWS = 3;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, ROWS - 1);
    /** The placed block's spot in the wall. */
    private static final int PLACED_COLUMN = 1;

    /** Screen layout, in units of 1/64 block, x to the right and y down from the screen's top-left corner. */
    public static final int UNITS = 64, SCREEN_WIDTH = COLUMNS * UNITS, SCREEN_HEIGHT = ROWS * UNITS;

    public record Button(int id, String key, int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public static final List<Button> BUTTONS = List.of(
            new Button(ReactorCoreBlockEntity.BUTTON_IGNITE, "ignite", 8, 124, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_SHUTDOWN, "shutdown", 70, 124, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_SCRAM, "scram", 132, 124, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_JUMP_START, "jump", 194, 124, 54, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_OFFSET_DOWN, "offset_down", 8, 147, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_OFFSET_UP, "offset_up", 70, 147, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_LASER_DOWN, "laser_down", 132, 147, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_LASER_UP, "laser_up", 194, 147, 54, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_TRIM_NW, "trim_nw", 8, 170, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_TRIM_SE, "trim_se", 70, 170, 56, 18),
            new Button(ReactorCoreBlockEntity.BUTTON_VENT, "vent", 132, 170, 116, 18));

    public ControlConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CONSOLE_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
    }

    // ------------------------------------------------------------------ the wall

    /** To the right as you face the screen. */
    public static Direction right(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public static boolean isOrigin(BlockState state) {
        return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    public static BlockPos origin(BlockPos pos, BlockState state) {
        return pos.relative(right(state), -state.getValue(COLUMN)).below(state.getValue(ROW));
    }

    /** Every part's position, origin first. */
    public static List<BlockPos> parts(BlockPos origin, Direction facing) {
        List<BlockPos> list = new ArrayList<>();
        Direction right = facing.getCounterClockWise();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) list.add(origin.relative(right, col).above(row));
        }
        return list;
    }

    private BlockState part(Direction facing, int col, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(COLUMN, col).setValue(ROW, row);
    }

    /** Builds a whole wall with its bottom-left at {@code origin} (for the debug assembler). Returns blocks placed. */
    public int placeWall(Level level, BlockPos origin, Direction facing) {
        int placed = 0;
        Direction right = facing.getCounterClockWise();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                level.setBlock(origin.relative(right, col).above(row), part(facing, col, row), Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos().relative(facing.getCounterClockWise(), -PLACED_COLUMN);
        Level level = context.getLevel();
        for (BlockPos pos : parts(origin, facing)) {
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return part(facing, PLACED_COLUMN, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockPos origin = origin(pos, state);
        Direction right = right(state);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                BlockPos p = origin.relative(right, col).above(row);
                if (!p.equals(pos)) level.setBlock(p, part(state.getValue(FACING), col, row), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Only the origin drops the item: breaking any other part breaks the origin too (with its drop).
        if (!level.isClientSide && !player.isCreative() && !isOrigin(state)) {
            BlockPos origin = origin(pos, state);
            if (level.getBlockState(origin).is(this)) level.destroyBlock(origin, true, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos origin = origin(pos, state);
            for (BlockPos p : parts(origin, state.getValue(FACING))) {
                BlockState other = level.getBlockState(p);
                if (!p.equals(pos) && other.is(this) && origin(p, other).equals(origin)) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------ screen

    /**
     * Where a hit on the wall lands on the screen, in screen units (x right, y down from the top-left), or null if it
     * wasn't on the screen's face.
     */
    @Nullable
    public static double[] toScreen(BlockPos pos, BlockState state, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) return null;
        Vec3 d = hit.getLocation().subtract(Vec3.atCenterOf(origin(pos, state)));
        Direction right = right(state);
        double u = d.x * right.getStepX() + d.z * right.getStepZ() + 0.5; // 0..COLUMNS
        double v = ROWS - 0.5 - d.y;                                       // 0..ROWS, from the top
        if (u < 0 || u > COLUMNS || v < 0 || v > ROWS) return null;
        return new double[] {u * UNITS, v * UNITS};
    }

    @Nullable
    public static Button buttonAt(double x, double y, boolean ventShown) {
        for (Button button : BUTTONS) {
            if (button.id() == ReactorCoreBlockEntity.BUTTON_VENT && !ventShown) continue;
            if (button.contains(x, y)) return button;
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos origin = origin(pos, state);
        if (!(level.getBlockEntity(origin) instanceof ControlConsoleBlockEntity console)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) console.open(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        double[] at = toScreen(pos, state, hit);
        Button button = at == null ? null : buttonAt(at[0], at[1], console.readout().lanceFormed());
        if (button == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            console.press(player, button.id());
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------ block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isOrigin(state) ? new ControlConsoleBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : Tickers.of(type, ModBlockEntities.CONTROL_CONSOLE.get(), (l, p, s, console) -> console.serverTick());
    }
}
