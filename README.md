# NEXORA — Native Android Community & Chat

NEXORA is a native Android application built with Java, AndroidX, XML layouts, and Supabase. Development is isolated on the `nexora/android-foundation` branch.

## Existing direct-chat foundation
- Email/password registration and sign-in through Supabase Auth.
- Session refresh and local token storage.
- Direct and group conversations, text messages, attachments, delivery/read receipts.
- Profiles, blocking, typing indicators, online presence, and conversation Realtime with REST polling fallback.
- Community area with statuses, social channels, group management, invites, polls, and privacy settings.
- Arabic/English language selection and RTL layout support.

## NEXORA server features implemented
- Public server discovery and server creation.
- Server membership with automatic owner membership.
- Text channels with topics and ordering.
- Channel messages and author deletion.
- Server invite codes and join-by-code flow.
- Server member list, role changes, mute and ban controls.
- Database authorization for owner/admin/moderator/member roles.
- RLS enabled on all five new public tables, explicit grants, and server-scoped message policies.

## Backend deployment
The following migrations are applied to the linked Supabase Free-tier project, which is currently ACTIVE_HEALTHY:
- `20261119202017_nexora_servers_channels`
- `20261119202326_nexora_profile_directory_security`

The profile directory view now uses `security_invoker=true`; a private, narrowly-scoped helper enforces profile photo and presence visibility. Security Advisor no longer reports the former SECURITY DEFINER view error. Remaining warnings include intentional authenticated SECURITY DEFINER RPCs (invite acceptance and manager-only role/moderation actions), anonymous-auth policy warnings in the existing schema, and leaked-password protection being disabled. These still need a final configuration review.

## Not yet complete or verified
- WebRTC voice/video calls and screen sharing.
- Discord-style server voice rooms and group calls.
- FCM instant push notifications (the existing WorkManager worker is periodic and not guaranteed instant).
- Full Arabic translation/accessibility polish and production release signing.
- Multi-account server flows and real-device network testing.
- OAuth provider credentials (Google/Facebook) must be configured by the project owner before those providers can work.

## Security and cost
- Android contains only the Supabase publishable key; never add a service-role or secret key to the client.
- Never deploy a paid TURN service, paid hosting, or paid provider without explicit owner approval. STUN-only WebRTC may fail on restrictive networks.
- Supabase is on the Free plan. No paid plan or service has been enabled.

## Build and artifacts
GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and Android SDK 35. The workflow also packages the Android source and attempts an emulator install/launch. A successful compile is not the same as a full end-to-end feature test. See the latest workflow run on the NEXORA branch.

This is an actively developed project, not yet a production-ready replacement for Discord or other messaging platforms.
