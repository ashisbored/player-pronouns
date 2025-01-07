package dev.ashhhleyyy.playerpronouns.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A combined set of {@link Pronoun}s
 * @param raw The plain text version of this pronoun set
 * @param formatted The styled version of this pronoun set
 * @param remote Whether the pronouns were fetched from a remote API
 * @param provider The ID of the external provider that these pronouns were fetched from
 */
public record Pronouns(
        String raw,
        Text formatted,
        boolean remote,
        @Nullable Identifier provider
) {
    public static final Codec<Pronouns> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("raw").forGetter(Pronouns::raw),
            TextCodecs.CODEC.fieldOf("formatted").forGetter(Pronouns::formatted),
            Codec.BOOL.optionalFieldOf("remote", false).forGetter(Pronouns::remote),
            Identifier.CODEC.optionalFieldOf("provider").forGetter(pronouns -> Optional.ofNullable(pronouns.provider()))
    ).apply(instance, Pronouns::new));

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // lol, lmao
    private Pronouns(String raw, Text formatted, boolean remote, Optional<Identifier> provider) {
        this(raw, formatted, remote, provider.orElse(null));
    }

    private Pronouns(String raw, Text formatted) {
        this(raw, formatted, false, (Identifier) null);
    }

    public static Pronouns fromString(String pronouns) {
        return Pronouns.fromString(pronouns, false, null);
    }

    public static Pronouns fromString(String pronouns, boolean remote, @Nullable Identifier provider) {
        Text formatted = PronounList.get().getCalculatedPronounStrings().getOrDefault(pronouns, Text.literal(pronouns));
        return new Pronouns(pronouns, formatted, remote, provider);
    }
}
