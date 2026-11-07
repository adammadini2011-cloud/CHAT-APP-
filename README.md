# Infinity Chat

**Infinity Chat** is a native Android messaging app built with Java, XML layouts, Android SDK, AndroidX, and Supabase.

Native Android messaging app project built with Java, XML layouts, Android SDK, AndroidX, and Supabase.

## Implemented in the source
- Email/password account creation and sign-in through Supabase Auth.
- Local access/refresh token storage and access-token renewal.
- Conversation list, user search, direct chats, and group creation.
- Reuse existing direct chats and prevent direct messages when either user has blocked the other.
- Text messages and supported file attachments (JPEG, PNG, WEBP, GIF, MP4, PDF, TXT), including private Storage uploads and signed download links. Unsupported MIME types are rejected before upload.
- Message sent/delivered/seen receipts.
- Edit and soft-delete controls for your own messages.
- Profile editing (display name, username, and bio).
- Block/unblock controls.
- Group owner controls: rename groups, add/remove members, and leave groups; owners must remove other members before leaving to avoid orphaned groups.
- Typing indicators backed by an RLS-protected table.
- Foreground online/last-seen updates with a 60-second heartbeat while the app remains active.
- Native Supabase Realtime WebSocket subscription for the open conversation, with five-second REST polling as a fallback.
- WorkManager background checks for local message notifications (minimum 15-minute interval; not instant push).
- English/Arabic language toggle, RTL layout direction, Infinity Chat launcher icon, and rounded native controls.

## Not yet complete or verified
- Live WebSocket behavior and reconnection still need verification with two real accounts on an Android device; REST polling remains the fallback.
- True instant push notifications; the background worker can be delayed by Android battery/network restrictions.
- Full Arabic translation coverage and full RTL/accessibility polish.
- Full device-level testing of account creation, multiple-user chats, groups, receipts, blocking, and all attachment types.
- Release signing and production deployment checks.

## Backend and security
The Android client contains only the Supabase project URL and public publishable key in `SupabaseConfig.java`. Never put a service-role or secret key in an Android client.

The backend includes profiles, conversations, conversation members, messages, message receipts, user blocks, and typing status. RLS is enabled on all seven public tables. Membership checks and the new-user profile trigger were moved to a non-exposed `private` schema; execute permissions are restricted. Security Advisor currently reports no security findings. The `chat-media` Storage bucket is private, with member-based read/upload policies. Performance indexes were added for foreign keys. These checks do not replace real multi-account and device testing.

## Build
GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and Android SDK 35. Check the latest workflow run and download the `chat-app-debug` artifact only after it succeeds.

This is a substantial working development build, but it is not yet a fully tested production replacement for WhatsApp or Messenger.
