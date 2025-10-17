package com.velocitychat;

import com.google.inject.Inject;
import com.velocitychat.chat.ChatChannel;
import com.velocitychat.chat.ChatManager;
import com.velocitychat.command.ChatCommand;
import com.velocitychat.command.MessageCommand;
import com.velocitychat.command.ReplyCommand;
import com.velocitychat.config.VelocityChatConfig;
import com.velocitychat.util.TextFormatter;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "velocitychat",
        name = "VelocityChat",
        version = "1.0.0-SNAPSHOT",
        authors = { "elimcgehee" }
)
public final class VelocityChatPlugin {
    private static final String STAFF_PERMISSION = "Velocitychat.staff";

    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityChatConfig config;
    private final ChatManager chatManager;
    private boolean secureChatWarningLogged;

    @Inject
    public VelocityChatPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.config = new VelocityChatConfig(dataDirectory, logger);
        this.chatManager = new ChatManager(proxy, config);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        config.reload();
        registerCommands();
        logger.info("VelocityChat enabled.");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        ChatChannel channel = config.getDefaultChannel();
        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            channel = ChatChannel.SERVER;
        }
        chatManager.initializePlayer(player, channel);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        chatManager.remove(event.getPlayer());
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatChannel channel = chatManager.getSpeakChannel(player);
        String plainMessage = event.getMessage();
        boolean canIntercept = canInterceptSignedChat(player);

        if (channel == ChatChannel.STAFF) {
            if (!player.hasPermission(STAFF_PERMISSION)) {
                if (canIntercept) {
                    suppressChat(event);
                }
                chatManager.setSpeakChannelExclusive(player, ChatChannel.SERVER);
                player.sendMessage(TextFormatter.colorize(config.message("not-staff", "&cYou must have staff permissions to do that.")));
                return;
            }
            if (!canIntercept) {
                handleSecureChatRestriction(player);
                return;
            }
            suppressChat(event);
            chatManager.sendStaffMessage(player, plainMessage);
            return;
        }

        if (channel == ChatChannel.NETWORK) {
            if (!canIntercept) {
                handleSecureChatRestriction(player);
                return;
            }
            suppressChat(event);
            chatManager.sendNetworkMessage(player, plainMessage);
        }
    }

    private void registerCommands() {
        CommandManager commandManager = proxy.getCommandManager();
        ChatCommand chatCommand = new ChatCommand(proxy, chatManager, config);
        MessageCommand messageCommand = new MessageCommand(proxy, chatManager, config);
        ReplyCommand replyCommand = new ReplyCommand(chatManager, config);

        commandManager.register(
                commandManager.metaBuilder("chat")
                        .plugin(this)
                        .build(),
                chatCommand
        );

        commandManager.register(
                commandManager.metaBuilder("msg")
                        .plugin(this)
                        .aliases("message", "tell", "whisper", "w")
                        .build(),
                messageCommand
        );

        commandManager.register(
                commandManager.metaBuilder("r")
                        .plugin(this)
                        .aliases("reply", "report")
                        .build(),
                replyCommand
        );
    }

    private boolean canInterceptSignedChat(Player player) {
        if (config.isForceChannelIntercept()) {
            return true;
        }
        IdentifiedKey key = player.getIdentifiedKey();
        if (key == null) {
            return true;
        }
        return key.getKeyRevision().compareTo(IdentifiedKey.Revision.LINKED_V2) < 0;
    }

    private void suppressChat(PlayerChatEvent event) {
        if (config.isForceChannelIntercept()) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        } else {
            event.setResult(PlayerChatEvent.ChatResult.message(""));
        }
    }

    private void handleSecureChatRestriction(Player player) {
        chatManager.setSpeakChannelExclusive(player, ChatChannel.SERVER);
        player.sendMessage(TextFormatter.colorize(config.message(
                "secure-chat-restricted",
                "&cSecure chat is enabled on this server, so VelocityChat channels are unavailable. You have been switched back to server chat."
        )));
        if (!secureChatWarningLogged) {
            secureChatWarningLogged = true;
            logger.warn("VelocityChat cannot intercept chat messages while secure chat is enforced "
                    + "(Minecraft 1.19.1+). Set enforce-secure-profile=false on backend servers to allow channel switching.");
        }
    }
}
