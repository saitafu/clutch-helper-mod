package com.mlgassist.clutch;

public final class ClutchTiming {
  public static final double BLOCK_TRIGGER_TICKS = 2.0;
  public static final double PROJECTILE_TRIGGER_TICKS = 5.0;
  public static final double MACE_TRIGGER_TICKS = 4.0;
  public static final double ENTITY_TRIGGER_TICKS = 2.5;

  private static final double MIN_VELOCITY = 0.05;

  private ClutchTiming() {}

  public static double computeTimeToImpact(double distance, double velocityY) {
    double absVel = Math.abs(velocityY);
    if (absVel < MIN_VELOCITY) {
      return Double.MAX_VALUE;
    }
    return distance / absVel;
  }

  public static double triggerThreshold(ClutchType type) {
    return switch (type) {
      case ENDER_PEARL, WIND_CHARGE -> PROJECTILE_TRIGGER_TICKS;
      case MACE, MOB_CLUTCH -> MACE_TRIGGER_TICKS;
      case BOAT, MINECART -> ENTITY_TRIGGER_TICKS;
      default -> BLOCK_TRIGGER_TICKS;
    };
  }

  public static boolean shouldTrigger(ClutchType type, double distance, double velocityY) {
    return computeTimeToImpact(distance, velocityY) <= triggerThreshold(type);
  }
}
