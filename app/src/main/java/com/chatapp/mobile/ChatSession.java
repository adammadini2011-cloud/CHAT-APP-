package com.chatapp.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;

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
    static synchronized String validToken(Context c) {
        String access=token(c), refresh=refresh(c);
        if(access.isEmpty()||refresh.isEmpty())return access;
        try{
            String[] parts=access.split("\\.");
            if(parts.length>1){
                byte[] data=Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
                long exp=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8)).optLong("exp",0L);
                if(exp>0&&exp<(System.currentTimeMillis()/1000L)+120L){
                    JSONObject renewed=SupabaseAuth.refresh(refresh);
                    String next=renewed.optString("access_token","");
                    if(!next.isEmpty())save(c,next,renewed.optString("refresh_token",refresh),userId(c),email(c));
                    if(!next.isEmpty())access=next;
                }
            }
        }catch(Exception ignored){}
        return access;
    }
    static void clear(Context c) { prefs(c).edit().clear().apply(); }
}
