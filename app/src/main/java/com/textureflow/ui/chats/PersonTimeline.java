package com.textureflow.ui.chats;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PersonTimeline {
    final String name;
    final String personKey;
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
    AttentionAssessment assessment;

    PersonTimeline(String name) {
        this.name = name;
        this.personKey = ChatListPresenter.normalizePersonKey(name);
    }
}
