package dev.ashhhleyyy.playerpronouns.impl.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Locale;

public class PronounsArgument {

    private PronounsArgument() {
    }

    public static RequiredArgumentBuilder<ServerCommandSource, String> pronouns(String name) {
        return CommandManager.argument(name, StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    String remaining = builder.getRemainingLowerCase();

                    for (String pronouns : PronounList.get().getCalculatedPronounStrings().keySet()) {
                        if (pronouns.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                            builder.suggest(pronouns);
                        }
                    }

                    return builder.buildFuture();
                });
    }
}
