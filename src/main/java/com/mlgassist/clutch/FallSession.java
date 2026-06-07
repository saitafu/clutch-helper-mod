package com.mlgassist.clutch;

public final class FallSession {
  private boolean active;
  private boolean locked;
  private boolean succeeded;
  private boolean alternateUsed;
  private boolean placementDone;
  private int failTicks;
  private int equipCooldown;
  private ClutchType lockedType;
  private ClutchType failedType;
  private int inventorySlot = -1;
  private int hotbarSlot = -1;
  private LandingAnalysis landing;

  public void begin() {
    active = true;
    locked = false;
    succeeded = false;
    alternateUsed = false;
    placementDone = false;
    failTicks = 0;
    equipCooldown = 0;
    lockedType = null;
    failedType = null;
    inventorySlot = -1;
    hotbarSlot = -1;
    landing = null;
  }

  public void reset() {
    active = false;
    locked = false;
    succeeded = false;
    alternateUsed = false;
    placementDone = false;
    failTicks = 0;
    equipCooldown = 0;
    lockedType = null;
    failedType = null;
    inventorySlot = -1;
    hotbarSlot = -1;
    landing = null;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isLocked() {
    return locked;
  }

  public boolean isSucceeded() {
    return succeeded;
  }

  public boolean isAlternateUsed() {
    return alternateUsed;
  }

  public boolean isPlacementDone() {
    return placementDone;
  }

  public int getFailTicks() {
    return failTicks;
  }

  public ClutchType getLockedType() {
    return lockedType;
  }

  public ClutchType getFailedType() {
    return failedType;
  }

  public int getInventorySlot() {
    return inventorySlot;
  }

  public int getHotbarSlot() {
    return hotbarSlot;
  }

  public LandingAnalysis getLanding() {
    return landing;
  }

  public void setLanding(LandingAnalysis landing) {
    this.landing = landing;
  }

  public void lock(ClutchType type, int inventorySlot, int hotbarSlot) {
    this.locked = true;
    this.lockedType = type;
    this.inventorySlot = inventorySlot;
    this.hotbarSlot = hotbarSlot;
    this.placementDone = false;
    this.failTicks = 0;
    this.equipCooldown = 0;
  }

  public void markPlacementDone() {
    this.placementDone = true;
  }

  public void markSucceeded() {
    this.succeeded = true;
  }

  public void incrementFailTicks() {
    failTicks++;
  }

  public void markAlternateUsed() {
    alternateUsed = true;
    failedType = lockedType;
    locked = false;
    lockedType = null;
    inventorySlot = -1;
    hotbarSlot = -1;
    placementDone = false;
    failTicks = 0;
    equipCooldown = 0;
  }

  public boolean shouldCountFailure() {
    if (lockedType == ClutchType.BOAT || lockedType == ClutchType.MINECART) {
      return !placementDone;
    }
    return true;
  }

  public boolean isEquipWaiting() {
    return equipCooldown > 0;
  }

  public void startEquipWait() {
    equipCooldown = 1;
  }

  public void tickEquipWait() {
    if (equipCooldown > 0) {
      equipCooldown--;
    }
  }
}
