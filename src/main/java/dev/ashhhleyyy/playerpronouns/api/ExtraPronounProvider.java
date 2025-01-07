package dev.ashhhleyyy.playerpronouns.api;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A provider that can add extra pronouns for a user based on remote data.
 */
public interface ExtraPronounProvider {
    CompletableFuture<Optional<String>> provideExtras(UUID playerId);

    Identifier getId();

    Text getName();

    boolean enabled();
}
