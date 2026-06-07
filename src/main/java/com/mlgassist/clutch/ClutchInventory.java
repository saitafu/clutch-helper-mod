package com.mlgassist.clutch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.util.math.BlockPos;

public final class ClutchInventory {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int SCREEN_HOTBAR_START = 36;

    private ClutchInventory() {
    }

    public static int findSlot(PlayerEntity player, ClutchType type) {
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && type.matches(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    public static int findSolidBlockSlot(PlayerEntity player) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isSolidBlock(stack)) {
                return i;
            }
        }
        for (int i = HOTBAR_SIZE; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isSolidBlock(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSolidBlock(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ClutchType.fromStack(stack) != null) {
            return false;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        BlockState state = block.getDefaultState();
        return state.isOpaque() || state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    public static int ensureHotbarSlot(MinecraftClient client, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= MAIN_INVENTORY_SIZE) {
            return -1;
        }

        if (inventorySlot < HOTBAR_SIZE) {
            return inventorySlot;
        }

        PlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return -1;
        }

        int hotbarSlot = findSwapHotbarSlot(player);
        int screenSlot = toScreenHandlerSlot(inventorySlot);

        client.interactionManager.clickSlot(
                player.currentScreenHandler.syncId,
                screenSlot,
                hotbarSlot,
                SlotActionType.SWAP,
                player
        );

        return hotbarSlot;
    }

    static int toScreenHandlerSlot(int inventoryIndex) {
        if (inventoryIndex < HOTBAR_SIZE) {
            return SCREEN_HOTBAR_START + inventoryIndex;
        }
        return inventoryIndex;
    }

    private static int findSwapHotbarSlot(PlayerEntity player) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return player.getInventory().getSelectedSlot();
    }

    public static void selectHotbarSlot(PlayerEntity player, int hotbarSlot) {
        if (hotbarSlot >= 0 && hotbarSlot < HOTBAR_SIZE) {
            player.getInventory().setSelectedSlot(hotbarSlot);
        }
    }
}
