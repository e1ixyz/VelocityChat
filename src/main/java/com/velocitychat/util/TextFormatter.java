package com.velocitychat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * Utility for applying simple placeholder replacement and translating legacy color codes.
 */
public final class TextFormatter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private TextFormatter() {
        throw new IllegalStateException("Utility class");
    }

    public static Component colorize(String input) {
        return LEGACY.deserialize(input);
    }

    public static Component format(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LEGACY.deserialize(result);
    }

    public static Component format(String template, String key, String value) {
        return format(template, Map.of(key, value));
    }
}
