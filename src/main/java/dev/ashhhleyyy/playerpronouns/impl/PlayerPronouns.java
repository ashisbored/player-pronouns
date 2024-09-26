package dev.ashhhleyyy.playerpronouns.impl;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ashhhleyyy.playerpronouns.api.Pronouns;
import dev.ashhhleyyy.playerpronouns.api.PronounsApi;
import dev.ashhhleyyy.playerpronouns.impl.command.PronounsCommand;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounDatabase;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerPronouns implements ModInitializer, PronounsApi.PronounReader, PronounsApi.PronounSetter {
    public static final Logger LOGGER = LoggerFactory.getLogger(PlayerPronouns.class);
    public static final String MOD_ID = "playerpronouns";
    private static final String USER_AGENT = "player-pronouns/1.0 (+https://ashhhleyyy.dev/projects/2021/player-pronouns)";

    private static final Map<String, String> PRONOUNDB_ID_MAP = new HashMap<>() {{
        // short pronoun set identifier map from https://pronoundb.org/wiki/api-docs
        put("he", "he/him");
        put("it", "it/its");
        put("she", "she/her");
        put("they", "they/them");
        put("any", "any");
        put("ask", "ask");
        put("avoid", "avoid");
        put("other", "other");
    }};

    public static Identifier identifier(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private PronounDatabase pronounDatabase;
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
            if(config.enablePronounDBSync()) {
                // Do not override player-set pronouns
                var currentPronouns = pronounDatabase.get(handler.getPlayer().getUuid());
                if (currentPronouns != null && !currentPronouns.remote()) return;

                var pronounDbUrl = "https://pronoundb.org/api/v2/lookup?platform=minecraft&ids=%s"
                                            .formatted(handler.getPlayer().getUuid());
                try {
                    var client = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    var req = HttpRequest.newBuilder()
                            .uri(new URI(pronounDbUrl))
                            .header("User-Agent", USER_AGENT)
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenAcceptAsync(body -> {
                                // Do not override player-set pronouns (repeated to prevent race-condition from network delays)
                                var currentPronouns2 = pronounDatabase.get(handler.getPlayer().getUuid());
                                if (currentPronouns2 != null && !currentPronouns2.remote()) return;

                                var json = JsonHelper.deserialize(body);
                                StringBuilder pronouns = new StringBuilder("unspecified");
                                if (json.has(handler.getPlayer().getUuid().toString()) && json.getAsJsonObject(handler.getPlayer().getUuid().toString()).getAsJsonObject("sets").has("en")) {
                                    var pronounsList = json.getAsJsonObject(handler.getPlayer().getUuid().toString()).getAsJsonObject("sets").getAsJsonArray("en");
                                    if (pronounsList.size() == 1) {
                                        pronouns = new StringBuilder(PRONOUNDB_ID_MAP.get(pronounsList.get(0).getAsString()));
                                    } else {
                                        pronouns = new StringBuilder();
                                        for (var prounoun: pronounsList) {
                                            pronouns.append(prounoun.getAsString()).append("/");
                                        }
                                        pronouns = new StringBuilder(pronouns.substring(0, pronouns.length() - 1)); // remove trailing slash
                                    }
                                }
                                if (!"unspecified".contentEquals(pronouns)) {
                                    setPronouns(handler.getPlayer(), new Pronouns(pronouns.toString(), PronounList.get().getCalculatedPronounStrings().get(pronouns.toString()), true));
                                }
                            }, server);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
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
