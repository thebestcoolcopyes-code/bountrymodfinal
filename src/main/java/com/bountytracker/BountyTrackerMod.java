package com.bountytracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BountyTrackerMod implements ModInitializer {
    public static final String MOD_ID = "bountytracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BountyManager.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                ModCommands.register(dispatcher, buildContext));

        ServerTickEvents.END_SERVER_TICK.register(TrackerManager::tick);
        // Right-click with a Tracking Compass: cycle to the next online player.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer sp && TrackerManager.handleUse(sp, sp.getItemInHand(hand))) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            BountyManager.onDeath(entity, damageSource);
            if (entity instanceof ServerPlayer target
                    && damageSource.getEntity() instanceof ServerPlayer killer
                    && killer != target) {
                TrackerManager.onTargetDeath(target);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BountyManager.save());

        LOGGER.info("Bounty Tracker loaded.");
    }
}
