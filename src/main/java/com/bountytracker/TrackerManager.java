package com.bountytracker;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;

/**
 * Handles the tracker compass. Everything is stored on the compass item itself
 * (custom data), so it survives restarts and needs no client mod.
 * A vanilla compass with a LodestoneTracker component points at that position,
 * and an action bar message shows the name, distance and a direction arrow.
 */
public final class TrackerManager {
    private static final String KEY_TARGET = "bt_target";
    private static final String KEY_NAME = "bt_name";
    private static final String KEY_DEATHS = "bt_deaths";
    /** Marks the reusable Player Tracker compass. */
    private static final String KEY_REUSABLE = "bt_compass";
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private TrackerManager() {}

    private static int deaths(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
    }

    public static ItemStack createCompass(ServerPlayer target) {
        String name = target.getName().getString();
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.ITEM_NAME,
                Component.literal("Player Tracker").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Tracking: " + name).withStyle(ChatFormatting.GRAY))));

        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TARGET, target.getStringUUID());
        tag.putString(KEY_NAME, name);
        tag.putInt(KEY_DEATHS, deaths(target));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition())), false));
        return stack;
    }

    /** Returns the tag if this stack is a tracker compass, else null. */
    private static CompoundTag readTag(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.COMPASS)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return (tag.contains(KEY_TARGET) || tag.contains(KEY_REUSABLE)) ? tag : null;
    }

    // ---------------------------------------------------------------- Player Tracker

    /** The reusable compass an admin can hand out. */
    public static ItemStack createTrackingCompass() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_REUSABLE, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        styleReusable(stack, null);
        return stack;
    }

    private static void styleReusable(ItemStack stack, String trackedName) {
        stack.set(DataComponents.ITEM_NAME,
                Component.literal(trackedName == null ? "Tracking Compass" : "Player Tracker")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Component line2 = trackedName == null
                ? Component.literal("Not tracking anyone").withStyle(ChatFormatting.GRAY)
                : Component.literal("Tracking: " + trackedName).withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                line2,
                Component.literal("Right-click: choose player").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Sneak + right-click: stop tracking").withStyle(ChatFormatting.DARK_GRAY))));
    }

    private static boolean isReusable(CompoundTag tag) {
        return tag.contains(KEY_REUSABLE);
    }

    /** Opens a chest-style player picker with each online player's skin head. */
    public static void showPlayerSelector(ServerPlayer holder) {
        MinecraftServer server = holder.level().getServer();
        List<ServerPlayer> candidates = server.getPlayerList().getPlayers().stream()
                .filter(p -> p != holder)
                .sorted(Comparator.comparing(p -> p.getName().getString().toLowerCase()))
                .toList();
        if (candidates.isEmpty()) {
            holder.sendSystemMessage(Component.literal("No other players are online.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int rows = Math.min(6, (candidates.size() + 8) / 9);
        SimpleContainer contents = new SimpleContainer(rows * 9);
        java.util.Map<Integer, UUID> targets = new java.util.HashMap<>();
        for (int i = 0; i < candidates.size() && i < contents.getContainerSize(); i++) {
            ServerPlayer candidate = candidates.get(i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(candidate.getGameProfile()));
            head.set(DataComponents.ITEM_NAME, Component.literal(candidate.getName().getString())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            head.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Click to track this player").withStyle(ChatFormatting.GRAY))));
            contents.setItem(i, head);
            targets.put(i, candidate.getUUID());
        }
        holder.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, player) ->
                new PlayerPickerMenu(id, inventory, contents, targets),
                Component.literal("Select a player to track")));
    }

    private static final class PlayerPickerMenu extends ChestMenu {
        private final java.util.Map<Integer, UUID> targets;

        PlayerPickerMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                         SimpleContainer contents, java.util.Map<Integer, UUID> targets) {
            super(switch (contents.getContainerSize() / 9) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                default -> MenuType.GENERIC_9x6;
            }, id, inventory, contents, contents.getContainerSize() / 9);
            this.targets = targets;
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            if (player instanceof ServerPlayer holder && targets.containsKey(slot)) {
                ServerPlayer target = holder.level().getServer().getPlayerList().getPlayer(targets.get(slot));
                if (target != null) selectTarget(holder, target);
                else holder.sendSystemMessage(Component.literal("That player is no longer online.")
                        .withStyle(ChatFormatting.RED));
                holder.closeContainer();
                return;
            }
            super.clicked(slot, button, clickType, player);
        }
    }

    /** Selects a target on the player's reusable Tracking Compass. */
    public static boolean selectTarget(ServerPlayer holder, ServerPlayer target) {
        if (holder == target) {
            holder.sendSystemMessage(Component.literal("You cannot track yourself.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        Inventory inv = holder.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            CompoundTag tag = readTag(stack);
            if (tag == null || !isReusable(tag)) continue;

            String name = target.getName().getString();
            tag.putString(KEY_TARGET, target.getStringUUID());
            tag.putString(KEY_NAME, name);
            tag.putInt(KEY_DEATHS, deaths(target));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                    Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition())), false));
            styleReusable(stack, name);
            holder.sendSystemMessage(Component.literal("Now tracking ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            return true;
        }
        holder.sendSystemMessage(Component.literal("You need a Tracking Compass to select a player.")
                .withStyle(ChatFormatting.RED));
        return false;
    }

    /** Called when a player right-clicks with an item. */
    public static boolean handleUse(ServerPlayer holder, ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null || !isReusable(tag)) return false;
        if (holder.isShiftKeyDown()) {
            tag.remove(KEY_TARGET);
            tag.remove(KEY_NAME);
            tag.remove(KEY_DEATHS);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.remove(DataComponents.LODESTONE_TRACKER);
            styleReusable(stack, null);
            holder.sendSystemMessage(Component.literal("Stopped tracking.").withStyle(ChatFormatting.GRAY), true);
        } else {
            showPlayerSelector(holder);
        }
        return true;
    }

    /** Immediately removes every compass tracking a player killed by another player. */
    public static void onTargetDeath(ServerPlayer target) {
        MinecraftServer server = target.level().getServer();
        if (server == null) return;
        UUID targetId = target.getUUID();
        String name = target.getName().getString();
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
            Inventory inv = holder.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                CompoundTag tag = readTag(stack);
                if (tag == null || !targetId.toString().equals(tag.getStringOr(KEY_TARGET, ""))) continue;
                inv.setItem(i, ItemStack.EMPTY);
                holder.sendSystemMessage(Component.literal(name + " was killed by a player. Your Player Tracker disappeared.")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    /** Removes every tracker compass the player carries. Returns how many were removed. */
    public static int clear(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (readTag(inv.getItem(i)) != null) {
                inv.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        return removed;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return; // twice per second
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
            updateHolder(server, holder);
        }
    }

    private static void updateHolder(MinecraftServer server, ServerPlayer holder) {
        Inventory inv = holder.getInventory();
        boolean shown = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            CompoundTag tag = readTag(stack);
            if (tag == null) continue;

            // Reusable compass with nothing selected yet: just show a hint while held.
            if (!tag.contains(KEY_TARGET)) {
                if (!shown && (stack == holder.getMainHandItem() || stack == holder.getOffhandItem())) {
                    holder.sendSystemMessage(Component.literal("Right-click to pick a player to track")
                            .withStyle(ChatFormatting.GRAY), true);
                    shown = true;
                }
                continue;
            }

            UUID targetId;
            try {
                targetId = UUID.fromString(tag.getStringOr(KEY_TARGET, ""));
            } catch (IllegalArgumentException e) {
                inv.setItem(i, ItemStack.EMPTY);
                continue;
            }
            String name = tag.getStringOr(KEY_NAME, "Unknown");
            int storedDeaths = tag.getIntOr(KEY_DEATHS, 0);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);

            Component line;
            if (target == null) {
                line = Component.literal(name + " is offline").withStyle(ChatFormatting.GRAY);
            } else {
                stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                        Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition())), false));

                if (holder.level().dimension() != target.level().dimension()) {
                    line = Component.literal(name + " is in another dimension").withStyle(ChatFormatting.GRAY);
                } else {
                    line = buildLine(holder, target, name);
                }
            }

            boolean held = stack == holder.getMainHandItem() || stack == holder.getOffhandItem();
            if (held && !shown) {
                holder.sendSystemMessage(line, true);
                shown = true;
            }
        }
    }

    private static Component buildLine(ServerPlayer holder, ServerPlayer target, String name) {
        double dx = target.getX() - holder.getX();
        double dz = target.getZ() - holder.getZ();
        int meters = Math.round(holder.distanceTo(target));

        // Minecraft yaw: 0 = south, 90 = west. Positive relative angle = target is to the right.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees((float) (targetYaw - holder.getYRot()));
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);

        return Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("  " + meters + " m  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(ARROWS[idx]).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }
}
