package com.chatapp.mobile;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class SupabaseAuth {
    static JSONObject refresh(String refreshToken) throws Exception {
        String endpoint = "/auth/v1/token?grant_type=refresh_token";
        URL url = new URL(SupabaseConfig.PROJECT_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) response.append(line);
        } finally { connection.disconnect(); }
        JSONObject result = response.length() == 0 ? new JSONObject() : new JSONObject(response.toString());
        if (code < 200 || code >= 300) throw new Exception(result.optString("message", result.optString("msg", "Session refresh failed (" + code + ")")));
        return result;
    }
    static JSONObject authenticate(String email, String password, boolean signUp) throws Exception {
        String endpoint = signUp ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password";
        URL url = new URL(SupabaseConfig.PROJECT_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }
        JSONObject result = response.length() == 0 ? new JSONObject() : new JSONObject(response.toString());
        if (code < 200 || code >= 300) {
            String message = result.optString("msg", result.optString("message", result.optString("error_description", "Request failed (" + code + ")")));
            throw new Exception(message);
        }
        return result;
    }
}
