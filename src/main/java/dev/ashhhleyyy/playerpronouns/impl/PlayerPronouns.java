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
import java.util.Random;
import java.util.UUID;

import static java.util.Calendar.getInstance;

public class PlayerPronouns implements ModInitializer, PronounsApi.PronounReader, PronounsApi.PronounSetter {
    public static final Logger LOGGER = LoggerFactory.getLogger(PlayerPronouns.class);
    public static final String MOD_ID = "playerpronouns";
    public static final String USER_AGENT = "player-pronouns/1.0 (+https://ashhhleyyy.dev/projects/2021/player-pronouns)";
    public static final byte[][] OWOS = new byte[][]{new byte[]{73,110,106,101,99,116,105,110,103,32,119,111,107,101,46,46,46},new byte[]{85,112,103,114,97,100,105,110,103,32,97,109,97,116,101,117,114,32,110,111,117,110,115,46,46,46},new byte[]{80,114,101,112,97,114,105,110,103,32,65,98,115,116,114,97,99,116,80,114,111,110,111,117,110,80,114,111,118,105,100,101,114,70,97,99,116,111,114,121,46,46,46},new byte[]{84,114,97,110,115,105,110,103,32,103,101,110,100,101,114,115,46,46,46},new byte[]{77,97,107,105,110,103,32,116,104,101,32,102,114,111,103,115,32,103,97,121,46,46,46},new byte[]{70,108,121,105,110,103,32,102,108,97,103,115,46,46,46},new byte[]{76,111,99,97,116,105,110,103,32,66,108,97,104,97,106,46,46,46},new byte[]{72,97,112,112,121,32,112,114,105,100,101,32,109,111,110,116,104,33,33}};
    public static Config config;
    private PronounDatabase pronounDatabase;
    private PronounDbClient pronounDbClient;

    public static Identifier identifier(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static void reloadConfig() {
        config = Config.load();
        PronounList.load(config);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[PlayerPronouns] {}", new String(OWOS[getInstance().get(2) == 5 ? OWOS.length - 1 : new Random().nextInt(OWOS.length - 1)]));

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
