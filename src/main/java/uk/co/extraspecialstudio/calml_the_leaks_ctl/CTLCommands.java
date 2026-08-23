package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers CTL commands: /ctl status, leaks, explain, panel, narrow (narrow-down toolkit).
 */
@Mod.EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CTLCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("ctl")
            .then(argument("subcommand", StringArgumentType.string())
                .suggests((context, builder) -> {
                    String remaining = builder.getRemaining().toLowerCase();
                    SuggestionsBuilder b = builder;

                    if ("status".startsWith(remaining)) {
                        b = b.suggest("status");
                    }
                    if ("leaks".startsWith(remaining)) {
                        b = b.suggest("leaks");
                    }
                    if ("explain".startsWith(remaining)) {
                        b = b.suggest("explain");
                    }
                    if ("panel".startsWith(remaining)) {
                        b = b.suggest("panel");
                    }
                    if ("narrow".startsWith(remaining)) {
                        b = b.suggest("narrow");
                    }

                    return b.buildFuture();
                })
                .executes(ctx -> {
                    String subcommand = StringArgumentType.getString(ctx, "subcommand");
                    return switch (subcommand.toLowerCase()) {
                        case "status" -> handleStatus(ctx);
                        case "leaks" -> handleLeaks(ctx);
                        case "explain" -> handleExplainAll(ctx);
                        case "panel" -> handlePanel(ctx);
                        case "narrow" -> {
                            ctx.getSource().sendFailure(Component.literal(
                                "Use /ctl narrow snapshot | compare | mod … (see /ctl narrow mod list)."));
                            yield 0;
                        }
                        default -> {
                            ctx.getSource().sendFailure(Component.literal(
                                "Unknown subcommand: " + subcommand + ". Use: status, leaks, explain, panel, narrow"));
                            yield 0;
                        }
                    };
                })
                .then(argument("type", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        LeakStateTracker tracker = LeakStateTracker.getInstance();
                        return SharedSuggestionProvider.suggest(
                            tracker.getAllStates().keySet().stream()
                                .map(sig -> sig.type)
                                .distinct()
                                .toList(),
                            builder);
                    })
                    .then(argument("class", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            String type = StringArgumentType.getString(context, "type");
                            LeakStateTracker tracker = LeakStateTracker.getInstance();
                            return SharedSuggestionProvider.suggest(
                                tracker.getAllStates().keySet().stream()
                                    .filter(sig -> sig.type.equalsIgnoreCase(type))
                                    .map(sig -> sig.targetClass)
                                    .toList(),
                                builder);
                        })
                        .executes(ctx -> {
                            String subcommand = StringArgumentType.getString(ctx, "subcommand");
                            if (!"explain".equalsIgnoreCase(subcommand)) {
                                ctx.getSource().sendFailure(Component.literal(
                                    "Type and class arguments are only valid for 'explain' subcommand"));
                                return 0;
                            }
                            return handleExplain(ctx,
                                StringArgumentType.getString(ctx, "type"),
                                StringArgumentType.getString(ctx, "class"));
                        })))));

        dispatcher.register(literal("ctl").then(narrowToolkitRoot()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> narrowToolkitRoot() {
        return literal("narrow")
            .then(literal("snapshot")
                .executes(ctx -> {
                    NarrowDownStore.get().takeSnapshot(LeakStateTracker.getInstance());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "CTL narrow-down snapshot saved (" + NarrowDownStore.get().getSnapshotSize() + " signatures). "
                            + "Disable mods, restart, ATL refresh, then /ctl narrow compare"), true);
                    return 1;
                }))
            .then(literal("compare")
                .executes(ctx -> {
                    for (String line : NarrowDownStore.get().buildCompareLines(LeakStateTracker.getInstance())) {
                        String l = line;
                        ctx.getSource().sendSuccess(() -> Component.literal(l), false);
                    }
                    return 1;
                }))
            .then(literal("clear")
                .then(literal("snapshot")
                    .executes(ctx -> {
                        NarrowDownStore.get().clearSnapshot();
                        ctx.getSource().sendSuccess(() -> Component.literal("Narrow-down snapshot cleared."), true);
                        return 1;
                    }))
                .then(literal("mods")
                    .executes(ctx -> {
                        NarrowDownStore.get().clearDisabledMods();
                        ctx.getSource().sendSuccess(() -> Component.literal("Narrow-down disabled-mod checklist cleared."), true);
                        return 1;
                    })))
            .then(literal("mod")
                .then(literal("add")
                    .then(argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                            ModList.get().getMods().stream()
                                .map(m -> m.getModId())
                                .collect(Collectors.toList()), b))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            if (NarrowDownStore.get().addDisabledMod(id)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Added to narrow-down checklist: " + id), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("Could not add (duplicate or list full): " + id));
                            return 0;
                        })))
                .then(literal("remove")
                    .then(argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(NarrowDownStore.get().getDisabledMods(), b))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            if (NarrowDownStore.get().removeDisabledMod(id)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Removed from narrow-down checklist: " + id), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("Mod id not in list: " + id));
                            return 0;
                        })))
                .then(literal("list")
                    .executes(ctx -> {
                        List<String> m = NarrowDownStore.get().getDisabledMods();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            m.isEmpty() ? "Narrow-down checklist empty." : "Disabled mods (checklist): " + String.join(", ", m)), false);
                        return 1;
                    })));
    }

    private static boolean trySendDiagnosticsGui(CommandSourceStack source) {
        if (!Config.preferDiagnosticsGui || source.getServer() == null) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        S2CPanelDataPacket payload = CtlPanelPayloadBuilder.build(source.getServer());
        CtlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        if (Config.ctlChatAck) {
            source.sendSuccess(() -> Component.literal("CTL diagnostics panel sent to client."), true);
        }
        return true;
    }

    private static int handlePanel(CommandContext<CommandSourceStack> context) {
        if (!trySendDiagnosticsGui(context.getSource())) {
            context.getSource().sendFailure(Component.literal(
                "Panel is only available for players with the mod on the client, or set prefer_diagnostics_gui=false and use /ctl status."));
            return 0;
        }
        return 1;
    }

    private static int handleStatus(CommandContext<CommandSourceStack> context) {
        if (trySendDiagnosticsGui(context.getSource())) {
            return 1;
        }
        LeakStateTracker tracker = LeakStateTracker.getInstance();
        List<Component> components = ReportFormatter.formatStatusColored(tracker);

        for (Component component : components) {
            context.getSource().sendSuccess(() -> component, false);
        }

        return 1;
    }

    private static int handleLeaks(CommandContext<CommandSourceStack> context) {
        if (trySendDiagnosticsGui(context.getSource())) {
            return 1;
        }
        LeakStateTracker tracker = LeakStateTracker.getInstance();
        List<Component> components = ReportFormatter.formatLeaksColored(tracker, context.getSource().getServer());

        for (Component component : components) {
            context.getSource().sendSuccess(() -> component, false);
        }

        return 1;
    }

    private static int handleExplainAll(CommandContext<CommandSourceStack> context) {
        LeakStateTracker tracker = LeakStateTracker.getInstance();

        if (tracker.getAllStates().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                "No leaks currently tracked. Run /atl force refresh to check for leaks."), false);
            return 1;
        }

        if (trySendDiagnosticsGui(context.getSource())) {
            return 1;
        }

        List<Component> components = ReportFormatter.formatExplainAllColored(tracker, context.getSource().getServer());

        for (Component component : components) {
            context.getSource().sendSuccess(() -> component, false);
        }

        return 1;
    }

    private static int handleExplain(CommandContext<CommandSourceStack> context, String type, String className) {
        if (trySendDiagnosticsGui(context.getSource())) {
            return 1;
        }

        LeakStateTracker tracker = LeakStateTracker.getInstance();

        LeakSignature found = null;
        for (LeakSignature sig : tracker.getAllStates().keySet()) {
            if (sig.type.equalsIgnoreCase(type) && sig.targetClass.equalsIgnoreCase(className)) {
                found = sig;
                break;
            }
        }

        if (found == null) {
            context.getSource().sendFailure(Component.literal("No leak found matching type: " + type + ", class: " + className));
            return 0;
        }

        LeakState state = tracker.getState(found);
        if (state == null) {
            context.getSource().sendFailure(Component.literal("No state found for leak signature"));
            return 0;
        }

        VerdictEngine.Verdict verdict = VerdictEngine.evaluate(found, state, context.getSource().getServer());
        String explanation = ReportFormatter.formatExplain(found, state, verdict);

        String[] lines = explanation.split("\n");
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }

        return 1;
    }
}
