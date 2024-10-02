package dev.ashhhleyyy.playerpronouns.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import dev.ashhhleyyy.playerpronouns.api.Pronouns;
import dev.ashhhleyyy.playerpronouns.api.PronounsApi;
import dev.ashhhleyyy.playerpronouns.impl.data.PronounList;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
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
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            String pronounsString = getString(ctx, "pronouns");

                            Map<String, Text> pronounTexts = PronounList.get().getCalculatedPronounStrings();
                            if (!PlayerPronouns.config.allowCustom() && !pronounTexts.containsKey(pronounsString)) {
                                ctx.getSource().sendError(Text.literal("Custom pronouns have been disabled by the server administrator."));
                                return 0;
                            }

                            // Check the length
                            int maxLength = PlayerPronouns.config.getMaxLength();

                            // If it's 0 or less that mean it's disabled
                            if (!(maxLength<=0)) {
                                if (pronounsString.length()>maxLength){
                                    // Pronouns too big:
                                    ctx.getSource().sendFeedback(() -> Text.literal("Your pronouns is too big for this server.\nPlease make it smaller or use an acronym")
                                            .formatted(Formatting.RED), false);
                                    return Command.SINGLE_SUCCESS;
                                }
                            }



                            Pronouns pronouns;
                            if (pronounTexts.containsKey(pronounsString)) {
                                pronouns = new Pronouns(pronounsString, pronounTexts.get(pronounsString), false);
                            } else {
                                pronouns = new Pronouns(pronounsString, Text.literal(pronounsString), false);
                            }

                            assert player != null;
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
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            assert player != null;
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
