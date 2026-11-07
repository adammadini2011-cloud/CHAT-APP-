package com.chatapp.mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText emailInput, passwordInput;
    private Button loginButton, registerButton;
    private ProgressBar progressBar;
    private TextView statusText;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        loginButton.setOnClickListener(v -> submit(false));
        registerButton.setOnClickListener(v -> submit(true));
    }

    private void submit(boolean signUp) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        statusText.setText("");
        if (email.isEmpty() || password.length() < 6) {
            statusText.setText("Enter a valid email and a password of at least 6 characters.");
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject result = SupabaseAuth.authenticate(email, password, signUp);
                String token = result.optString("access_token", "");
                String userId = result.optJSONObject("user") == null ? "" : result.optJSONObject("user").optString("id", "");
                if (!token.isEmpty()) {
                    getSharedPreferences("chat_session", MODE_PRIVATE).edit()
                        .putString("access_token", token).putString("user_id", userId)
                        .putString("email", email).apply();
                }
                runOnUiThread(() -> {
                    setBusy(false);
                    if (token.isEmpty()) {
                        statusText.setText(signUp
                            ? "Account request accepted. Check your email if confirmation is enabled, then sign in."
                            : "No active session was returned. Check your account and try again.");
                    } else {
                        statusText.setText("Signed in successfully. Inbox and messaging screens are the next implementation step.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    statusText.setText(e.getMessage() == null ? "Could not connect. Try again." : e.getMessage());
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
