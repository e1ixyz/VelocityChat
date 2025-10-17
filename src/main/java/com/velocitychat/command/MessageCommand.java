package com.velocitychat.command;

import com.velocitychat.chat.ChatManager;
import com.velocitychat.config.VelocityChatConfig;
import com.velocitychat.util.TextFormatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MessageCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final ChatManager chatManager;
    private final VelocityChatConfig config;

    public MessageCommand(ProxyServer proxy, ChatManager chatManager, VelocityChatConfig config) {
        this.proxy = proxy;
        this.chatManager = chatManager;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 2) {
            sendMessage(source, "message-needed", "&cYou must provide a message.");
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = proxy.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            sendMessage(source, "player-not-found", "&cCould not find player named {target}.",
                    Map.of("target", targetName));
            return;
        }

        Player target = targetOpt.get();

        if (source instanceof Player sender && sender.getUniqueId().equals(target.getUniqueId())) {
            sendMessage(source, "cannot-message-self", "&cYou cannot message yourself.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        chatManager.sendPrivateMessage(source, target, message);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .collect(java.util.stream.Collectors.toList());
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            proxy.getAllPlayers().forEach(player -> {
                String name = player.getUsername();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                }
            });
            return names;
        }
        return List.of();
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
}
