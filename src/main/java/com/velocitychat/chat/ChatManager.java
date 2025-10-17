package com.velocitychat.chat;

import com.velocitychat.config.VelocityChatConfig;
import com.velocitychat.util.TextFormatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Maintains channel state, ignore lists, and message dispatch.
 */
public final class ChatManager {
    private static final String STAFF_PERMISSION = "Velocitychat.staff";

    private final ProxyServer proxy;
    private final VelocityChatConfig config;

    private final Map<UUID, ChatPreferences> preferences = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> ignoreLists = new ConcurrentHashMap<>();
    private final Map<UUID, ConversationContext> lastConversation = new ConcurrentHashMap<>();

    public ChatManager(ProxyServer proxy, VelocityChatConfig config) {
        this.proxy = proxy;
        this.config = config;
    }

    public void initializePlayer(Player player, ChatChannel defaultChannel) {
        if (defaultChannel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            defaultChannel = ChatChannel.SERVER;
        }
        setSpeakChannelExclusive(player, defaultChannel);
    }

    public ChatChannel getSpeakChannel(Player player) {
        return getPreferences(player).getSpeakChannel();
    }

    public void setSpeakChannel(Player player, ChatChannel channel) {
        ChatPreferences prefs = getPreferences(player);
        applySpeakChannel(player, prefs, channel);
    }

    public ChatChannel setSpeakChannelExclusive(Player player, ChatChannel channel) {
        ChatPreferences prefs = getPreferences(player);
        ChatChannel applied = applySpeakChannel(player, prefs, channel);
        prefs.clearListening();
        if (applied != ChatChannel.SERVER) {
            prefs.enableListening(applied);
        }
        return applied;
    }

