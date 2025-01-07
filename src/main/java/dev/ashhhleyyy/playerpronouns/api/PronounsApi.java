package dev.ashhhleyyy.playerpronouns.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Entrypoint to the API, and provides access to a {@link PronounReader} and {@link PronounSetter}
 */
public final class PronounsApi {
    private static @Nullable PronounReader READER = null;
    private static @Nullable PronounSetter SETTER = null;
    private static final List<ExtraPronounProvider> PROVIDERS = new ArrayList<>();

    /**
     * @return The currently initialised {@link PronounReader}
     */
    public static PronounReader getReader() {
        if (READER == null) {
            throw new IllegalStateException("PronounReader has not been initialised");
        }
        return READER;
    }

    /**
     * @return The currently initialised {@link PronounSetter}
     */
    public static PronounSetter getSetter() {
        if (SETTER == null) {
            throw new IllegalStateException("PronounSetter has not been initialised");
        }
        return SETTER;
    }

    /**
     * Makes the passed reader be set as the default.
     * <p>
     * This should not be called by most mods, unless they are implementing a custom backend.
     * 
     * @param reader The reader to configure
     */
    public static void initReader(PronounReader reader) {
        if (READER != null) {
            throw new IllegalStateException("PronounReader has already been initialised");
        }
        READER = reader;
    }

    /**
     * Makes the passed setter be set as the default.
     * <p.
     * This should not be called by most mods, unless they are implementing a custom backend.
     * 
     * @param setter The setter to configure
     */
    public static void initSetter(PronounSetter setter) {
        if (SETTER != null) {
            throw new IllegalStateException("PronounSetter has already been initialised");
        }
        SETTER = setter;
    }

    public static void registerPronounProvider(ExtraPronounProvider provider) {
        PROVIDERS.add(provider);
    }

    public static List<ExtraPronounProvider> getExtraPronounProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /**
     * Allows updating a player's {@link Pronouns}.
     * <p>
     * Methods in this class may invoke blocking IO operations to save the database to disk.
     */
    public interface PronounSetter {
        default boolean setPronouns(ServerPlayerEntity player, @Nullable Pronouns pronouns) {
            return this.setPronouns(player.getUuid(), pronouns);
        }
        boolean setPronouns(UUID playerId, @Nullable Pronouns pronouns);
    }

    /**
     * Allows obtaining a player's {@link Pronouns}
     */
    public interface PronounReader {
        default @Nullable Pronouns getPronouns(ServerPlayerEntity player) {
            return this.getPronouns(player.getUuid());
        }
        @Nullable Pronouns getPronouns(UUID playerId);
    }
}
