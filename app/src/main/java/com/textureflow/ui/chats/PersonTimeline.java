package com.textureflow.ui.chats;

import com.textureflow.data.StoredNotificationEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PersonTimeline {
    final String name;
    final Set<String> apps = new LinkedHashSet<>();
    final List<StoredNotificationEvent> events = new ArrayList<>();
    final List<ChatBubble> demoMessages = new ArrayList<>();
    long latestAt;
    long latestUnreadAt;
    String latestBody = "";
    String latestPackage = "";
    String latestAppLabel = "";
    String latestUnreadPackage = "";
    String latestUnreadAppLabel = "";
    int unreadCount;
    boolean demo;

    PersonTimeline(String name) {
        this.name = name;
    }
}
