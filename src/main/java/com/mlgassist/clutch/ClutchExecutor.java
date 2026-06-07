package com.mlgassist.clutch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ClutchExecutor {
  private static final double RAYCAST_DISTANCE = 6.0;
  private static final int ATTEMPTS_PER_TICK = 2;
  public static int maceAttackAttemptsThisTick = 0;

  private ClutchExecutor() {}

  public static boolean execute(MinecraftClient client, FallSession session) {
    ClientPlayerEntity player = client.player;
    if (player == null || client.interactionManager == null || session.getLockedType() == null) {
      return false;
    }

    if (player.hasVehicle()) {
      return true;
    }

    LandingAnalysis landing = session.getLanding();
    if (landing == null) {
      return false;
    }

    ClutchType type = session.getLockedType();

    session.tickEquipWait();
    if (session.isEquipWaiting()) {
      return false;
    }

    if (type == ClutchType.MOB_CLUTCH) {
      return executeMobClutch(client, landing);
    }

    if (!ensureCorrectSlot(client, session, type)) {
      return false;
    }

    Vec3d aimTarget = resolveAimTarget(type, landing, player);
    aimAt(player, aimTarget);

    return switch (type) {
      case WATER_BUCKET, POWDER_SNOW_BUCKET -> placeBucket(client, session, landing);
      case BOAT -> placeBoatAndMount(client, session, landing);
      case MINECART -> mountOrPlaceMinecart(client, session, landing);
      case WIND_CHARGE, ENDER_PEARL -> useProjectile(client, type, landing);
      case MACE -> executeMaceClutch(client, session, landing);
      case TRIDENT -> useTrident(client);
      default -> placeBlock(client, session, landing, type);
    };
  }

  private static boolean ensureCorrectSlot(
      MinecraftClient client, FallSession session, ClutchType type) {
    ClientPlayerEntity player = client.player;
    if (player == null) {
      return false;
    }

    if (session.getHotbarSlot() == -1) {
      return !type.requiresWeaponEquip();
    }

    int desiredSlot = session.getHotbarSlot();
    if (player.getInventory().getSelectedSlot() != desiredSlot) {
      ClutchInventory.selectHotbarSlot(player, desiredSlot);
      session.startEquipWait();
      return false;
    }

    if (type.requiresWeaponEquip()) {
      ItemStack held = player.getMainHandStack();
      if (!type.matches(held.getItem())) {
        session.startEquipWait();
        return false;
      }
    }

    return true;
  }

  private static Vec3d resolveAimTarget(ClutchType type, LandingAnalysis landing, PlayerEntity player) {
    if (type.isEntityInteraction() && landing.mountableEntity() != null) {
      return landing.mountableEntity().getBoundingBox().getCenter();
    }

    if (type == ClutchType.MACE || type == ClutchType.MOB_CLUTCH) {
      Entity target = landing.attackTarget() != null ? landing.attackTarget() : landing.mobEntity();
      if (target != null) {
        return target.getBoundingBox().getCenter();
      }
    }

    if (type.isProjectileUse()) {
      return projectileAimTarget(player, landing);
    }

    if (type == ClutchType.TRIDENT) {
      return projectileAimTarget(player, landing);
    }

    if (type.needsSidePlacement()) {
      BlockPos ground = landing.landingPos().down();
      return Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0);
    }

    return landing.landingCenter();
  }

  private static Vec3d projectileAimTarget(PlayerEntity player, LandingAnalysis landing) {
    Vec3d eye = player.getEyePos();
    Vec3d towardLanding = landing.landingCenter().subtract(eye);
    double horizontal = Math.sqrt(towardLanding.x * towardLanding.x + towardLanding.z * towardLanding.z);
    if (horizontal < 0.001) {
      return eye.add(0.0, -2.0, 0.0);
    }
    double pitchRadians = Math.toRadians(65.0);
    double throwDistance = Math.max(horizontal, 1.0);
    return eye.add(
        towardLanding.x / horizontal * throwDistance * Math.cos(pitchRadians),
        -Math.sin(pitchRadians) * throwDistance,
        towardLanding.z / horizontal * throwDistance * Math.cos(pitchRadians));
  }

  public static void aimAt(PlayerEntity player, Vec3d target) {
    Vec3d eye = player.getEyePos();
    Vec3d delta = target.subtract(eye);
    double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
    float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    player.setYaw(yaw);
  }

  private static boolean placeBucket(MinecraftClient client, FallSession session, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;
    World world = client.world;
    if (world == null) {
      return false;
    }

    BlockPos targetPos = landing.landingPos();
    if (isWaterAt(world, targetPos)) {
      return true;
    }

    for (int attempt = 0; attempt < ATTEMPTS_PER_TICK; attempt++) {
      HitResult rayHit = player.raycast(RAYCAST_DISTANCE, 1.0f, false);
      if (rayHit.getType() == HitResult.Type.BLOCK) {
        BlockHitResult rayBlock = (BlockHitResult) rayHit;
        BlockHitResult topFace =
            new BlockHitResult(rayBlock.getPos(), Direction.UP, rayBlock.getBlockPos(), false);

        ActionResult result =
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, topFace);
        if (result.isAccepted()) {
          player.swingHand(Hand.MAIN_HAND);
          return true;
        }
      }

      BlockPos placePos = landing.landingPos().down();
      BlockHitResult blockHit =
          new BlockHitResult(
              Vec3d.ofCenter(placePos).add(0.0, 0.5, 0.0), Direction.UP, placePos, false);

      ActionResult blockResult =
          client.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
      if (blockResult.isAccepted()) {
        player.swingHand(Hand.MAIN_HAND);
        return true;
      }

      ActionResult airResult = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      if (airResult.isAccepted()) {
        player.swingHand(Hand.MAIN_HAND);
        return true;
      }
    }

    return false;
  }

  private static boolean isWaterAt(World world, BlockPos pos) {
    return world.getFluidState(pos).isIn(FluidTags.WATER)
        || world.getFluidState(pos).getFluid() == Fluids.WATER;
  }

  private static boolean placeBlock(
      MinecraftClient client, FallSession session, LandingAnalysis landing, ClutchType type) {
    ClientPlayerEntity player = client.player;

    for (int attempt = 0; attempt < ATTEMPTS_PER_TICK; attempt++) {
      HitResult hit = player.raycast(RAYCAST_DISTANCE, 1.0f, false);
      if (hit.getType() == HitResult.Type.BLOCK) {
        BlockHitResult blockHit = (BlockHitResult) hit;
        ActionResult result =
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        if (result.isAccepted()) {
          player.swingHand(Hand.MAIN_HAND);
          session.markPlacementDone();
          return true;
        }
      }

      if (type.needsSidePlacement()) {
        for (Direction face :
            new Direction[] {
              Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
            }) {
          BlockPos adjacent = landing.landingPos().offset(face.getOpposite());
          BlockHitResult sideHit =
              new BlockHitResult(
                  Vec3d.ofCenter(adjacent)
                      .add(
                          face.getOffsetX() * 0.5,
                          face.getOffsetY() * 0.5,
                          face.getOffsetZ() * 0.5),
                  face,
                  adjacent,
                  false);
          ActionResult sideResult =
              client.interactionManager.interactBlock(player, Hand.MAIN_HAND, sideHit);
          if (sideResult.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            session.markPlacementDone();
            return true;
          }
        }
      }

      BlockPos below = landing.landingPos().down();
      BlockHitResult fallback =
          new BlockHitResult(
              Vec3d.ofCenter(below).add(0.0, 0.5, 0.0), Direction.UP, below, false);
      ActionResult fallbackResult =
          client.interactionManager.interactBlock(player, Hand.MAIN_HAND, fallback);
      if (fallbackResult.isAccepted()) {
        player.swingHand(Hand.MAIN_HAND);
        session.markPlacementDone();
        return true;
      }
    }

    return false;
  }

  private static boolean placeBoatAndMount(
      MinecraftClient client, FallSession session, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;

    Entity existing = landing.mountableEntity();
    if (existing != null && tryMountEntity(client, existing)) {
      return true;
    }

    BoatEntity nearby = raycastBoat(client);
    if (nearby != null && tryMountEntity(client, nearby)) {
      return true;
    }

    if (!session.isPlacementDone() && session.getHotbarSlot() != -1) {
      boolean placed = placeBlock(client, session, landing, ClutchType.BOAT);
      if (placed) {
        session.markPlacementDone();
      }
    }

    BoatEntity placedBoat = raycastBoat(client);
    if (placedBoat != null) {
      return tryMountEntity(client, placedBoat);
    }

    placedBoat = findNearbyBoat(client, landing.landingCenter());
    if (placedBoat != null) {
      return tryMountEntity(client, placedBoat);
    }

    return player.hasVehicle();
  }

  private static boolean mountOrPlaceMinecart(
      MinecraftClient client, FallSession session, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;

    Entity existing = landing.mountableEntity();
    if (existing != null && tryMountEntity(client, existing)) {
      return true;
    }

    if (!session.isPlacementDone() && session.getHotbarSlot() != -1) {
      boolean placed = placeBlock(client, session, landing, ClutchType.MINECART);
      if (placed) {
        session.markPlacementDone();
      }
    }

    HitResult hit = player.raycast(RAYCAST_DISTANCE, 1.0f, false);
    if (hit instanceof EntityHitResult entityHit) {
      return tryMountEntity(client, entityHit.getEntity());
    }

    if (existing != null) {
      return tryMountEntity(client, existing);
    }

    return player.hasVehicle();
  }

  private static BoatEntity raycastBoat(MinecraftClient client) {
    ClientPlayerEntity player = client.player;
    if (player == null) {
      return null;
    }

    HitResult hit = player.raycast(RAYCAST_DISTANCE, 1.0f, false);
    if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof BoatEntity boat) {
      return boat;
    }

    return null;
  }

  private static BoatEntity findNearbyBoat(MinecraftClient client, Vec3d center) {
    if (client.world == null || client.player == null) {
      return null;
    }

    BoatEntity closest = null;
    double closestDist = 16.0;

    for (Entity entity :
        client.world.getOtherEntities(client.player, client.player.getBoundingBox().expand(5.0))) {
      if (entity instanceof BoatEntity boat) {
        double dist = boat.squaredDistanceTo(center);
        if (dist < closestDist) {
          closestDist = dist;
          closest = boat;
        }
      }
    }

    return closest;
  }

  private static boolean tryMountEntity(MinecraftClient client, Entity entity) {
    ClientPlayerEntity player = client.player;
    aimAt(player, entity.getBoundingBox().getCenter());

    ActionResult result = client.interactionManager.interactEntity(player, entity, Hand.MAIN_HAND);
    if (result.isAccepted()) {
      player.swingHand(Hand.MAIN_HAND);
      return true;
    }

    HitResult hit = player.raycast(RAYCAST_DISTANCE, 1.0f, false);
    if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == entity) {
      ActionResult rayMount =
          client.interactionManager.interactEntityAtLocation(
              player, entity, entityHit, Hand.MAIN_HAND);
      if (rayMount.isAccepted()) {
        player.swingHand(Hand.MAIN_HAND);
        return true;
      }
    }

    return player.hasVehicle();
  }

  private static boolean useProjectile(
      MinecraftClient client, ClutchType type, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;
    aimAt(player, projectileAimTarget(player, landing));

    for (int attempt = 0; attempt < ATTEMPTS_PER_TICK; attempt++) {
      ActionResult result = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      if (result.isAccepted()) {
        player.swingHand(Hand.MAIN_HAND);
        return true;
      }
    }

    return false;
  }

  private static boolean useTrident(MinecraftClient client) {
    ClientPlayerEntity player = client.player;
    ActionResult use = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
    if (use.isAccepted()) {
      player.swingHand(Hand.MAIN_HAND);
      return true;
    }
    return false;
  }

  private static boolean executeMaceClutch(
      MinecraftClient client, FallSession session, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;
    Entity target = landing.attackTarget();
    if (target == null || !target.isAlive()) {
      return false;
    }

    int maceSlot = ClutchInventory.findSlot(player, ClutchType.MACE);
    if (maceSlot == -1) {
      return false;
    }

    int hotbarSlot = session.getHotbarSlot();
    if (hotbarSlot == -1 || !ClutchType.MACE.matches(player.getInventory().getStack(hotbarSlot).getItem())) {
      hotbarSlot = ClutchInventory.ensureHotbarSlot(client, maceSlot);
      if (hotbarSlot == -1) {
        return false;
      }
      session.lock(ClutchType.MACE, maceSlot, hotbarSlot);
    }

    if (player.getInventory().getSelectedSlot() != hotbarSlot) {
      ClutchInventory.selectHotbarSlot(player, hotbarSlot);
      session.startEquipWait();
      return false;
    }

    if (session.isEquipWaiting()) {
      return false;
    }

    if (!isMaceEquipped(player)) {
      session.startEquipWait();
      return false;
    }

    double eyeDistSq = target.squaredDistanceTo(player.getEyePos());
    double maxReach = 3.8;
    if (eyeDistSq > maxReach * maxReach) {
      return false;
    }

    aimAt(player, target.getBoundingBox().getCenter());

    if (maceAttackAttemptsThisTick < 1) {
      maceAttackAttemptsThisTick++;
      client.interactionManager.attackEntity(player, target);
      player.swingHand(Hand.MAIN_HAND);
      return true;
    }

    return false;
  }

  private static boolean isMaceEquipped(ClientPlayerEntity player) {
    return ClutchType.MACE.matches(player.getMainHandStack().getItem());
  }

  private static boolean executeMobClutch(MinecraftClient client, LandingAnalysis landing) {
    ClientPlayerEntity player = client.player;
    Entity mob = landing.attackTarget() != null ? landing.attackTarget() : landing.mobEntity();
    if (mob == null || !mob.isAlive()) {
      return false;
    }

    aimAt(player, mob.getBoundingBox().getCenter());
    client.interactionManager.attackEntity(player, mob);
    player.swingHand(Hand.MAIN_HAND);
    return true;
  }
}
