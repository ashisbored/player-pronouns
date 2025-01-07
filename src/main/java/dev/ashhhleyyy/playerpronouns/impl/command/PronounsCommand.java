package dev.ashhhleyyy.playerpronouns.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.ashhhleyyy.playerpronouns.api.Pronouns;
import dev.ashhhleyyy.playerpronouns.api.PronounsApi;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static dev.ashhhleyyy.playerpronouns.impl.command.PronounsArgument.pronouns;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PronounsCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pronouns")
                .then(literal("set")
                        .then(pronouns("pronouns")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String pronounsString = getString(ctx, "pronouns");

                                    Map<String, Text> pronounTexts = PronounList.get().getCalculatedPronounStrings();
                                    boolean isCustom = !pronounTexts.containsKey(pronounsString);
                                    if (isCustom && !PlayerPronouns.config.allowCustom()) {
                                        ctx.getSource().sendError(Text.literal("Custom pronouns have been disabled by the server administrator."));
                                        return 0;
                                    }

                                    int maxLength = PlayerPronouns.config.maxPronounLength();
                                    if (isCustom && maxLength > 0 && pronounsString.length() > maxLength) {
                                        ctx.getSource().sendError(Text.literal("The server administrator has limited the length of custom pronouns to " + maxLength + "."));
                                        return 0;
                                    }

                                    Pronouns pronouns = Pronouns.fromString(pronounsString);

                                    if (!PronounsApi.getSetter().setPronouns(player, pronouns)) {
                                        ctx.getSource().sendError(Text.literal("Failed to update pronouns, sorry"));
                                    } else {
                                        ctx.getSource().sendFeedback(() -> Text.literal("Updated your pronouns to ")
                                                .append(pronouns.formatted())
                                                .formatted(Formatting.GREEN), false);
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                ).then(literal("reload-config")
                        .requires(ctx -> Permissions.check(ctx, "playerpronouns.reload_config", 4))
                        .executes(ctx -> {
                            PlayerPronouns.reloadConfig();
                            ctx.getSource().sendFeedback(() -> Text.literal("Reloaded the config!").formatted(Formatting.GREEN), true);
                            return Command.SINGLE_SUCCESS;
                        })
                ).then(literal("unset")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            if (!PronounsApi.getSetter().setPronouns(player, null)) {
                                ctx.getSource().sendError(Text.literal("Failed to update pronouns, sorry"));
                            } else {
                                ctx.getSource().sendFeedback(() -> Text.literal("Cleared your pronouns!")
                                        .formatted(Formatting.GREEN), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                ).then(literal("show")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                    Pronouns pronouns = PronounsApi.getReader().getPronouns(player);
                                    if (pronouns != null) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("")
                                                .append(player.getDisplayName())
                                                .append(Text.literal("'s pronouns are ")
                                                        .append(pronouns.formatted())), false);
                                    } else {
                                        ctx.getSource().sendFeedback(() -> Text.literal("")
                                                .append(player.getDisplayName())
                                                .append(Text.literal(" has not set any pronouns.")), false);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
        );
    }
}
