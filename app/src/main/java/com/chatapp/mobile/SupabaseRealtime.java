package com.chatapp.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

/** Minimal Supabase Realtime v2 subscriber for the currently open conversation. */
final class SupabaseRealtime {
    interface Listener {
        void onMessageChange();
        void onState(boolean connected, String detail);
    }

    private static final String TOPIC_PREFIX = "realtime:chat-";
    private static final long[] RETRY_DELAYS = {1000L, 2000L, 5000L, 10000L};
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context context;
    private Listener listener;
    private WebSocket socket;
    private String conversationId = "";
    private String accessToken = "";
    private String topic = "";
    private String joinRef = "1";
    private int ref = 1;
    private int retryCount = 0;
    private int generation = 0;
    private boolean closed = true;
    private boolean joined = false;
    private boolean reconnectScheduled = false;

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (closed || !joined || socket == null) return;
            try {
                JSONArray frame = new JSONArray();
                frame.put(JSONObject.NULL);
                frame.put(String.valueOf(++ref));
                frame.put("phoenix");
                frame.put("heartbeat");
                frame.put(new JSONObject());
                socket.send(frame.toString());
            } catch (Exception ignored) {}
            handler.postDelayed(this, 20000L);
        }
    };

    private final Runnable reconnect = () -> {
        reconnectScheduled = false;
        if (!closed) openSocket();
    };

    void start(Context appContext, String cid, String token, Listener callback) {
        stop();
        context = appContext.getApplicationContext();
        conversationId = cid;
        accessToken = token;
        listener = callback;
        topic = TOPIC_PREFIX + cid;
        closed = false;
        retryCount = 0;
        openSocket();
    }

    void updateAccessToken(String token) {
        if (token == null || token.isEmpty()) return;
        accessToken = token;
        if (joined && socket != null) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("access_token", token);
                JSONArray frame = new JSONArray();
                frame.put(joinRef);
                frame.put(String.valueOf(++ref));
                frame.put(topic);
                frame.put("access_token");
                frame.put(payload);
                socket.send(frame.toString());
            } catch (Exception ignored) {}
        }
    }

    private void openSocket() {
        if (closed || context == null) return;
        handler.removeCallbacks(heartbeat);
        joined = false;
        final int thisGeneration = ++generation;
        try {
            String base = SupabaseConfig.PROJECT_URL.replaceFirst("^https://", "wss://");
            String url = base + "/realtime/v1/websocket?apikey="
                + URLEncoder.encode(SupabaseConfig.PUBLISHABLE_KEY, "UTF-8") + "&vsn=2.0.0";
            Request request = new Request.Builder().url(url).build();
            socket = HTTP.newWebSocket(request, new WebSocketListener() {
                @Override public void onOpen(WebSocket webSocket, Response response) {
                    if (thisGeneration != generation || closed) { webSocket.close(1000, "stale"); return; }
                    sendJoin(webSocket);
                }

                @Override public void onMessage(WebSocket webSocket, String text) {
                    if (thisGeneration != generation || closed) return;
                    handleFrame(text);
                }

                @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                    if (thisGeneration == generation && !closed) scheduleReconnect("Connection closed");
                }

                @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                    if (thisGeneration == generation && !closed) scheduleReconnect(error == null ? "Connection failed" : error.getMessage());
                }
            });
        } catch (Exception e) {
            scheduleReconnect(e.getMessage());
        }
    }

    private void sendJoin(WebSocket webSocket) {
        try {
            JSONObject change = new JSONObject();
            change.put("event", "*");
            change.put("schema", "public");
            change.put("table", "messages");
            change.put("filter", "conversation_id=eq." + conversationId);

            JSONArray changes = new JSONArray();
            changes.put(change);

            JSONObject config = new JSONObject();
            config.put("broadcast", new JSONObject().put("ack", false).put("self", false));
            config.put("presence", new JSONObject().put("enabled", false).put("key", ""));
            config.put("postgres_changes", changes);
            config.put("private", false);

            JSONObject payload = new JSONObject();
            payload.put("config", config);
            payload.put("access_token", accessToken);

            JSONArray frame = new JSONArray();
            frame.put(joinRef);
            frame.put(String.valueOf(++ref));
            frame.put(topic);
            frame.put("phx_join");
            frame.put(payload);
            webSocket.send(frame.toString());
        } catch (Exception e) {
            scheduleReconnect(e.getMessage());
        }
    }

    private void handleFrame(String raw) {
        try {
            JSONArray frame = new JSONArray(raw);
            String event = frame.optString(3, "");
            JSONObject payload = frame.optJSONObject(4);
            if ("phx_reply".equals(event) && payload != null) {
                if ("ok".equals(payload.optString("status"))) {
                    joined = true;
                    retryCount = 0;
                    reconnectScheduled = false;
                    handler.removeCallbacks(heartbeat);
                    handler.postDelayed(heartbeat, 20000L);
                    notifyState(true, "Realtime connected");
                } else {
                    JSONObject response = payload.optJSONObject("response");
                    String reason = response == null ? "Realtime subscription rejected" : response.optString("reason", "Realtime subscription rejected");
                    notifyState(false, reason);
                    scheduleReconnect(reason);
                }
            } else if ("postgres_changes".equals(event)) {
                notifyMessageChange();
            } else if ("phx_error".equals(event) || "phx_close".equals(event)) {
                joined = false;
                handler.removeCallbacks(heartbeat);
                scheduleReconnect("Realtime channel disconnected");
            } else if ("system".equals(event) && payload != null && "error".equals(payload.optString("status"))) {
                notifyState(false, payload.optString("message", "Realtime server error"));
            }
        } catch (Exception ignored) {}
    }

    private void scheduleReconnect(String reason) {
        if (closed || reconnectScheduled) return;
        joined = false;
        handler.removeCallbacks(heartbeat);
        long delay = RETRY_DELAYS[Math.min(retryCount, RETRY_DELAYS.length - 1)];
        retryCount++;
        reconnectScheduled = true;
        notifyState(false, reason == null ? "Realtime reconnecting" : reason);
        handler.postDelayed(reconnect, delay);
    }

    private void notifyMessageChange() {
        Listener callback = listener;
        if (callback != null) handler.post(callback::onMessageChange);
    }

    private void notifyState(boolean connected, String detail) {
        Listener callback = listener;
        if (callback != null) handler.post(() -> callback.onState(connected, detail));
    }

    void stop() {
        closed = true;
        generation++;
        reconnectScheduled = false;
        joined = false;
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(reconnect);
        if (socket != null) {
            socket.close(1000, "conversation closed");
            socket = null;
        }
        listener = null;
        context = null;
        conversationId = "";
        accessToken = "";
        topic = "";
    }
}
