package dev.vanish.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared command tree for Fabric, Forge and NeoForge. */
public final class ModCommands {
  private ModCommands() {}

  public static void register(
      CommandDispatcher<CommandSourceStack> dispatcher, Supplier<ModVanish> service) {
    dispatcher.register(
        Commands.literal("vanish")
            .executes(context -> run(context, service, "toggle", null))
            .then(Commands.literal("on").executes(context -> run(context, service, "on", null)))
            .then(Commands.literal("off").executes(context -> run(context, service, "off", null)))
            .then(
                Commands.literal("toggle")
                    .executes(context -> run(context, service, "toggle", null)))
            .then(
                Commands.literal("status")
                    .executes(context -> run(context, service, "status", null))
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes(
                                context ->
                                    run(context, service, "status", target(context)))))
            .then(Commands.literal("list").executes(context -> run(context, service, "list", null)))
            .then(
                Commands.literal("reload")
                    .executes(context -> run(context, service, "reload", null)))
            .then(
                Commands.argument("action", StringArgumentType.word())
                    .suggests(
                        (context, builder) -> {
                          builder.suggest("on");
                          builder.suggest("off");
                          builder.suggest("toggle");
                          builder.suggest("status");
                          ModVanish current = service.get();
                          if (current != null) {
                            for (String name : current.onlineNames()) {
                              builder.suggest(name);
                            }
                          }
                          return builder.buildFuture();
                        })
                    .executes(
                        context ->
                            run(
                                context,
                                service,
                                StringArgumentType.getString(context, "action"),
                                null))
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes(
                                context ->
                                    run(
                                        context,
                                        service,
                                        StringArgumentType.getString(context, "action"),
                                        target(context))))));
    dispatcher.register(
        Commands.literal("v")
            .executes(context -> run(context, service, "toggle", null))
            .then(Commands.literal("on").executes(context -> run(context, service, "on", null)))
            .then(Commands.literal("off").executes(context -> run(context, service, "off", null)))
            .then(
                Commands.literal("toggle")
                    .executes(context -> run(context, service, "toggle", null)))
            .then(
                Commands.literal("status")
                    .executes(context -> run(context, service, "status", null))));
  }

  private static String target(CommandContext<CommandSourceStack> context) {
    try {
      return EntityArgument.getPlayer(context, "target").getGameProfile().name();
    } catch (CommandSyntaxException error) {
      throw new IllegalArgumentException("Specify an online player");
    }
  }

  private static int run(
      CommandContext<CommandSourceStack> context,
      Supplier<ModVanish> service,
      String action,
      String target) {
    CommandSourceStack source = context.getSource();
    ServerPlayer sender = source.getPlayer();
    if (sender == null) {
      source.sendFailure(ModText.parse("\u00a7cRun this command as a player."));
      return 0;
    }
    ModVanish vanish = service.get();
    if (vanish == null) {
      source.sendFailure(ModText.parse("\u00a7e" + "Vanish is not ready yet."));
      return 0;
    }
    try {
      String feedback = new ModCommand(vanish).execute(sender, action, target);
      if (feedback != null) {
        source.sendSuccess(() -> ModText.parse(feedback), false);
      }
      return 1;
    } catch (IllegalArgumentException error) {
      source.sendFailure(ModText.parse(error.getMessage()));
      return 0;
    } catch (IOException error) {
      source.sendFailure(
          ModText.parse(
              vanish.config().message("save-failed", "{error}Vanish save failed; contact an admin.")));
      return 0;
    }
  }
}
