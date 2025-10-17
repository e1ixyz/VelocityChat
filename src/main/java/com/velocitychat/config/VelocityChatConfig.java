package com.velocitychat.config;

import com.velocitychat.chat.ChatChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and exposes configuration and message templates.
 */
public final class VelocityChatConfig {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Path dataDirectory;
    private final Logger logger;

    private ChatChannel defaultChannel = ChatChannel.SERVER;
    private final Map<ChatChannel, String> prefixes = new EnumMap<>(ChatChannel.class);
    private final Map<ChatChannel, String> formats = new EnumMap<>(ChatChannel.class);
    private final Map<String, String> messages = new HashMap<>();
    private String alertPrefix;
    private String alertFormat;
    private String privateSendFormat;
    private String privateReceiveFormat;
    private boolean forceChannelIntercept;

    public VelocityChatConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void reload() {
        Path configPath = dataDirectory.resolve("config.yml");
        ensureDefaultConfig(configPath);

        Yaml yaml = new Yaml();
        Map<String, Object> root;

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map<?, ?> map) {
                root = castMap(map);
            } else {
                root = new HashMap<>();
            }
        } catch (IOException ex) {
            logger.error("Failed to read config.yml", ex);
            root = new HashMap<>();
        }

        loadChannels(root);
        loadSettings(root);
        loadMessages(root);
    }

    private void loadChannels(Map<String, Object> root) {
        String defaultChannelName = string(root, "channels.default", "SERVER");
        defaultChannel = parseChannel(defaultChannelName, ChatChannel.SERVER);

        prefixes.put(ChatChannel.NETWORK, string(root, "channels.prefixes.network", "&b[Network]"));
        prefixes.put(ChatChannel.STAFF, string(root, "channels.prefixes.staff", "&c[Staff]"));
        prefixes.put(ChatChannel.SERVER, "");

        formats.put(ChatChannel.NETWORK, string(root, "channels.formats.network", "{prefix} {player}: {message}"));
        formats.put(ChatChannel.STAFF, string(root, "channels.formats.staff", "{prefix} {player}: {message}"));
        formats.put(ChatChannel.SERVER, "{player}: {message}");

        alertPrefix = string(root, "channels.alert.prefix", "&4[Alert]");
        alertFormat = string(root, "channels.alert.format", "{prefix} {message}");

        privateSendFormat = string(root, "private-messages.send", "&d[To {target}] {message}");
        privateReceiveFormat = string(root, "private-messages.receive", "&d[From {sender}] {message}");
    }

    private void loadSettings(Map<String, Object> root) {
        forceChannelIntercept = bool(root, "settings.force-channel-intercept", false);
    }

    @SuppressWarnings("unchecked")
    private void loadMessages(Map<String, Object> root) {
        messages.clear();
        Map<String, Object> messageRoot = (Map<String, Object>) find(root, "messages");
        if (messageRoot != null) {
            flatten(messageRoot, "messages", messages);
        }
    }

    private void ensureDefaultConfig(Path configPath) {
        if (Files.exists(configPath)) {
            return;
        }
        try {
            Files.createDirectories(dataDirectory);
            try (InputStream in = getResourceStream("/config.yml")) {
                if (in == null) {
                    logger.warn("Default config.yml missing from jar resources.");
                    return;
                }
                Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            logger.error("Unable to save default config.yml", ex);
        }
    }

    private InputStream getResourceStream(String name) {
        return getClass().getResourceAsStream(name);
    }

    public ChatChannel getDefaultChannel() {
        return defaultChannel;
    }

    public String getPrefix(ChatChannel channel) {
        return prefixes.getOrDefault(channel, "");
    }

    public String getFormat(ChatChannel channel) {
        return formats.getOrDefault(channel, "{player}: {message}");
    }

    public String getAlertPrefix() {
        return alertPrefix;
    }

    public String getAlertFormat() {
        return alertFormat;
    }

    public String getPrivateSendFormat() {
        return privateSendFormat;
    }

    public String getPrivateReceiveFormat() {
        return privateReceiveFormat;
    }

    public boolean isForceChannelIntercept() {
        return forceChannelIntercept;
    }

    public String message(String key, String def) {
        return messages.getOrDefault("messages." + key, def);
    }

    public Component componentMessage(String key, String def) {
        return LEGACY.deserialize(message(key, def));
    }

    private static ChatChannel parseChannel(String name, ChatChannel fallback) {
        try {
            return ChatChannel.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static void flatten(Map<String, Object> source, String prefix, Map<String, String> output) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = castMap(map);
                flatten(child, key, output);
            } else if (value != null) {
                output.put(key, String.valueOf(value));
            }
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return typed;
    }

    private static Object find(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private static String string(Map<String, Object> root, String path, String def) {
        Object value = find(root, path);
        return value == null ? def : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> root, String path, boolean def) {
        Object value = find(root, path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return def;
    }
}
