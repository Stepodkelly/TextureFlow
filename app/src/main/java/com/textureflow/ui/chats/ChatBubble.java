package com.textureflow.ui.chats;

final class ChatBubble {
    final boolean outbound;
    final String body;
    final long at;

    ChatBubble(boolean outbound, String body, long at) {
        this.outbound = outbound;
        this.body = body;
        this.at = at;
    }
}
