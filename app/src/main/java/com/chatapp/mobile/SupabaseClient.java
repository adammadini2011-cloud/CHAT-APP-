package com.chatapp.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class SupabaseClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS).build();
    private SupabaseClient() {}
    static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
    static String request(String method, String path, String token, JSONObject body, String prefer) throws Exception {
        RequestBody requestBody = body == null ? null : RequestBody.create(JSON, body.toString());
        Request.Builder builder = new Request.Builder()
            .url(SupabaseConfig.PROJECT_URL + path)
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json");
        if (prefer != null) builder.header("Prefer", prefer);
        if ("GET".equals(method) || "DELETE".equals(method)) builder.method(method, null);
        else builder.method(method, requestBody == null ? RequestBody.create(null, new byte[0]) : requestBody);
        try (Response response = HTTP.newCall(builder.build()).execute()) {
            String value = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String detail = value;
                try { JSONObject error = new JSONObject(value); detail = error.optString("message", error.optString("msg", error.optString("error", value))); } catch (Exception ignored) {}
                throw new Exception("Server (" + response.code() + "): " + detail);
            }
            return value;
        }
    }
    static JSONArray getArray(String path, String token) throws Exception {
        return new JSONArray(request("GET", path, token, null, null));
    }
    static JSONArray post(String path, String token, JSONObject body) throws Exception {
        String value = request("POST", path, token, body, "return=representation");
        return value.isEmpty() ? new JSONArray() : new JSONArray(value);
    }
    static void upsert(String path, String token, JSONObject body) throws Exception {
        request("POST", path, token, body, "resolution=merge-duplicates,return=minimal");
    }
    static void patch(String path, String token, JSONObject body) throws Exception {
        request("PATCH", path, token, body, "return=minimal");
    }
    static void delete(String path, String token) throws Exception {
        request("DELETE", path, token, null, "return=minimal");
    }
}
