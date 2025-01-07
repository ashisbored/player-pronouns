package dev.ashhhleyyy.playerpronouns.impl.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ashhhleyyy.playerpronouns.api.Pronoun;
import dev.ashhhleyyy.playerpronouns.impl.Config;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class PronounList {
    private static PronounList INSTANCE;

    private final List<Pronoun> defaultSingle;
    private final List<Pronoun> defaultPairs;
    private final List<Pronoun> customSingle;
    private final List<Pronoun> customPairs;
    private final Map<String, Text> calculatedPronounStrings;

    public PronounList(List<Pronoun> defaultSingle, List<Pronoun> defaultPairs, List<Pronoun> customSingle, List<Pronoun> customPairs) {
        this.defaultSingle = defaultSingle;
        this.defaultPairs = defaultPairs;
        this.customSingle = new ArrayList<>(customSingle);
        this.customPairs = new ArrayList<>(customPairs);
        this.calculatedPronounStrings = this.computePossibleCombinations();
    }

    public static void load(Config config) {
        if (INSTANCE != null) {
            INSTANCE.reload(config);
            return;
        }

        Pair<List<Pronoun>, List<Pronoun>> defaults = loadDefaults();
        INSTANCE = new PronounList(
                defaults.getLeft(),
                defaults.getRight(),
                config.getSingle(),
                config.getPairs()
        );
    }

    public static PronounList get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("PronounList has not been loaded!");
        }
        return INSTANCE;
    }

    private static Pair<List<Pronoun>, List<Pronoun>> loadDefaults() {
        try (InputStream is = Objects.requireNonNull(PronounList.class.getResourceAsStream("/default_pronouns.json"));
             InputStreamReader reader = new InputStreamReader(is)) {
            JsonObject ele = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray jsonSingle = ele.getAsJsonArray("single");
            JsonArray jsonPairs = ele.getAsJsonArray("pairs");
            List<Pronoun> single = new ArrayList<>();
            List<Pronoun> pairs = new ArrayList<>();
            jsonSingle.forEach(e -> single.add(new Pronoun(e.getAsString(), Style.EMPTY)));
            jsonPairs.forEach(e -> pairs.add(new Pronoun(e.getAsString(), Style.EMPTY)));
            return new Pair<>(single, pairs);
        } catch (IOException e) {
            PlayerPronouns.LOGGER.error("Failed to load default pronouns!", e);
            return new Pair<>(Collections.emptyList(), Collections.emptyList());
        }
    }

    public Map<String, Text> getCalculatedPronounStrings() {
        return this.calculatedPronounStrings;
    }

    private Map<String, Text> computePossibleCombinations() {
        Map<String, Text> ret = new HashMap<>();
        for (Pronoun pronoun : this.defaultSingle) {
            ret.put(pronoun.pronoun(), pronoun.toText());
        }
        for (Pronoun pronoun : this.customSingle) {
            ret.put(pronoun.pronoun(), pronoun.toText());
        }
        List<Pronoun> combinedPairs = new ArrayList<>();
        combinedPairs.addAll(this.defaultPairs);
        combinedPairs.addAll(this.customPairs);
        for (int i = 0; i < combinedPairs.size(); i++) {
            for (int j = 0; j < combinedPairs.size(); j++) {
                if (i == j) continue;
                Pronoun a = combinedPairs.get(i);
                Pronoun b = combinedPairs.get(j);
                MutableText combined = Text.literal("");
                combined.append(a.toText());
                combined.append(Text.literal("/"));
                combined.append(b.toText());
                ret.put(a.pronoun() + "/" + b.pronoun(), combined);
            }
        }
        return ret;
    }

    private void reload(Config config) {
        this.customSingle.clear();
        this.customPairs.clear();
        this.customSingle.addAll(config.getSingle());
        this.customPairs.addAll(config.getPairs());
        this.calculatedPronounStrings.clear();
        this.calculatedPronounStrings.putAll(this.computePossibleCombinations());
    }
}
