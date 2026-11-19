package com.chatapp.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CallActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 9301;
    private static final int SCREEN_CAPTURE_REQUEST = 9302;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String callId = "", uid = "", token = "";
    private boolean caller = false, videoCall = false, closed = false, polling = false;
    private boolean microphoneEnabled = true, cameraEnabled = true, speakerEnabled = true;
    private TextView status;
    private Button muteButton, cameraButton, speakerButton, shareButton;
    private SurfaceViewRenderer localRenderer, remoteRenderer;
    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private SurfaceTextureHelper textureHelper;
    private CameraVideoCapturer cameraCapturer;
    private ScreenCapturerAndroid screenCapturer;
    private VideoCapturer activeCapturer;
    private MediaProjectionManager projectionManager;
    private final List<String> seenSignals = new ArrayList<>();
    private final List<IceCandidate> pendingIce = new ArrayList<>();
    private boolean remoteDescriptionSet = false;
    private final Runnable callTimeout = () -> {
        if (!caller || closed) return;
        io.execute(() -> {
            try {
                JSONArray rows=SupabaseClient.getArray("/rest/v1/call_sessions?select=status&id=eq."+SupabaseClient.enc(callId),token);
                if(rows.length()>0 && "ringing".equals(rows.getJSONObject(0).optString("status"))){
                    JSONObject patch=new JSONObject();patch.put("status","missed");patch.put("ended_at",nowIso());
                    SupabaseClient.request("PATCH","/rest/v1/call_sessions?id=eq."+SupabaseClient.enc(callId),token,patch,null);
                    runOnUiThread(()->{toast("No answer.");endCall(false);});
                }
            } catch(Exception ignored) {}
        });
    };

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void toast(String value) {
        runOnUiThread(() -> Toast.makeText(this, value, Toast.LENGTH_LONG).show());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        callId = getIntent().getStringExtra("call_id");
        caller = getIntent().getBooleanExtra("caller", false);
        videoCall = "video".equals(getIntent().getStringExtra("call_type"));
        uid = ChatSession.userId(this);
        token = ChatSession.validToken(this);
        projectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (callId == null || callId.isEmpty() || uid.isEmpty() || token.isEmpty()) {
            finish();
            return;
        }
        buildUi();
        requestCallPermissions();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF081321);
        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this);
        status.setText(caller ? "Calling…" : "Connecting…");
        status.setTextColor(0xFFEAF3FF);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, dp(64), Gravity.CENTER);
        stage.addView(status, statusParams);

        remoteRenderer = new SurfaceViewRenderer(this);
        remoteRenderer.setVisibility(videoCall ? View.VISIBLE : View.GONE);
        stage.addView(remoteRenderer, new FrameLayout.LayoutParams(-1, -1));

        localRenderer = new SurfaceViewRenderer(this);
        localRenderer.setVisibility(videoCall ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams localParams = new FrameLayout.LayoutParams(dp(116), dp(164), Gravity.TOP | Gravity.END);
        localParams.setMargins(dp(12), dp(14), dp(14), dp(12));
        stage.addView(localRenderer, localParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(6), dp(12), dp(6), dp(20));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(88)));

        muteButton = control("Mute");
        speakerButton = control("Speaker");
        cameraButton = control("Camera");
        shareButton = control("Share screen");
        Button end = control("End call");
        controls.addView(muteButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        controls.addView(speakerButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        if (videoCall) {
            controls.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(54), 1));
            controls.addView(shareButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        controls.addView(end, new LinearLayout.LayoutParams(0, dp(54), 1));
        muteButton.setOnClickListener(v -> toggleMute());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        cameraButton.setOnClickListener(v -> toggleCamera());
        shareButton.setOnClickListener(v -> startScreenShare());
        end.setOnClickListener(v -> endCall(true));
        setContentView(root);
    }

    private Button control(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            "End call".equals(label) ? 0xFFC63858 : 0xFF1D3854
        ));
        return b;
    }

    private void requestCallPermissions() {
        List<String> required = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            required.add(Manifest.permission.RECORD_AUDIO);
        if (videoCall && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            required.add(Manifest.permission.CAMERA);
        if (!required.isEmpty()) {
            ActivityCompat.requestPermissions(this, required.toArray(new String[0]), PERMISSION_REQUEST);
        } else {
            initializeCall();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != PERMISSION_REQUEST) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                toast("Microphone/camera permission is required for this call.");
                finish();
                return;
            }
        }
        initializeCall();
    }

    private void initializeCall() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                    .createInitializationOptions()
            );
            egl = EglBase.create();
            remoteRenderer.init(egl.getEglBaseContext(), null);
            localRenderer.init(egl.getEglBaseContext(), null);
            localRenderer.setMirror(true);

            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();

            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            peer = factory.createPeerConnection(config, new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    runOnUiThread(() -> {
                        if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED)
                            status.setText("Connected");
                        else if (state == PeerConnection.IceConnectionState.DISCONNECTED)
                            status.setText("Reconnecting…");
                        else if (state == PeerConnection.IceConnectionState.FAILED)
                            status.setText("Connection failed — check network");
                    });
                }
                @Override public void onIceConnectionReceivingChange(boolean receiving) {}
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
                @Override public void onIceCandidate(IceCandidate candidate) {
                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("sdpMid", candidate.sdpMid);
                        payload.put("sdpMLineIndex", candidate.sdpMLineIndex);
                        payload.put("sdp", candidate.sdp);
                        sendSignal("ice", payload);
                    } catch (Exception ignored) {}
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
                @Override public void onAddStream(MediaStream stream) {
                    if (stream.videoTracks.size() > 0) {
                        VideoTrack track = stream.videoTracks.get(0);
                        runOnUiThread(() -> track.addSink(remoteRenderer));
                    }
                }
                @Override public void onRemoveStream(MediaStream stream) {}
                @Override public void onDataChannel(org.webrtc.DataChannel channel) {}
                @Override public void onRenegotiationNeeded() {}
                @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                    if (receiver.track() instanceof VideoTrack) {
                        VideoTrack track = (VideoTrack)receiver.track();
                        runOnUiThread(() -> track.addSink(remoteRenderer));
                    }
                }
            });
            if (peer == null) throw new IllegalStateException("Could not create WebRTC peer.");

            audioSource = factory.createAudioSource(new MediaConstraints());
            audioTrack = factory.createAudioTrack("nexora-audio", audioSource);
            peer.addTrack(audioTrack, Collections.singletonList("nexora-stream"));

            if (videoCall) initializeCamera();
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audio.setSpeakerphoneOn(videoCall);
            speakerEnabled = videoCall;
            speakerButton.setText(speakerEnabled ? "Speaker on" : "Speaker");
            status.setText(caller ? "Calling…" : "Waiting for caller…");
            if (caller) { createOffer(); handler.postDelayed(callTimeout, 45000L); }
            handler.post(signalPoll);
        } catch (Exception e) {
            toast("Could not start WebRTC: " + e.getMessage());
            endCall(false);
        }
    }

    private void initializeCamera() {
        CameraEnumerator enumerator = Camera2Enumerator.isSupported(this)
            ? new Camera2Enumerator(this) : new Camera1Enumerator(true);
        cameraCapturer = createCameraCapturer(enumerator);
        if (cameraCapturer == null) throw new IllegalStateException("No camera is available.");
        videoSource = factory.createVideoSource(false);
        textureHelper = SurfaceTextureHelper.create("NEXORA-Camera", egl.getEglBaseContext());
        cameraCapturer.initialize(textureHelper, this, videoSource.getCapturerObserver());
        cameraCapturer.startCapture(1280, 720, 24);
        activeCapturer = cameraCapturer;
        videoTrack = factory.createVideoTrack("nexora-video", videoSource);
        videoTrack.addSink(localRenderer);
        peer.addTrack(videoTrack, Collections.singletonList("nexora-stream"));
    }

    private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isBackFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }

    private void createOffer() {
        if (peer == null || closed) return;
        peer.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(new SimpleSdpObserver(), sdp);
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("type", sdp.type.canonicalForm());
                    payload.put("sdp", sdp.description);
                    sendSignal("offer", payload);
                    runOnUiThread(() -> status.setText("Calling…"));
                } catch (Exception e) { toast("Could not send call offer."); }
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String error) { toast("Offer failed: " + error); }
            @Override public void onSetFailure(String error) {}
        }, new MediaConstraints());
    }

    private void createAnswer() {
        peer.createAnswer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peer.setLocalDescription(new SimpleSdpObserver(), sdp);
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("type", sdp.type.canonicalForm());
                    payload.put("sdp", sdp.description);
                    sendSignal("answer", payload);
                    runOnUiThread(() -> status.setText("Connecting…"));
                } catch (Exception e) { toast("Could not send call answer."); }
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String error) { toast("Answer failed: " + error); }
            @Override public void onSetFailure(String error) {}
        }, new MediaConstraints());
    }

    private final class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) { toast(error); }
        @Override public void onSetFailure(String error) { toast(error); }
    }

    private void sendSignal(String type, JSONObject payload) {
        if (closed) return;
        io.execute(() -> {
            try {
                JSONObject row = new JSONObject();
                row.put("call_id", callId);
                row.put("sender_id", uid);
                row.put("recipient_id", ""); // Filled from the call participant row below.
                row.put("signal_type", type);
                row.put("payload", payload);
                JSONArray sessions = SupabaseClient.getArray(
                    "/rest/v1/call_sessions?select=caller_id,callee_id&id=eq." + SupabaseClient.enc(callId), token
                );
                if (sessions.length() == 0) return;
                JSONObject session = sessions.getJSONObject(0);
                String recipient = uid.equals(session.optString("caller_id"))
                    ? session.optString("callee_id") : session.optString("caller_id");
                row.put("recipient_id", recipient);
                SupabaseClient.post("/rest/v1/call_signals", token, row);
            } catch (Exception e) {
                if (!closed) toast("Signaling error: " + e.getMessage());
            }
        });
    }

    private final Runnable signalPoll = new Runnable() {
        @Override public void run() {
            if (closed || isFinishing()) return;
            if (!polling) {
                polling = true;
                io.execute(() -> {
                    try {
                        JSONArray rows = SupabaseClient.getArray(
                            "/rest/v1/call_signals?select=id,sender_id,signal_type,payload&call_id=eq."
                                + SupabaseClient.enc(callId) + "&recipient_id=eq." + SupabaseClient.enc(uid)
                                + "&processed_at=is.null&order=created_at.asc&limit=100", token
                        );
                        for (int i=0;i<rows.length()&&!closed;i++) {
                            JSONObject row = rows.optJSONObject(i);
                            if (row == null) continue;
                            String id = row.optString("id");
                            if (seenSignals.contains(id)) continue;
                            seenSignals.add(id);
                            handleSignal(row);
                            JSONObject patch = new JSONObject();
                            patch.put("processed_at", nowIso());
                            SupabaseClient.request("PATCH",
                                "/rest/v1/call_signals?id=eq." + SupabaseClient.enc(id), token, patch, null);
                        }
                        JSONArray sessions = SupabaseClient.getArray(
                            "/rest/v1/call_sessions?select=status&id=eq." + SupabaseClient.enc(callId), token
                        );
                        if (sessions.length()>0) {
                            String state=sessions.getJSONObject(0).optString("status");
                            if ("accepted".equals(state)) handler.removeCallbacks(callTimeout);
                            if ("rejected".equals(state)||"ended".equals(state)||"missed".equals(state))
                                runOnUiThread(()->endCall(false));
                        }
                    } catch (Exception e) {
                        if (!closed) runOnUiThread(() -> status.setText("Signaling reconnecting…"));
                    } finally { polling=false; }
                });
            }
            if (!closed) handler.postDelayed(this, 1100L);
        }
    };

    private void handleSignal(JSONObject row) {
        String type = row.optString("signal_type");
        JSONObject payload = row.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        try {
            if ("offer".equals(type)) {
                SessionDescription offer = new SessionDescription(
                    SessionDescription.Type.OFFER, payload.optString("sdp")
                );
                peer.setRemoteDescription(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sdp) {}
                    @Override public void onSetSuccess() { remoteDescriptionSet=true; applyPendingIce(); createAnswer(); }
                    @Override public void onCreateFailure(String error) {}
                    @Override public void onSetFailure(String error) { toast("Offer rejected: " + error); }
                }, offer);
            } else if ("answer".equals(type)) {
                SessionDescription answer = new SessionDescription(
                    SessionDescription.Type.ANSWER, payload.optString("sdp")
                );
                peer.setRemoteDescription(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sdp) {}
                    @Override public void onSetSuccess() { remoteDescriptionSet=true; applyPendingIce(); }
                    @Override public void onCreateFailure(String error) {}
                    @Override public void onSetFailure(String error) { toast("Answer rejected: " + error); }
                }, answer);
                runOnUiThread(() -> status.setText("Connecting…"));
            } else if ("ice".equals(type)) {
                IceCandidate candidate = new IceCandidate(
                    payload.optString("sdpMid"),
                    payload.optInt("sdpMLineIndex"),
                    payload.optString("sdp")
                );
                if (remoteDescriptionSet) peer.addIceCandidate(candidate);
                else pendingIce.add(candidate);
            } else if ("hangup".equals(type)) {
                runOnUiThread(() -> endCall(false));
            }
        } catch (Exception e) {
            toast("Could not process call signal: " + e.getMessage());
        }
    }

    private void applyPendingIce() {
        if (peer == null || !remoteDescriptionSet || pendingIce.isEmpty()) return;
        for (IceCandidate candidate : new ArrayList<>(pendingIce)) peer.addIceCandidate(candidate);
        pendingIce.clear();
    }

    private void toggleMute() {
        microphoneEnabled = !microphoneEnabled;
        if (audioTrack != null) audioTrack.setEnabled(microphoneEnabled);
        muteButton.setText(microphoneEnabled ? "Mute" : "Unmute");
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        if (videoTrack != null) videoTrack.setEnabled(cameraEnabled);
        cameraButton.setText(cameraEnabled ? "Camera" : "Camera off");
    }

    private void toggleSpeaker() {
        speakerEnabled = !speakerEnabled;
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        audio.setSpeakerphoneOn(speakerEnabled);
        speakerButton.setText(speakerEnabled ? "Speaker on" : "Speaker");
    }

    private void startScreenShare() {
        if (!videoCall || projectionManager == null || screenCapturer != null) return;
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SCREEN_CAPTURE_REQUEST) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            toast("Screen sharing was cancelled.");
            return;
        }
        try {
            if (cameraCapturer != null) {
                try { cameraCapturer.stopCapture(); } catch (InterruptedException ignored) {}
                cameraCapturer.dispose();
                cameraCapturer = null;
            }
            screenCapturer = new ScreenCapturerAndroid(data, new MediaProjection.Callback() {
                @Override public void onStop() { runOnUiThread(() -> shareButton.setText("Share screen")); }
            });
            screenCapturer.initialize(textureHelper, this, videoSource.getCapturerObserver());
            screenCapturer.startCapture(1280, 720, 15);
            activeCapturer = screenCapturer;
            localRenderer.setMirror(false);
            shareButton.setText("Sharing screen");
            status.setText("Screen sharing");
        } catch (Exception e) {
            toast("Screen sharing failed: " + e.getMessage());
        }
    }

    private void endCall(boolean sendHangup) {
        if (closed) return;
        if (sendHangup) sendSignal("hangup", new JSONObject());
        closed = true;
        handler.removeCallbacks(signalPoll);
        handler.removeCallbacks(callTimeout);
        io.execute(() -> {
            try {
                JSONObject patch = new JSONObject();
                patch.put("status", "ended");
                patch.put("ended_at", nowIso());
                SupabaseClient.request("PATCH",
                    "/rest/v1/call_sessions?id=eq." + SupabaseClient.enc(callId), token, patch, null);
            } catch (Exception ignored) {}
        });
        releaseMedia();
        finish();
    }

    private void releaseMedia() {
        try { if (activeCapturer != null) activeCapturer.stopCapture(); } catch (Exception ignored) {}
        try { if (activeCapturer != null) activeCapturer.dispose(); } catch (Exception ignored) {}
        activeCapturer = null;
        if (audioTrack != null) audioTrack.dispose();
        if (videoTrack != null) videoTrack.dispose();
        if (audioSource != null) audioSource.dispose();
        if (videoSource != null) videoSource.dispose();
        if (textureHelper != null) textureHelper.dispose();
        if (peer != null) { peer.close(); peer.dispose(); }
        if (factory != null) factory.dispose();
        if (localRenderer != null) localRenderer.release();
        if (remoteRenderer != null) remoteRenderer.release();
        if (egl != null) egl.release();
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        audio.setSpeakerphoneOn(false);
        audio.setMode(AudioManager.MODE_NORMAL);
    }

    @Override public void onBackPressed() { endCall(true); }

    @Override protected void onDestroy() {
        if (!closed) {
            closed = true;
            handler.removeCallbacks(signalPoll);
            handler.removeCallbacks(callTimeout);
            releaseMedia();
        }
        io.shutdown();
        super.onDestroy();
    }
}
