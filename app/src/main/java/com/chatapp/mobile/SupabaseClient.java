package com.chatapp.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class SupabaseClient {
    private SupabaseClient() {}
    static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
    static String request(String method, String path, String token, JSONObject body, String prefer) throws Exception {
        URL url = new URL(SupabaseConfig.PROJECT_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder result = new StringBuilder();
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
            }
        }
        c.disconnect();
        if (code < 200 || code >= 300) {
            String message = result.toString();
            try {
                JSONObject error = new JSONObject(message);
                message = error.optString("message", error.optString("msg", error.optString("error", message)));
            } catch (Exception ignored) {}
            throw new Exception("Server (" + code + "): " + message);
        }
        return result.toString();
    }
    static JSONArray getArray(String path, String token) throws Exception {
        String value = request("GET", path, token, null, null);
        return new JSONArray(value);
    }
    static JSONArray post(String path, String token, JSONObject body) throws Exception {
        String value = request("POST", path, token, body, "return=representation");
        return value.isEmpty() ? new JSONArray() : new JSONArray(value);
    }
    static void patch(String path, String token, JSONObject body) throws Exception {
        request("PATCH", path, token, body, "return=minimal");
    }
}
