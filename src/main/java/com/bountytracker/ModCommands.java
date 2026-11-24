package com.bountytracker;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** All commands require operator permission (gamemaster level or higher). */
public final class ModCommands {
    private ModCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        // /tracker give <players> <target>   /tracker clear <players>
        d.register(Commands.literal("tracker")
                .then(Commands.literal("select")
                        .executes(c -> select(c.getSource())))
                .then(Commands.literal("choose")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> choose(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> give(c.getSource(),
                                                EntityArgument.getPlayers(c, "players"),
                                                EntityArgument.getPlayer(c, "target"))))))
                .then(Commands.literal("compass")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> giveAndOpenPicker(c.getSource(),
                                java.util.List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> giveAndOpenPicker(c.getSource(),
                                        EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("clear")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "players"))))));

        // /bounty set <target> <reward> [count]   /bounty remove <name>   /bounty list   /bounty clear
        d.register(Commands.literal("bounty")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("reward", ItemArgument.item(ctx))
                                        .executes(c -> setBounty(c.getSource(),
                                                EntityArgument.getPlayer(c, "target"),
                                                ItemArgument.getItem(c, "reward").item().value(), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> setBounty(c.getSource(),
                                                        EntityArgument.getPlayer(c, "target"),
                                                        ItemArgument.getItem(c, "reward").item().value(),
                                                        IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(BountyManager.names(), b))
                                .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("clear").executes(c -> {
                    BountyManager.clearAll();
                    c.getSource().sendSuccess(() -> Component.literal("All bounties cleared."), true);
                    return 1;
                })));
    }


    private static int select(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        TrackerManager.showPlayerSelector(player);
        return 1;
    }

    private static int choose(CommandSourceStack src, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return TrackerManager.selectTarget(src.getPlayerOrException(), target) ? 1 : 0;
    }

    private static int give(CommandSourceStack src, Collection<ServerPlayer> players, ServerPlayer target) {
        for (ServerPlayer p : players) {
            p.getInventory().placeItemBackInInventory(TrackerManager.createCompass(target), Prediction.SERVER_ONLY);
            p.sendSystemMessage(Component.literal("You received a tracker for " + target.getName().getString() + ".")
                    .withStyle(ChatFormatting.YELLOW));
        }
        final int n = players.size();
        src.sendSuccess(() -> Component.literal("Gave " + n + " player(s) a tracker for "
                + target.getName().getString()), true);
        return n;
    }

    private static int giveTrackingCompass(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            p.getInventory().placeItemBackInInventory(TrackerManager.createTrackingCompass(), Prediction.SERVER_ONLY);
        }
        final int n = players.size();
        src.sendSuccess(() -> Component.literal("Gave " + n + " player(s) a Tracking Compass"), true);
        return n;
    }

    private static int giveAndOpenPicker(CommandSourceStack src, Collection<ServerPlayer> players) {
        int given = giveTrackingCompass(src, players);
        for (ServerPlayer player : players) TrackerManager.showPlayerSelector(player);
        return given;
    }

    private static int clear(CommandSourceStack src, Collection<ServerPlayer> players) {
        int removed = 0;
        for (ServerPlayer p : players) removed += TrackerManager.clear(p);
        final int r = removed;
        src.sendSuccess(() -> Component.literal("Removed " + r + " tracker(s)."), true);
        return r;
    }

    private static int setBounty(CommandSourceStack src, ServerPlayer target, Item item, int count) {
        if (count > new ItemStack(item).getMaxStackSize()) {
            src.sendFailure(Component.literal("That item can't stack to " + count + "."));
            return 0;
        }
        BountyManager.set(target, item, count);
        ItemStack reward = new ItemStack(item, count);

        Component msg = Component.literal("A bounty has been placed on ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("! Reward: " + count + "x ").withStyle(ChatFormatting.GOLD))
                .append(reward.getHoverName().copy().withStyle(ChatFormatting.AQUA));
        src.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        return 1;
    }

    private static int remove(CommandSourceStack src, String name) {
        if (BountyManager.removeByName(name)) {
            src.sendSuccess(() -> Component.literal("Removed the bounty on " + name + "."), true);
            return 1;
        }
        src.sendFailure(Component.literal("No bounty on " + name + "."));
        return 0;
    }

    private static int list(CommandSourceStack src) {
        if (BountyManager.all().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No active bounties."), false);
            return 0;
        }
        BountyManager.all().values().forEach(b -> src.sendSuccess(
                () -> Component.literal(b.targetName() + " -> " + b.count() + "x " + b.itemId()), false));
        return BountyManager.all().size();
    }
}
