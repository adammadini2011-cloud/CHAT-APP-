package com.chatapp.mobile;

import android.content.Context;
import android.content.SharedPreferences;

final class ChatSession {
    private static final String PREFS = "chat_session";
    private ChatSession() {}
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static String token(Context c) { return prefs(c).getString("access_token", ""); }
    static String refresh(Context c) { return prefs(c).getString("refresh_token", ""); }
    static String userId(Context c) { return prefs(c).getString("user_id", ""); }
    static String email(Context c) { return prefs(c).getString("email", ""); }
    static void save(Context c, String access, String refresh, String id, String email) {
        prefs(c).edit().putString("access_token", access).putString("refresh_token", refresh)
            .putString("user_id", id).putString("email", email).apply();
    }
    static void clear(Context c) { prefs(c).edit().clear().apply(); }
}
