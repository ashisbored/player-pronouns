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

public class Config {
    private static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("allow_custom").forGetter(config -> config.allowCustom),
            Codec.BOOL.fieldOf("enable_pronoundb_sync").forGetter(config -> config.enablePronounDBSync),
            Pronoun.CODEC.listOf().fieldOf("single").forGetter(config -> config.single),
            Pronoun.CODEC.listOf().fieldOf("pairs").forGetter(config -> config.pairs),
            Codec.STRING.optionalFieldOf("default_placeholder", "Unknown").forGetter(config -> config.defaultPlaceholder),
            Codec.INT.fieldOf("max_length").forGetter(config -> config.maxLength)
    ).apply(instance, Config::new));

    private final boolean allowCustom;
    private final boolean enablePronounDBSync;
    private final List<Pronoun> single;
    private final List<Pronoun> pairs;
    private final String defaultPlaceholder;
    private final int maxLength;

    private Config(boolean allowCustom, boolean enablePronounDBSync, List<Pronoun> single, List<Pronoun> pairs, String defaultPlaceholder,int maxLength) {
        this.allowCustom = allowCustom;
        this.enablePronounDBSync = enablePronounDBSync;
        this.single = single;
        this.pairs = pairs;
        this.defaultPlaceholder = defaultPlaceholder;
        this.maxLength = maxLength;
    }

    private Config() {
        this(true, true, Collections.emptyList(), Collections.emptyList(), "Unknown",-1);
    }

    public boolean allowCustom() {
        return allowCustom;
    }

    public boolean enablePronounDBSync() {
        return enablePronounDBSync;
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

    public int getMaxLength() {return maxLength;}

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
                return result.result().orElseGet(Config::new);
            } catch (IOException e) {
                PlayerPronouns.LOGGER.warn("Failed to load config!", e);
                return new Config();
            }
        }
    }
}