    public boolean toggleListening(Player player, ChatChannel channel) {
        if (channel == ChatChannel.SERVER) {
            return true;
        }
        ChatPreferences prefs = getPreferences(player);
        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            return false;
        }
        boolean enable = !prefs.isListening(channel);
        setListeningInternal(player, prefs, channel, enable);
        return enable;
    }

    public boolean setListening(Player player, ChatChannel channel, boolean enable) {
        if (channel == ChatChannel.SERVER) {
            return true;
        }
        ChatPreferences prefs = getPreferences(player);
        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            return false;
        }
        setListeningInternal(player, prefs, channel, enable);
        return true;
    }

    public boolean isListening(Player player, ChatChannel channel) {
        if (channel == ChatChannel.SERVER) {
            return true;
        }
        ChatPreferences prefs = getPreferences(player);
        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            return false;
        }
        return prefs.isListening(channel);
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        preferences.remove(id);
        ignoreLists.remove(id);
        lastConversation.entrySet().removeIf(entry -> entry.getKey().equals(id)
                || (entry.getValue().partnerId != null && entry.getValue().partnerId.equals(id)));
    }

    public boolean toggleIgnore(Player owner, Player target) {
        Map<UUID, String> ignored = ignoreLists.computeIfAbsent(owner.getUniqueId(), key -> new ConcurrentHashMap<>());
        UUID targetId = target.getUniqueId();
        if (ignored.containsKey(targetId)) {
            ignored.remove(targetId);
            return false;
        }
        ignored.put(targetId, target.getUsername());
        return true;
    }

    public boolean isIgnoring(UUID owner, UUID target) {
        Map<UUID, String> ignored = ignoreLists.get(owner);
        return ignored != null && ignored.containsKey(target);
    }

    public Set<String> getIgnoredNames(Player owner) {
        Map<UUID, String> ignored = ignoreLists.get(owner.getUniqueId());
        if (ignored == null || ignored.isEmpty()) {
            return Collections.emptySet();
        }
        return ignored.values().stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public void sendNetworkMessage(Player sender, String rawMessage) {
        dispatchToChannel(sender, rawMessage, ChatChannel.NETWORK);
    }

    public void sendStaffMessage(Player sender, String rawMessage) {
        dispatchToChannel(sender, rawMessage, ChatChannel.STAFF);
    }

    private void dispatchToChannel(Player sender, String rawMessage, ChatChannel channel) {
        String serverName = sender.getCurrentServer()
                .map(ServerConnection::getServerInfo)
                .map(ServerInfo::getName)
                .orElse("Unknown");

        String format = config.getFormat(channel);
        String prefix = config.getPrefix(channel);
        Component component = TextFormatter.format(format, Map.of(
                "prefix", prefix,
                "player", sender.getUsername(),
                "message", rawMessage,
                "server", serverName
        ));

        UUID senderId = sender.getUniqueId();
        proxy.getAllPlayers().forEach(player -> {
            if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
                return;
            }
            boolean listening = isListening(player, channel);
            if (!listening && !player.getUniqueId().equals(senderId)) {
                return;
            }
            if (isIgnoring(player.getUniqueId(), sender.getUniqueId())) {
                return;
            }
            player.sendMessage(component);
        });
    }

    public void sendAlert(CommandSource source, String rawMessage) {
        String senderName = resolveName(source);
        Component component = TextFormatter.format(config.getAlertFormat(), Map.of(
                "prefix", config.getAlertPrefix(),
                "message", rawMessage,
                "sender", senderName
        ));

        proxy.getAllPlayers().forEach(player -> player.sendMessage(component));
        proxy.getConsoleCommandSource().sendMessage(component);
    }

    public void sendPrivateMessage(CommandSource source, Player target, String rawMessage) {
        Player sender = source instanceof Player player ? player : null;
        String senderName = sender != null ? sender.getUsername() : resolveName(source);
        String targetName = target.getUsername();

        if (sender != null && isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
            source.sendMessage(TextFormatter.colorize(config.message("ignored-you", "&c{target} is ignoring you.")
                    .replace("{target}", targetName)));
            return;
        }

        Component sendComponent = TextFormatter.format(config.getPrivateSendFormat(), Map.of(
                "sender", senderName,
                "target", targetName,
                "message", rawMessage
        ));
        Component receiveComponent = TextFormatter.format(config.getPrivateReceiveFormat(), Map.of(
                "sender", senderName,
                "target", targetName,
                "message", rawMessage
        ));

        if (sender != null) {
            sender.sendMessage(sendComponent);
        } else {
            source.sendMessage(sendComponent);
        }
        target.sendMessage(receiveComponent);
        if (sender != null) {
            recordConversation(sender.getUniqueId(), target.getUniqueId(), targetName);
        }
        recordConversation(target.getUniqueId(), sender != null ? sender.getUniqueId() : null, senderName);
    }

    public Optional<Player> getLastConversationPartner(Player player) {
        ConversationContext context = lastConversation.get(player.getUniqueId());
        if (context == null) {
            return Optional.empty();
        }
        Optional<Player> partner = proxy.getPlayer(context.partnerId);
        if (partner.isEmpty()) {
            return Optional.empty();
        }
        return partner;
    }

    public Optional<String> getLastConversationPartnerName(Player player) {
        ConversationContext context = lastConversation.get(player.getUniqueId());
        return context == null ? Optional.empty() : Optional.ofNullable(context.partnerName);
    }

    public void clearLastConversation(Player player) {
        lastConversation.remove(player.getUniqueId());
    }

    private String resolveName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }
        return "Console";
    }

    private ChatPreferences getPreferences(Player player) {
        return preferences.computeIfAbsent(player.getUniqueId(), id -> new ChatPreferences());
    }

    private void recordConversation(UUID owner, UUID partner, String partnerName) {
        if (owner == null || partner == null) {
            return;
        }
        lastConversation.put(owner, new ConversationContext(partner, partnerName));
    }

    private ChatChannel applySpeakChannel(Player player, ChatPreferences prefs, ChatChannel channel) {
        if (channel == ChatChannel.STAFF && !player.hasPermission(STAFF_PERMISSION)) {
            prefs.setSpeakChannel(ChatChannel.SERVER);
        } else {
            prefs.setSpeakChannel(channel);
        }
        return prefs.getSpeakChannel();
    }

    private void setListeningInternal(Player player, ChatPreferences prefs, ChatChannel channel, boolean enable) {
        if (enable) {
            prefs.enableListening(channel);
        } else {
            prefs.disableListening(channel);
            if (prefs.getSpeakChannel() == channel) {
                prefs.setSpeakChannel(ChatChannel.SERVER);
            }
        }
    }

    private static final class ChatPreferences {
        private ChatChannel speakChannel = ChatChannel.SERVER;
        private final EnumSet<ChatChannel> listening = EnumSet.noneOf(ChatChannel.class);

        ChatChannel getSpeakChannel() {
            return speakChannel;
        }

        void setSpeakChannel(ChatChannel channel) {
            this.speakChannel = channel;
        }

        boolean isListening(ChatChannel channel) {
            return listening.contains(channel);
        }

        void enableListening(ChatChannel channel) {
            listening.add(channel);
        }

        void disableListening(ChatChannel channel) {
            listening.remove(channel);
        }

        void clearListening() {
            listening.clear();
        }
    }

    private static final class ConversationContext {
        private final UUID partnerId;
        private final String partnerName;

        private ConversationContext(UUID partnerId, String partnerName) {
            this.partnerId = partnerId;
            this.partnerName = partnerName;
        }
    }
}
