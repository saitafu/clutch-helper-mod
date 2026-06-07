package com.mlgassist;

import com.mlgassist.clutch.ClutchExecutor;
import com.mlgassist.clutch.ClutchInventory;
import com.mlgassist.clutch.ClutchKeybinds;
import com.mlgassist.clutch.ClutchSelector;
import com.mlgassist.clutch.ClutchTiming;
import com.mlgassist.clutch.FallSession;
import com.mlgassist.clutch.LandingAnalysis;
import com.mlgassist.clutch.LandingScanner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class AutoClutch {
  private static final float MIN_FALL_DISTANCE = 3.0f;
  private static final double MIN_FALL_VELOCITY = -0.08;
  private static final int MAX_FAIL_TICKS = 12;

  private static final FallSession session = new FallSession();

  private static int blockPlacementAttemptsThisTick = 0;
  private static final int MAX_PLACEMENT_ATTEMPTS_PER_TICK = 1;

  private AutoClutch() {}

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(AutoClutch::tick);
  }

  private static void tick(MinecraftClient client) {
    if (client.player == null || client.world == null || client.interactionManager == null) {
      return;
    }

    ClientPlayerEntity player = client.player;

    ClutchExecutor.maceAttackAttemptsThisTick = 0;
    blockPlacementAttemptsThisTick = 0;

    if (!ClutchKeybinds.isEnabled()) {
      return;
    }

    if (player.getAbilities().flying || player.hasVehicle()) {
      return;
    }

    boolean knockbackClutchActive = checkAndExecuteKnockbackClutch(client);
    if (knockbackClutchActive) {
      return;
    }

    if (player.isOnGround() || player.fallDistance < 0.5f) {
      if (session.isActive()) {
        session.reset();
      }
      return;
    }

    double velocityY = player.getVelocity().y;
    if (player.fallDistance < MIN_FALL_DISTANCE || velocityY >= MIN_FALL_VELOCITY) {
      return;
    }

    if (!session.isActive()) {
      session.begin();
    }

    if (session.isSucceeded()) {
      return;
    }

    LandingAnalysis landing = LandingScanner.scan(client, player);
    session.setLanding(landing);

    boolean inNether = client.world.getRegistryKey() == World.NETHER;

    if (!session.isLocked()) {
      ClutchSelector.Selection selection =
          ClutchSelector.select(player, landing, inNether, session.getFailedType());
      if (selection == null) {
        return;
      }

      if (!ClutchTiming.shouldTrigger(selection.type(), landing.distance(), velocityY)) {
        return;
      }

      lockSelection(client, selection);
      if (!session.isLocked()) {
        return;
      }
    }

    boolean success = ClutchExecutor.execute(client, session);
    if (success) {
      session.markSucceeded();
      return;
    }

    if (session.shouldCountFailure()) {
      session.incrementFailTicks();
      if (session.getFailTicks() >= MAX_FAIL_TICKS && !session.isAlternateUsed()) {
        session.markAlternateUsed();
        ClutchSelector.Selection alternate =
            ClutchSelector.select(player, landing, inNether, session.getFailedType());
        if (alternate != null
            && ClutchTiming.shouldTrigger(alternate.type(), landing.distance(), velocityY)) {
          lockSelection(client, alternate);
        }
      }
    }
  }

  private static boolean checkAndExecuteKnockbackClutch(MinecraftClient client) {
    ClientPlayerEntity player = client.player;
    World world = client.world;
    if (player == null || world == null || client.interactionManager == null) {
      return false;
    }

    boolean isHighFall = player.fallDistance >= MIN_FALL_DISTANCE && player.getVelocity().y < MIN_FALL_VELOCITY;
    if (isHighFall) {
      return false;
    }

    double vx = player.getVelocity().x;
    double vz = player.getVelocity().z;
    double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);

    if (horizontalSpeed <= 0.15) {
      return false;
    }

    if (!isNoBlockBelow(world, player) && !isNearEdge(world, player)) {
      return false;
    }

    int blockSlot = ClutchInventory.findSolidBlockSlot(player);
    if (blockSlot == -1) {
      return false;
    }

    double nextX = player.getX() + vx;
    double nextZ = player.getZ() + vz;

    BlockPos[] candidates = new BlockPos[] {
        BlockPos.ofFloored(nextX, player.getY() - 1.0, nextZ),
        BlockPos.ofFloored(player.getX(), player.getY() - 1.0, player.getZ()),
        BlockPos.ofFloored(nextX, player.getY() - 2.0, nextZ),
        BlockPos.ofFloored(player.getX(), player.getY() - 2.0, player.getZ())
    };

    BlockPos targetPlacePos = null;
    Direction targetFace = null;
    BlockPos neighborPos = null;

    for (BlockPos placePos : candidates) {
      if (!world.getBlockState(placePos).isReplaceable()) {
        continue;
      }
      for (Direction dir : Direction.values()) {
        BlockPos neighbor = placePos.offset(dir);
        if (isSolid(world, neighbor)) {
          targetPlacePos = placePos;
          targetFace = dir.getOpposite();
          neighborPos = neighbor;
          break;
        }
      }
      if (targetPlacePos != null) {
        break;
      }
    }

    if (targetPlacePos == null) {
      return false;
    }

    if (blockSlot < 9) {
      if (player.getInventory().getSelectedSlot() != blockSlot) {
        ClutchInventory.selectHotbarSlot(player, blockSlot);
        return true;
      }
    } else {
      int hotbarSlot = ClutchInventory.ensureHotbarSlot(client, blockSlot);
      if (hotbarSlot == -1) {
        return false;
      }
      ClutchInventory.selectHotbarSlot(player, hotbarSlot);
      return true;
    }

    if (blockPlacementAttemptsThisTick >= MAX_PLACEMENT_ATTEMPTS_PER_TICK) {
      return false;
    }

    if (Math.abs(vx) > 0.01 || Math.abs(vz) > 0.01) {
      float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
      player.setYaw(yaw);
      player.setPitch(82.0f);
    }

    Vec3d hitVec = Vec3d.ofCenter(neighborPos).add(
        targetFace.getVector().getX() * 0.5,
        targetFace.getVector().getY() * 0.5,
        targetFace.getVector().getZ() * 0.5
    );

    BlockHitResult hitResult = new BlockHitResult(hitVec, targetFace, neighborPos, false);
    blockPlacementAttemptsThisTick++;
    ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
    if (result.isAccepted()) {
      player.swingHand(Hand.MAIN_HAND);
      return true;
    }

    return false;
  }

  private static boolean isSolid(World world, BlockPos pos) {
    return !world.getBlockState(pos).isAir() && !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
  }

  private static boolean isNoBlockBelow(World world, PlayerEntity player) {
    BlockPos pos = player.getBlockPos();
    for (int i = 0; i <= 3; i++) {
      if (!world.getBlockState(pos.down(i)).isAir()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isNearEdge(World world, PlayerEntity player) {
    Vec3d velocity = player.getVelocity();
    double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    if (horizontalSpeed < 0.05) {
      return false;
    }
    double dirX = velocity.x / horizontalSpeed;
    double dirZ = velocity.z / horizontalSpeed;
    BlockPos aheadPos = BlockPos.ofFloored(player.getX() + dirX * 0.8, player.getY() - 0.5, player.getZ() + dirZ * 0.8);
    return world.getBlockState(aheadPos).isAir() && world.getBlockState(aheadPos.down()).isAir();
  }

  private static void lockSelection(MinecraftClient client, ClutchSelector.Selection selection) {
    int hotbarSlot = -1;
    if (selection.inventorySlot() != -1) {
      hotbarSlot = ClutchInventory.ensureHotbarSlot(client, selection.inventorySlot());
      if (hotbarSlot == -1) {
        return;
      }
    }

    session.lock(selection.type(), selection.inventorySlot(), hotbarSlot);
  }
}
