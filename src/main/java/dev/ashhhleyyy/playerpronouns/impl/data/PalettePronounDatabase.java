package dev.ashhhleyyy.playerpronouns.impl.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.ashhhleyyy.playerpronouns.api.Pronouns;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An improved version of {@link dev.ashhhleyyy.playerpronouns.impl.data.BinaryPronounDatabase} that uses a palette
 * for efficiency when storing lots of players. It also supports versioning of the file to allow for changes to be
 * made to the format in the future.
 */
public class PalettePronounDatabase implements PronounDatabase {
    public static final int VERSION_NUMBER = 1;
    private final Path databasePath;
    private final Object2ObjectMap<UUID, Pronouns> data;

    protected PalettePronounDatabase(Path databasePath, Object2ObjectMap<UUID, Pronouns> data) {
        this.databasePath = databasePath;
        this.data = data;
    }

    private PalettePronounDatabase(Path databasePath) {
        this(databasePath, new Object2ObjectOpenHashMap<>());
    }

    @Override
    public void put(UUID player, @Nullable Pronouns pronouns) {
        if (pronouns == null) {
            this.data.remove(player);
        } else {
            this.data.put(player, pronouns);
        }
    }

    @Override
    public @Nullable Pronouns get(UUID player) {
        return this.data.get(player);
    }

    @Override
    public synchronized void save() throws IOException {
        try (OutputStream os = Files.newOutputStream(this.databasePath);
             DataOutputStream out = new DataOutputStream(os)) {

            out.writeShort(0x4568);
            out.writeInt(VERSION_NUMBER);

            Pair<List<Pronouns>, Object2IntMap<UUID>> pair = this.convertToPalette();
            List<Pronouns> palette = pair.getLeft();
            Object2IntMap<UUID> values = pair.getRight();

            out.writeInt(palette.size());
            for (Pronouns pronouns : palette) {
                String p = Pronouns.CODEC.encodeStart(JsonOps.INSTANCE, pronouns).result().orElseThrow().toString();
                out.writeUTF(p);
            }

            out.writeInt(values.size());
            for (var entry : values.object2IntEntrySet()) {
                out.writeLong(entry.getKey().getMostSignificantBits());
                out.writeLong(entry.getKey().getLeastSignificantBits());
                out.writeInt(entry.getIntValue());
            }
        }
    }

    private Pair<List<Pronouns>, Object2IntMap<UUID>> convertToPalette() {
        List<Pronouns> palette = new ArrayList<>();
        Object2IntMap<UUID> values = new Object2IntOpenHashMap<>();
        for (var entry : this.data.entrySet()) {
            if (!palette.contains(entry.getValue())) {
                palette.add(entry.getValue());
            }
            values.put(entry.getKey(), palette.indexOf(entry.getValue()));
        }
        return new Pair<>(palette, values);
    }

    public static PalettePronounDatabase load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new PalettePronounDatabase(path);
        }

        try (InputStream is = Files.newInputStream(path);
             DataInputStream in = new DataInputStream(is)) {

            short magic = in.readShort();
            if (magic != 0x4568) {
                throw new IOException("Invalid DB magic: " + magic);
            }

            int version = in.readInt();
            if (version > VERSION_NUMBER) {
                throw new IOException("DB version " + version + " is greater than the latest supported: " + version);
            }

            List<Pronouns> palette = new ArrayList<>();
            int paletteSize = in.readInt();
            for (int i = 0; i < paletteSize; i++) {
                String s = in.readUTF();
                Optional<Pronouns> optionalPronouns = Pronouns.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(s)).resultOrPartial(e -> {
                    throw new RuntimeException(new IOException("Invalid pronouns in database: " + s));
                }).map(com.mojang.datafixers.util.Pair::getFirst);
                if (optionalPronouns.isEmpty()) {
                    throw new IOException("Invalid pronouns in database: " + s);
                }
                palette.add(optionalPronouns.get());
            }

            Object2ObjectMap<UUID, Pronouns> data = new Object2ObjectOpenHashMap<>();

            // V1 Parsing
            if (version == 1) {
                int playerCount = in.readInt();
                for (int i = 0; i < playerCount; i++) {
                    long mostSigBits = in.readLong();
                    long leastSigBits = in.readLong();
                    UUID uuid = new UUID(mostSigBits, leastSigBits);
                    int pronounIndex = in.readInt();
                    Pronouns old = data.put(uuid, palette.get(pronounIndex));
                    if (old != null) {
                        PlayerPronouns.LOGGER.warn("Duplicate UUID in database: {}", uuid);
                    }
                }
            }

            return new PalettePronounDatabase(path, data);
        }
    }
}
