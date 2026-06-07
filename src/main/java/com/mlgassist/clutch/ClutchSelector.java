package com.mlgassist.clutch;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClutchSelector {
  private ClutchSelector() {}

  public static Selection select(
      PlayerEntity player, LandingAnalysis landing, boolean inNether, ClutchType exclude) {
    if (landing.hasMountTarget()) {
      Selection mountSelection = resolveMountSelection(player, landing);
      if (mountSelection != null && mountSelection.type() != exclude) {
        return mountSelection;
      }
    }

    List<ClutchType> order = buildPriorityOrder(landing, inNether);

    for (ClutchType type : order) {
      if (type == exclude) {
        continue;
      }

      if (type == ClutchType.MACE) {
        if (!landing.hasAttackTarget()) {
          continue;
        }
        int maceSlot = ClutchInventory.findSlot(player, ClutchType.MACE);
        if (maceSlot != -1) {
          return new Selection(ClutchType.MACE, maceSlot, type.priority());
        }
        continue;
      }

      if (type == ClutchType.MOB_CLUTCH) {
        if (landing.hasMobTarget() || landing.hasAttackTarget()) {
          return new Selection(ClutchType.MOB_CLUTCH, -1, type.priority());
        }
        continue;
      }

      int slot = ClutchInventory.findSlot(player, type);
      if (slot != -1) {
        return new Selection(type, slot, type.priority());
      }
    }

    return null;
  }

  private static Selection resolveMountSelection(PlayerEntity player, LandingAnalysis landing) {
    if (landing.mountableEntity() instanceof BoatEntity) {
      int boatSlot = ClutchInventory.findSlot(player, ClutchType.BOAT);
      return new Selection(ClutchType.BOAT, boatSlot, ClutchType.BOAT.priority() - 2);
    }

    if (landing.mountableEntity() instanceof AbstractMinecartEntity) {
      int cartSlot = ClutchInventory.findSlot(player, ClutchType.MINECART);
      return new Selection(ClutchType.MINECART, cartSlot, ClutchType.MINECART.priority() - 2);
    }

    return null;
  }

  private static List<ClutchType> buildPriorityOrder(LandingAnalysis landing, boolean inNether) {
    List<ClutchType> types = new ArrayList<>(List.of(ClutchType.values()));
    types.sort(Comparator.comparingInt(ClutchType::priority));

    if (inNether) {
      types.remove(ClutchType.WATER_BUCKET);
      types.remove(ClutchType.POWDER_SNOW_BUCKET);
      moveEarlier(types, ClutchType.COBWEB);
      moveEarlier(types, ClutchType.VINE);
      moveEarlier(types, ClutchType.ENDER_PEARL);
    }

    if (landing.prefersBlockClutch()) {
      types.remove(ClutchType.WATER_BUCKET);
      moveEarlier(types, ClutchType.COBWEB);
      moveEarlier(types, ClutchType.VINE);
    } else if (landing.solidGround() && !inNether) {
      moveEarlier(types, ClutchType.WATER_BUCKET);
    }

    return types;
  }

  private static void moveEarlier(List<ClutchType> types, ClutchType type) {
    if (!types.contains(type)) {
      return;
    }
    types.remove(type);
    types.add(0, type);
  }

  public record Selection(ClutchType type, int inventorySlot, int sortKey) {}
}
