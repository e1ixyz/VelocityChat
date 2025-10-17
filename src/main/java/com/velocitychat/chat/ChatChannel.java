package com.velocitychat.chat;

public enum ChatChannel {
    SERVER("Server"),
    NETWORK("Network"),
    STAFF("Staff");

    private final String displayName;

    ChatChannel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
