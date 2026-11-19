# NEXORA — Native Android Community & Chat

NEXORA is a native Android application built with Java, AndroidX, XML layouts, and Supabase. Development is isolated on the `nexora/android-foundation` branch.

## Existing direct-chat foundation
- Email/password registration and sign-in through Supabase Auth.
- Session refresh and local token storage.
- Direct and group conversations, text messages, attachments, delivery/read receipts.
- Profiles, blocking, typing indicators, online presence, and Supabase Realtime with REST polling fallback.
- Social channels, statuses, group management, invites, polls, and privacy settings.
- Arabic/English language selection and RTL support in the main app.

## NEXORA server features implemented
- Public server discovery and server creation.
- Server membership with automatic owner membership.
- Text channels with topics and ordering.
- Channel messages with Supabase Realtime subscriptions and a five-second REST fallback.
- Server invite codes and join-by-code flow.
- Server member list, role changes, mute and ban controls.
- RLS-backed owner/admin/moderator/member authorization.

## Direct WebRTC calls implemented (not yet device-tested)
- Voice and video call actions in direct conversations.
- Incoming call prompt while the app is open.
- SDP offer/answer and ICE signaling stored in Supabase.
- WebRTC peer connection with Google STUN servers, mute, speaker, camera toggle, and an initial screen-share flow.
- Early ICE candidate queue and unanswered-call timeout.
- No TURN server is configured. STUN-only calls can fail on restrictive NAT/firewall networks; do not promise universal connectivity.
- Screen sharing and camera/microphone behavior still require real Android device testing.

## Backend deployment and security
The following migrations are applied to the linked Supabase Free-tier project, which is currently ACTIVE_HEALTHY:
- `20261119202017_nexora_servers_channels`
- `20261119202326_nexora_profile_directory_security`
- `20261119202442_nexora_webrtc_signaling`
- `20261119202707_nexora_api_privilege_hardening`
- `20261119203709_nexora_server_realtime`

RLS is enabled on all new public server/call tables. Client table grants are least-privilege; call session updates are limited to status/timestamps and call signal updates to processed_at. Server message policies are scoped to the matching server and channel. The profile directory view uses `security_invoker=true` and a private helper for photo/presence visibility.

Security Advisor no longer reports the former SECURITY DEFINER view error. Remaining warnings include intentional authenticated SECURITY DEFINER RPCs with in-function authorization checks, anonymous-auth policy warnings in the existing project (the Auth setting cannot be changed through the connected controls), and leaked-password protection being disabled. These require owner-level Auth settings review.

## Not yet complete or verified
- Group voice/video calls and persistent server voice rooms.
- FCM push notifications for incoming calls and messages when the app is backgrounded or closed. Incoming call polling currently requires the app to be open.
- Production-grade screen sharing with Android foreground media-projection service.
- TURN relay and testing across restrictive networks. No paid TURN service is enabled.
- Full Arabic translation/accessibility polish across CommunityActivity and CallActivity.
- Multi-account server/call E2E tests and real-device testing.
- Production release signing, privacy policy, and store readiness.
- Google/Facebook OAuth credentials must be configured by the project owner before those providers can work.

## Security and cost
- Android contains only the Supabase publishable key; never add a service-role or secret key to the client.
- Supabase is on the Free plan. No paid plan or service has been enabled.
- Never deploy a paid TURN service, paid hosting, or paid provider without explicit owner approval.

## Build and artifacts
GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and Android SDK 35, verifies APK package metadata, packages source separately, and attempts an emulator install/launch. A successful compile is not the same as a completed multi-user call or server E2E test. See the latest workflow run on the NEXORA branch.

This is still an active development build, not yet a production-ready replacement for Discord or other messaging platforms.
