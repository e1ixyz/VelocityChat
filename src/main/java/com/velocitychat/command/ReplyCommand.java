package com.velocitychat.command;

import com.velocitychat.chat.ChatManager;
import com.velocitychat.config.VelocityChatConfig;
import com.velocitychat.util.TextFormatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReplyCommand implements SimpleCommand {
    private final ChatManager chatManager;
    private final VelocityChatConfig config;

    public ReplyCommand(ChatManager chatManager, VelocityChatConfig config) {
        this.chatManager = chatManager;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            sendMessage(source, "must-be-player", "&cYou must be a player to use that command.");
            return;
        }

        Optional<String> lastName = chatManager.getLastConversationPartnerName(player);
        if (lastName.isEmpty()) {
            sendMessage(player, "reply-no-target", "&cNo one has messaged you yet.");
            return;
        }

        Optional<Player> partnerOpt = chatManager.getLastConversationPartner(player);
        if (partnerOpt.isEmpty()) {
            chatManager.clearLastConversation(player);
            sendMessage(player, "reply-target-offline", "&c{target} is no longer online.",
                    Map.of("target", lastName.get()));
            return;
        }

        if (args.length == 0) {
            sendMessage(player, "message-needed", "&cYou must provide a message.");
            return;
        }

        String message = String.join(" ", args);
        chatManager.sendPrivateMessage(player, partnerOpt.get(), message);
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
        return List.of();
    }
}
