package dev.ashhhleyyy.playerpronouns.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A single pronoun, which consists of the word itself, along with an associated style.
 *
 * @param pronoun The text of this pronoun
 * @param style   An associated style that is used for display as {@link Text}
 */
public record Pronoun(
        String pronoun,
        Style style
) {
    public static final Codec<Pronoun> CODEC = new PronounCodec();

    private static Style styleFrom(List<String> formatting) {
        Style style = Style.EMPTY;

        for (String format : formatting) {
            switch (format) {
                case "bold" -> style = style.withBold(true);
                case "italic" -> style = style.withItalic(true);
                case "strikethrough" -> style = style.withStrikethrough(true);
                case "underline" -> style = style.withUnderline(true);
                case "obfuscated" -> style = style.withObfuscated(true);
                default -> {
                    TextColor col = TextColor.parse(format).result().orElse(null);
                    if (col != null) {
                        style = style.withColor(col);
                    } else {
                        PlayerPronouns.LOGGER.warn("Invalid formatting: {}", format);
                    }
                }
            }
        }

        return style;
    }

    private static List<String> fromStyle(Style style) {
        List<String> ret = new ArrayList<>();
        TextColor colour = style.getColor();
        if (colour != null) {
            ret.add(colour.toString());
        }
        if (style.isBold()) {
            ret.add("bold");
        }
        if (style.isItalic()) {
            ret.add("italic");
        }
        if (style.isStrikethrough()) {
            ret.add("strikethrough");
        }
        if (style.isUnderlined()) {
            ret.add("underline");
        }
        if (style.isObfuscated()) {
            ret.add("obfuscated");
        }
        return ret;
    }

    /**
     * @return A version of this pronoun formatted as a {@link MutableText}
     */
    public MutableText toText() {
        return Text.literal(this.pronoun).setStyle(this.style);
    }

    @Override
    public String toString() {
        return this.pronoun;
    }

    @Override
    public int hashCode() {
        return this.pronoun.hashCode();
    }

    private static class PronounCodec implements Codec<Pronoun> {
        private static final Codec<Pronoun> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("pronoun").forGetter(Pronoun::pronoun),
                Codec.STRING.listOf().xmap(Pronoun::styleFrom, Pronoun::fromStyle).fieldOf("style").forGetter(Pronoun::style)
        ).apply(instance, Pronoun::new));

        private PronounCodec() {
        }

        @Override
        public <T> DataResult<Pair<Pronoun, T>> decode(DynamicOps<T> ops, T input) {
            Optional<String> asString = ops.getStringValue(input).result();
            return asString.map(s -> DataResult.success(new Pair<>(new Pronoun(s, Style.EMPTY), ops.empty())))
                    .orElseGet(() -> OBJECT_CODEC.decode(ops, input));
        }

        @Override
        public <T> DataResult<T> encode(Pronoun input, DynamicOps<T> ops, T prefix) {
            if (input.style.isEmpty()) {
                return ops.mergeToPrimitive(prefix, ops.createString(input.pronoun));
            } else {
                return OBJECT_CODEC.encode(input, ops, prefix);
            }
        }
    }
}
