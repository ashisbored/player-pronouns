package dev.ashhhleyyy.playerpronouns.impl;

import com.google.common.collect.Iterators;
import dev.ashhhleyyy.playerpronouns.api.ExtraPronounProvider;
import dev.ashhhleyyy.playerpronouns.api.Pronouns;
import dev.ashhhleyyy.playerpronouns.api.PronounsApi;
import dev.ashhhleyyy.playerpronouns.impl.command.PronounsCommand;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounDatabase;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;
import dev.ashhhleyyy.playerpronouns.impl.interop.PronounDbClient;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

public class PlayerPronouns implements ModInitializer, PronounsApi.PronounReader, PronounsApi.PronounSetter {
    public static final Logger LOGGER = LoggerFactory.getLogger(PlayerPronouns.class);
    public static final String MOD_ID = "playerpronouns";
    public static final String USER_AGENT = "player-pronouns/1.0 (+https://ashhhleyyy.dev/projects/2021/player-pronouns)";

    public static Identifier identifier(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private PronounDatabase pronounDatabase;
    private PronounDbClient pronounDbClient;
    public static Config config;

    @Override
    public void onInitialize() {
        LOGGER.info("Player Pronouns initialising...");

        config = Config.load();
        PronounList.load(config);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                Path playerData = server.getSavePath(WorldSavePath.PLAYERDATA);
                if (!Files.exists(playerData)) {
                    Files.createDirectories(playerData);
                }
                pronounDatabase = PronounDatabase.load(playerData.resolve("pronouns.dat"));
            } catch (IOException e) {
                LOGGER.error("Failed to create/load pronoun database!", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (pronounDatabase != null) {
                try {
                    pronounDatabase.save();
                } catch (IOException e) {
                    LOGGER.error("Failed to save pronoun database!", e);
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Iterator<ExtraPronounProvider> providers = PronounsApi.getExtraPronounProviders().iterator();
            UUID uuid = handler.player.getUuid();
            Pronouns currentPronouns = PronounsApi.getReader().getPronouns(uuid);
            if (currentPronouns != null) {
                if (currentPronouns.remote()) {
                    if (currentPronouns.provider() != null) {
                        for (ExtraPronounProvider provider : PronounsApi.getExtraPronounProviders()) {
                            if (provider.getId().equals(currentPronouns.provider())) {
                                this.tryFetchPronouns(server, uuid, Iterators.singletonIterator(provider), true);
                                break;
                            }
                        }
                    } else {
                        // remote pronouns without a provider are pre-multi-provider-support
                        // from when only pronoundb.org was supported
                        this.tryFetchPronouns(server, uuid, Iterators.singletonIterator(pronounDbClient), true);
                    }
                }
            } else {
                this.tryFetchPronouns(server, uuid, providers, false);
            }
        });

        //noinspection CodeBlock2Expr
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            PronounsCommand.register(dispatcher);
        });

        Placeholders.register(PlayerPronouns.identifier("pronouns"), (ctx, argument) ->
                fromContext(ctx, argument, true));

        Placeholders.register(PlayerPronouns.identifier("raw_pronouns"), (ctx, argument) ->
                fromContext(ctx, argument, false));

        PronounsApi.initReader(this);
        PronounsApi.initSetter(this);
        PronounsApi.registerPronounProvider(this.pronounDbClient = new PronounDbClient());
    }

    private void tryFetchPronouns(MinecraftServer server, UUID player, Iterator<ExtraPronounProvider> providers, boolean alreadySet) {
        if (!providers.hasNext()) return;
        ExtraPronounProvider provider = providers.next();
        if (!provider.enabled()) {
            // skip disabled providers
            tryFetchPronouns(server, player, providers, alreadySet);
        }
        provider.provideExtras(player)
                .thenAcceptAsync(optionalPronouns -> {
                    optionalPronouns.ifPresent(pronouns -> {
                        Pronouns currentPronouns = getPronouns(player);
                        Pronouns newPronouns = Pronouns.fromString(pronouns, true, provider.getId());
                        setPronouns(player, newPronouns);
                        if (!newPronouns.equals(currentPronouns) || !alreadySet) {
                            ServerPlayerEntity playerEntity = server.getPlayerManager().getPlayer(player);
                            if (playerEntity != null) {
                                var message = Text.literal("Set your pronouns to " + pronouns + " (from ").append(provider.getName()).append(")");
                                playerEntity.sendMessage(message.formatted(Formatting.GREEN));
                            }
                        }
                    });
                    if (optionalPronouns.isEmpty()) {
                        tryFetchPronouns(server, player, providers, alreadySet);
                    }
                }, server);
    }

    private PlaceholderResult fromContext(PlaceholderContext ctx, @Nullable String argument, boolean formatted) {
        if (!ctx.hasPlayer()) {
            return PlaceholderResult.invalid("missing player");
        }
        String defaultMessage = argument != null ? argument : config.getDefaultPlaceholder();
        ServerPlayerEntity player = ctx.player();
        assert player != null;
        if (pronounDatabase == null) {
            return PlaceholderResult.value(defaultMessage);
        }
        Pronouns pronouns = pronounDatabase.get(player.getUuid());
        if (pronouns == null) {
            return PlaceholderResult.value(defaultMessage);
        }
        if (formatted) {
            return PlaceholderResult.value(pronouns.formatted());
        } else {
            return PlaceholderResult.value(pronouns.raw());
        }
    }

    public static void reloadConfig() {
        config = Config.load();
        PronounList.load(config);
    }

    public boolean setPronouns(UUID playerId, @Nullable Pronouns pronouns) {
        if (pronounDatabase == null) return false;

        pronounDatabase.put(playerId, pronouns);
        try {
            pronounDatabase.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save pronoun database!", e);
        }

        return true;
    }

    public @Nullable Pronouns getPronouns(ServerPlayerEntity player) {
        return getPronouns(player.getUuid());
    }

    public @Nullable Pronouns getPronouns(UUID playerId) {
        if (pronounDatabase == null) return null;
        return pronounDatabase.get(playerId);
    }
}
