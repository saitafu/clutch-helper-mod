package com.mlgassist.clutch;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record LandingAnalysis(
    BlockPos landingPos,
    Vec3d landingCenter,
    double distance,
    boolean solidGround,
    boolean leaves,
    boolean uneven,
    boolean caveBelow,
    Entity mountableEntity,
    Entity mobEntity,
    Entity attackTarget) {
  public boolean prefersBlockClutch() {
    return leaves || uneven || caveBelow;
  }

  public boolean hasMountTarget() {
    return mountableEntity != null;
  }

  public boolean hasMobTarget() {
    return mobEntity != null;
  }

  public boolean hasAttackTarget() {
    return attackTarget != null;
  }
}
