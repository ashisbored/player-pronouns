package dev.ashhhleyyy.playerpronouns.impl;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.ashhhleyyy.playerpronouns.api.Pronoun;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record Config(
        boolean allowCustom,
        List<Pronoun> single,
        List<Pronoun> pairs,
        String defaultPlaceholder,
        Integrations integrations
) {
    private static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("allow_custom").forGetter(Config::allowCustom),
            Pronoun.CODEC.listOf().fieldOf("single").forGetter(Config::single),
            Pronoun.CODEC.listOf().fieldOf("pairs").forGetter(Config::pairs),
            Codec.STRING.optionalFieldOf("default_placeholder", "Unknown").forGetter(Config::defaultPlaceholder),
            Integrations.CODEC.fieldOf("integrations").forGetter(Config::integrations)
    ).apply(instance, Config::new));

    private Config() {
        this(true, Collections.emptyList(), Collections.emptyList(), "Unknown", new Integrations());
    }

    public boolean allowCustom() {
        return allowCustom;
    }

    public List<Pronoun> getSingle() {
        return single;
    }

    public List<Pronoun> getPairs() {
        return pairs;
    }

    public String getDefaultPlaceholder() {
        return defaultPlaceholder;
    }

    public static Config load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("player-pronouns.json");
        if (!Files.exists(path)) {
            Config config = new Config();
            Optional<JsonElement> result = CODEC.encodeStart(JsonOps.INSTANCE, config).result();
            if (result.isPresent()) {
                try {
                    Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(result.get()));
                } catch (IOException e) {
                    PlayerPronouns.LOGGER.warn("Failed to save default config!", e);
                }
            } else {
                PlayerPronouns.LOGGER.warn("Failed to save default config!");
            }
            return new Config();
        } else {
            try {
                String s = Files.readString(path);
                JsonElement ele = JsonParser.parseString(s);
                DataResult<Config> result = CODEC.decode(JsonOps.INSTANCE, ele).map(Pair::getFirst);
                Optional<DataResult.Error<Config>> err = result.error();
                err.ifPresent(e -> PlayerPronouns.LOGGER.warn("Failed to load config: {}", e.message()));
                Config config = result.result().orElseGet(Config::new);
                if (err.isEmpty() && ele.getAsJsonObject().has("enable_pronoundb_sync")) {
                    if (!ele.getAsJsonObject().get("enable_pronoundb_sync").getAsBoolean()) {
                        PlayerPronouns.LOGGER.warn("Config option `enable_pronoundb_sync` is legacy and will be removed in the next release. Please set `integrations.pronoundb` to `false` instead.");
                        config = new Config(config.allowCustom, config.single, config.pairs, config.defaultPlaceholder, new Integrations(
                                false
                        ));
                    }
                }
                return config;
            } catch (IOException e) {
                PlayerPronouns.LOGGER.warn("Failed to load config!", e);
                return new Config();
            }
        }
    }

    public record Integrations(
            boolean pronounDB
    ) {
        private static final Codec<Integrations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("pronoun_db").forGetter(Integrations::pronounDB)
        ).apply(instance, Integrations::new));

        private Integrations() {
            this(true);
        }
    }
}
