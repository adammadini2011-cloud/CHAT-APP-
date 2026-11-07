package com.chatapp.mobile;

final class ChatItem {
    final String id;
    final String title;
    final boolean group;
    ChatItem(String id, String title, boolean group) {
        this.id = id;
        this.title = title == null || title.trim().isEmpty() ? (group ? "Group" : "Conversation") : title;
        this.group = group;
    }
}
