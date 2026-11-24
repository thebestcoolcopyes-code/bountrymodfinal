package com.bountytracker;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Stores bounties (target -> reward) in config/bountytracker.json and pays them out on a player kill. */
public final class BountyManager {
    public record Bounty(String targetName, String itemId, int count) {
        public ItemStack createStack() {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
        }
    }

    private static final Map<UUID, Bounty> BOUNTIES = new LinkedHashMap<>();

    private BountyManager() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bountytracker.json");
    }

    public static void load() {
        BOUNTIES.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                BOUNTIES.put(UUID.fromString(e.getKey()),
                        new Bounty(o.get("name").getAsString(), o.get("item").getAsString(), o.get("count").getAsInt()));
            }
        } catch (Exception ex) {
            BountyTrackerMod.LOGGER.error("Could not read bounties file", ex);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        BOUNTIES.forEach((uuid, b) -> {
            JsonObject o = new JsonObject();
            o.addProperty("name", b.targetName());
            o.addProperty("item", b.itemId());
            o.addProperty("count", b.count());
            root.add(uuid.toString(), o);
        });
        try (Writer w = Files.newBufferedWriter(file())) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (IOException ex) {
            BountyTrackerMod.LOGGER.error("Could not save bounties file", ex);
        }
    }

    public static void set(ServerPlayer target, Item item, int count) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        BOUNTIES.put(target.getUUID(), new Bounty(target.getName().getString(), id, count));
        save();
    }

    public static boolean removeByName(String name) {
        UUID found = null;
        for (var e : BOUNTIES.entrySet()) {
            if (e.getValue().targetName().equalsIgnoreCase(name)) { found = e.getKey(); break; }
        }
        if (found == null) return false;
        BOUNTIES.remove(found);
        save();
        return true;
    }

    public static void clearAll() {
        BOUNTIES.clear();
        save();
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        BOUNTIES.values().forEach(b -> out.add(b.targetName()));
        return out;
    }

    public static Map<UUID, Bounty> all() {
        return BOUNTIES;
    }

    /** Fired by Fabric API after any living entity dies. */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer victim)) return;
        Bounty bounty = BOUNTIES.get(victim.getUUID());
        if (bounty == null) return;
        if (!(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return;

        BOUNTIES.remove(victim.getUUID());
        save();

        ItemStack reward = bounty.createStack();
        if (!reward.isEmpty()) {
            Component rewardName = reward.getHoverName();
            killer.getInventory().placeItemBackInInventory(reward.copy(), Prediction.SERVER_ONLY);
            broadcast(killer, Component.literal(killer.getName().getString()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal(" collected the bounty on ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(bounty.targetName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("! Reward: " + bounty.count() + "x ").withStyle(ChatFormatting.GOLD))
                    .append(rewardName.copy().withStyle(ChatFormatting.AQUA)));
        }
    }

    private static void broadcast(ServerPlayer any, Component msg) {
        MinecraftServer server = any.level().getServer();
        if (server != null) server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
