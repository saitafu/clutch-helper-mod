package com.mlgassist.clutch;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class LandingScanner {
    private static final double SCAN_RADIUS = 2.5;
    private static final double ENTITY_SEARCH_RADIUS = 4.0;
    private static final int MAX_SCAN_DEPTH = 64;

    private LandingScanner() {
    }

    public static LandingAnalysis scan(MinecraftClient client, PlayerEntity player) {
        World world = client.world;
        Vec3d velocity = player.getVelocity();
        Vec3d start = player.getEyePos();

        double fallTicks = Math.max(1.0, Math.abs(velocity.y) * 2.0);
        Vec3d predicted = start.add(velocity.x * fallTicks, velocity.y * fallTicks, velocity.z * fallTicks);
        Vec3d end = predicted.add(0.0, -MAX_SCAN_DEPTH, 0.0);

        BlockHitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        BlockPos landingPos = hit.getBlockPos();
        if (!world.getBlockState(landingPos).isAir()) {
            landingPos = landingPos.up();
        }

        double distance = player.getY() - landingPos.getY();
        if (distance < 0.0) {
            distance = 0.0;
        }

        BlockPos groundPos = landingPos.down();
        boolean solidGround = isSolid(world, groundPos);
        boolean leaves = isLeafBlock(world.getBlockState(groundPos).getBlock());
        boolean uneven = isUneven(world, landingPos);
        boolean caveBelow = isCaveBelow(world, groundPos);

        Vec3d landingCenter = Vec3d.ofCenter(landingPos);
        Entity mountable = findMountableEntity(world, player, landingCenter);
        Entity mob = findMobEntity(world, player, landingCenter);
        Entity attackTarget = findAttackTarget(world, player, 6.0);

        return new LandingAnalysis(
                landingPos,
                landingCenter,
                distance,
                solidGround,
                leaves,
                uneven,
                caveBelow,
                mountable,
                mob,
                attackTarget
        );
    }

    private static boolean isSolid(World world, BlockPos pos) {
        return !world.getBlockState(pos).isAir() && !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isLeafBlock(Block block) {
        return block == Blocks.OAK_LEAVES
                || block == Blocks.SPRUCE_LEAVES
                || block == Blocks.BIRCH_LEAVES
                || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES
                || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.MANGROVE_LEAVES
                || block == Blocks.CHERRY_LEAVES
                || block == Blocks.AZALEA_LEAVES
                || block == Blocks.FLOWERING_AZALEA_LEAVES
                || block == Blocks.PALE_OAK_LEAVES
                || block.getDefaultState().isIn(BlockTags.LEAVES);
    }

    private static boolean isUneven(World world, BlockPos center) {
        int baseY = center.getY();
        int minY = baseY;
        int maxY = baseY;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos check = center.add(dx, -1, dz);
                if (isSolid(world, check)) {
                    int surfaceY = check.getY() + 1;
                    minY = Math.min(minY, surfaceY);
                    maxY = Math.max(maxY, surfaceY);
                }
            }
        }

        return maxY - minY > 1;
    }

    private static boolean isCaveBelow(World world, BlockPos groundPos) {
        int airBelow = 0;
        for (int i = 1; i <= 4; i++) {
            if (world.getBlockState(groundPos.down(i)).isAir()) {
                airBelow++;
            } else {
                break;
            }
        }
        return airBelow >= 2;
    }

    private static Entity findMountableEntity(World world, PlayerEntity player, Vec3d landingCenter) {
        Box box = new Box(landingCenter, landingCenter).expand(SCAN_RADIUS, 2.0, SCAN_RADIUS);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : world.getOtherEntities(player, box, LandingScanner::isMountable)) {
            double dist = entity.squaredDistanceTo(landingCenter);
            if (dist < bestDistance) {
                bestDistance = dist;
                best = entity;
            }
        }

        return best;
    }

    private static Entity findMobEntity(World world, PlayerEntity player, Vec3d landingCenter) {
        Box box = new Box(landingCenter, landingCenter).expand(ENTITY_SEARCH_RADIUS, 3.0, ENTITY_SEARCH_RADIUS);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : world.getOtherEntities(player, box, e -> e instanceof LivingEntity && !isMountable(e))) {
            double dist = entity.squaredDistanceTo(landingCenter);
            if (dist < bestDistance) {
                bestDistance = dist;
                best = entity;
            }
        }

        return best;
    }

    public static boolean isMountable(Entity entity) {
        return entity instanceof BoatEntity || entity instanceof AbstractMinecartEntity;
    }

    private static Entity findAttackTarget(World world, PlayerEntity player, double range) {
        Box box = player.getBoundingBox().expand(range);
        Entity best = null;
        double bestDistance = range * range;

        for (Entity entity :
                world.getOtherEntities(
                        player,
                        box,
                        e -> e instanceof LivingEntity && !isMountable(e) && e.isAlive())) {
            double dist = entity.squaredDistanceTo(player);
            if (dist <= bestDistance) {
                bestDistance = dist;
                best = entity;
            }
        }

        return best;
    }
}
