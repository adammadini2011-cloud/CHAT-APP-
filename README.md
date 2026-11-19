# NEXORA — Native Android Community & Chat

NEXORA is a native Android application built with Java, AndroidX, XML layouts, and Supabase. It is developed on the `nexora/android-foundation` branch.

## Existing direct-chat foundation
- Email/password registration and sign-in through Supabase Auth.
- Session refresh and local token storage.
- Direct and group conversations, text messages, attachments, delivery/read receipts.
- Profiles, blocking, typing indicators, online presence, and conversation Realtime with REST polling fallback.
- Community area with statuses, channels, group management, invites, polls, and privacy settings.
- Arabic/English language selection and RTL layout support.

## NEXORA server features in this branch
- Public server discovery and server creation.
- Server membership and owner membership bootstrap.
- Server text channels with ordering and topics.
- Channel messages, author deletion, and server/channel manager authorization.
- Dedicated SQL migration with RLS policies and explicit Data API grants.

## Important deployment status
The migration `supabase/migrations/20261119000100_nexora_servers_channels.sql` is committed to this repository but has **not** been applied to the hosted Supabase project. The linked project is currently INACTIVE. Do not assume server APIs are live until the project is restored, the migration is applied, and security advisors and multi-user tests pass.

## Not yet complete or verified
- WebRTC voice/video calls and screen sharing.
- Discord-style voice rooms, richer server roles/permission matrix, server invites and moderation UI.
- Instant push notifications (the existing WorkManager worker is periodic and not guaranteed instant).
- Device-level and multi-account end-to-end verification of all flows.
- Full Arabic translation/accessibility polish and production release signing.
- OAuth provider credentials (Google/Facebook) must be configured by the project owner before those providers can work.

## Security and cost
- Android contains only the Supabase publishable key; never add a service-role or secret key to the client.
- Every new public table in the server migration has RLS enabled and explicit grants for authenticated users.
- Do not deploy a TURN service, paid hosting, or paid provider without explicit owner approval. STUN-only WebRTC may fail on restrictive networks.
- The current project must be active before any backend runtime tests can be performed.

## Build and artifacts
GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and Android SDK 35, then installs and launches it in an Android emulator. Use the latest successful run on the NEXORA branch. Source ZIP and APK are separate artifacts; an APK is only available when the build step succeeds.

This is an actively developed project, not yet a production-ready replacement for Discord or other messaging platforms.
