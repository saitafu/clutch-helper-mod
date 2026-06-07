package com.mlgassist.clutch;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum ClutchType {
  WATER_BUCKET(0),
  COBWEB(1),
  SLIME_BLOCK(2),
  HAY_BLOCK(3),
  HONEY_BLOCK(4),
  VINE(5),
  LADDER(6),
  BOAT(7),
  MINECART(8),
  POWDER_SNOW_BUCKET(9),
  WIND_CHARGE(10),
  ENDER_PEARL(11),
  MACE(12),
  TRIDENT(13),
  MOB_CLUTCH(14);

  private final int priority;

  ClutchType(int priority) {
    this.priority = priority;
  }

  public int priority() {
    return priority;
  }

  public boolean matches(Item item) {
    return switch (this) {
      case WATER_BUCKET -> item == Items.WATER_BUCKET;
      case COBWEB -> item == Items.COBWEB;
      case SLIME_BLOCK -> item == Items.SLIME_BLOCK;
      case HAY_BLOCK -> item == Items.HAY_BLOCK;
      case HONEY_BLOCK -> item == Items.HONEY_BLOCK;
      case VINE -> isVineItem(item);
      case LADDER -> item == Items.LADDER;
      case BOAT -> isBoat(item);
      case MINECART ->
          item == Items.MINECART || item == Items.CHEST_MINECART || item == Items.TNT_MINECART;
      case POWDER_SNOW_BUCKET -> item == Items.POWDER_SNOW_BUCKET;
      case WIND_CHARGE -> item == Items.WIND_CHARGE;
      case ENDER_PEARL -> item == Items.ENDER_PEARL;
      case MACE -> item == Items.MACE;
      case TRIDENT -> item == Items.TRIDENT;
      case MOB_CLUTCH -> false;
    };
  }

  public static boolean isBoat(Item item) {
    return item == Items.OAK_BOAT
        || item == Items.SPRUCE_BOAT
        || item == Items.BIRCH_BOAT
        || item == Items.JUNGLE_BOAT
        || item == Items.ACACIA_BOAT
        || item == Items.DARK_OAK_BOAT
        || item == Items.MANGROVE_BOAT
        || item == Items.CHERRY_BOAT
        || item == Items.BAMBOO_RAFT
        || item == Items.PALE_OAK_BOAT
        || item == Items.OAK_CHEST_BOAT
        || item == Items.SPRUCE_CHEST_BOAT
        || item == Items.BIRCH_CHEST_BOAT
        || item == Items.JUNGLE_CHEST_BOAT
        || item == Items.ACACIA_CHEST_BOAT
        || item == Items.DARK_OAK_CHEST_BOAT
        || item == Items.MANGROVE_CHEST_BOAT
        || item == Items.CHERRY_CHEST_BOAT
        || item == Items.BAMBOO_CHEST_RAFT
        || item == Items.PALE_OAK_CHEST_BOAT;
  }

  private static boolean isVineItem(Item item) {
    return item == Items.VINE || item == Items.WEEPING_VINES || item == Items.TWISTING_VINES;
  }

  public boolean isBucketPlacement() {
    return this == WATER_BUCKET || this == POWDER_SNOW_BUCKET;
  }

  public boolean isEntityInteraction() {
    return this == BOAT || this == MINECART || this == MOB_CLUTCH;
  }

  public boolean isProjectileUse() {
    return this == WIND_CHARGE || this == ENDER_PEARL;
  }

  public boolean needsSidePlacement() {
    return this == VINE || this == LADDER;
  }

  public boolean requiresWeaponEquip() {
    return this == MACE || this == TRIDENT;
  }

  public boolean allowedInNether() {
    return this != WATER_BUCKET && this != POWDER_SNOW_BUCKET;
  }

  public static ClutchType fromStack(ItemStack stack) {
    if (stack.isEmpty()) {
      return null;
    }
    Item item = stack.getItem();
    for (ClutchType type : values()) {
      if (type.matches(item)) {
        return type;
      }
    }
    return null;
  }
}
