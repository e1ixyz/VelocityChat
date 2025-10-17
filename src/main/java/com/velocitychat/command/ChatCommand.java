package com.velocitychat.command;

import com.velocitychat.chat.ChatChannel;
import com.velocitychat.chat.ChatManager;
import com.velocitychat.config.VelocityChatConfig;
import com.velocitychat.util.TextFormatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ChatCommand implements SimpleCommand {
    private static final String STAFF_PERMISSION = "Velocitychat.staff";

    private final ProxyServer proxy;
    private final ChatManager chatManager;
    private final VelocityChatConfig config;

    public ChatCommand(ProxyServer proxy, ChatManager chatManager, VelocityChatConfig config) {
        this.proxy = proxy;
        this.chatManager = chatManager;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            if (source instanceof Player player) {
                sendMessage(source, "channel-current", "&7You are currently in {channel} chat.",
                        Map.of("channel", chatManager.getSpeakChannel(player).getDisplayName()));
            } else {
                sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            }
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "network" -> handleNetwork(source, args);
            case "server" -> handleServer(source);
            case "staff" -> handleStaff(source, args);
            case "listen" -> handleListen(source, args);
            case "ignore" -> handleIgnore(source, args);
            case "alert" -> handleAlert(source, args);
            default -> sendUsage(source);
        }
    }

    private void handleServer(CommandSource source) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }
        switchChannel(player, ChatChannel.SERVER);
    }

    private void handleNetwork(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }

        ChatChannel applied = switchChannel(player, ChatChannel.NETWORK);
        if (applied != ChatChannel.NETWORK) {
            return;
        }

        if (args.length > 1) {
            String message = joinMessage(args, 1);
            chatManager.sendNetworkMessage(player, message);
        }
    }

    private void handleStaff(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }
        if (!player.hasPermission(STAFF_PERMISSION)) {
            sendMessage(source, "not-staff", "&cYou must have staff permissions to do that.");
            return;
        }
        ChatChannel applied = switchChannel(player, ChatChannel.STAFF);
        if (applied != ChatChannel.STAFF) {
            return;
        }

        if (args.length > 1) {
            String message = joinMessage(args, 1);
            chatManager.sendStaffMessage(player, message);
        }
    }

    private void handleListen(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }
        if (args.length < 2) {
            sendListenUsage(player);
            return;
        }

        ChatChannel channel = parseToggleChannel(args[1]);
        if (channel == null) {
            sendListenUsage(player);
            return;
        }

        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            sendMessage(player, "not-staff", "&cYou must have staff permissions to do that.");
            return;
        }

        Boolean desired = null;
        if (args.length >= 3) {
            String state = args[2].toLowerCase(Locale.ROOT);
            if (state.equals("on") || state.equals("enable") || state.equals("true")) {
                desired = true;
            } else if (state.equals("off") || state.equals("disable") || state.equals("false")) {
                desired = false;
            } else {
                sendListenUsage(player);
                return;
            }
        }

        if (desired == null) {
            boolean current = chatManager.isListening(player, channel);
            if (!chatManager.setListening(player, channel, !current)) {
                sendMessage(player, "not-staff", "&cYou must have staff permissions to do that.");
                return;
            }
        } else {
            if (!chatManager.setListening(player, channel, desired)) {
                sendMessage(player, "not-staff", "&cYou must have staff permissions to do that.");
                return;
            }
        }

        boolean newState = chatManager.isListening(player, channel);

        String channelName = channel.getDisplayName();
        if (newState) {
            sendMessage(player, "channel-listen-enabled", "&aYou will now see {channel} chat.",
                    Map.of("channel", channelName));
        } else {
            sendMessage(player, "channel-listen-disabled", "&cYou will no longer see {channel} chat.",
                    Map.of("channel", channelName));
            if (chatManager.getSpeakChannel(player) == channel) {
                sendMessage(player, "channel-switched.server", "&aYou are now talking in {channel} chat.",
                        Map.of("channel", ChatChannel.SERVER.getDisplayName()));
            }
        }
    }

    private void handleIgnore(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }

        if (args.length == 1) {
            Set<String> ignored = chatManager.getIgnoredNames(player);
            if (ignored.isEmpty()) {
                sendMessage(player, "ignored-list-empty", "&7You are not ignoring anyone.");
            } else {
                String list = String.join(", ", ignored);
                sendMessage(player, "ignored-list", "&7You are ignoring: {list}", Map.of("list", list));
            }
            return;
        }

        String targetName = args[1];
        if (player.getUsername().equalsIgnoreCase(targetName)) {
            sendMessage(player, "cannot-ignore-self", "&cYou cannot ignore yourself.");
            return;
        }

        Optional<Player> targetOpt = proxy.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            sendMessage(player, "player-not-found", "&cCould not find player named {target}.",
                    Map.of("target", targetName));
            return;
        }

        Player target = targetOpt.get();

        if (target.hasPermission(STAFF_PERMISSION)) {
            sendMessage(player, "cannot-ignore-staff", "&cYou cannot ignore staff members.");
            return;
        }

        boolean nowIgnoring = chatManager.toggleIgnore(player, target);
        if (nowIgnoring) {
            sendMessage(player, "ignored-add", "&eYou are now ignoring {target}.",
                    Map.of("target", target.getUsername()));
        } else {
            sendMessage(player, "ignored-remove", "&eYou are no longer ignoring {target}.",
                    Map.of("target", target.getUsername()));
        }
    }

    private void handleAlert(CommandSource source, String[] args) {
        if (!source.hasPermission(STAFF_PERMISSION)) {
            sendMessage(source, "not-staff", "&cYou must have staff permissions to do that.");
            return;
        }
        if (args.length < 2) {
            sendMessage(source, "message-needed", "&cYou must provide a message.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        chatManager.sendAlert(source, message);
        sendMessage(source, "alert-sent", "&aAlert sent to the network.");
    }

    private void sendUsage(CommandSource source) {
        List<Component> lines = List.of(
                TextFormatter.colorize("&7/chat server"),
                TextFormatter.colorize("&7/chat network [message]"),
                TextFormatter.colorize("&7/chat staff [message]"),
                TextFormatter.colorize("&7/chat listen <network|staff> [on|off]"),
                TextFormatter.colorize("&7/chat ignore [player]"),
                TextFormatter.colorize("&7/chat alert <message>")
        );
        lines.forEach(source::sendMessage);
    }

    private String joinMessage(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }

    private void sendMessage(CommandSource target, String key, String def) {
        sendMessage(target, key, def, Map.of());
    }

    private void sendMessage(CommandSource target, String key, String def, Map<String, String> placeholders) {
        String template = config.message(key, def);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        target.sendMessage(TextFormatter.colorize(template));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("server", "network", "staff", "listen", "ignore", "alert");
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("server", "network", "staff", "listen", "ignore", "alert").stream()
                    .filter(option -> option.startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (args.length == 2 && "ignore".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            proxy.getAllPlayers().forEach(player -> {
                String name = player.getUsername();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(name);
                }
            });
            return matches;
        }
        if (args.length == 2 && "listen".equalsIgnoreCase(args[0])) {
            return List.of("network", "staff");
        }
        if (args.length == 3 && "listen".equalsIgnoreCase(args[0])) {
            return List.of("on", "off");
        }
        return List.of();
    }

    private ChatChannel switchChannel(Player player, ChatChannel channel) {
        ChatChannel current = chatManager.getSpeakChannel(player);
        if (current == channel) {
            sendMessage(player, "channel-already", "&eYou are already chatting in {channel} chat.",
                    Map.of("channel", channel.getDisplayName()));
            chatManager.setSpeakChannelExclusive(player, channel);
            return channel;
        }
        ChatChannel applied = chatManager.setSpeakChannelExclusive(player, channel);
        sendMessage(player, "channel-switched." + applied.name().toLowerCase(Locale.ROOT),
                "&aYou are now talking in {channel} chat.",
                Map.of("channel", applied.getDisplayName()));
        return applied;
    }

    private ChatChannel parseToggleChannel(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "network" -> ChatChannel.NETWORK;
            case "staff" -> ChatChannel.STAFF;
            default -> null;
        };
    }

    private void sendListenUsage(Player player) {
        player.sendMessage(TextFormatter.colorize("&cUsage: /chat listen <network|staff> [on|off]"));
    }
}
