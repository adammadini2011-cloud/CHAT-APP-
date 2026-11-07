# CHAT-APP-

Native Android messaging app project built with Java, XML layouts, Android SDK, AndroidX, and Supabase.

## Implemented in the source
- Email/password account creation and sign-in through Supabase Auth.
- Local access/refresh token storage and access-token renewal.
- Conversation list, user search, direct chats, and group creation.
- Reuse existing direct chats and prevent direct messages when either user has blocked the other.
- Text messages and file attachments, including private Storage uploads and signed download links.
- Message sent/delivered/seen receipts.
- Edit and soft-delete controls for your own messages.
- Profile editing (display name, username, and bio).
- Block/unblock controls.
- Group owner controls: rename groups, add/remove members, and leave groups.
- Typing indicators backed by an RLS-protected table.
- Foreground online/last-seen updates.
- Five-second foreground REST polling for chat updates.
- WorkManager background checks for local message notifications (minimum 15-minute interval; not instant push).
- English/Arabic language toggle, RTL layout direction, custom launcher icon, and rounded native controls.

## Not yet complete or verified
- Native Supabase Realtime/WebSocket updates; foreground chat currently uses REST polling.
- True instant push notifications; the background worker can be delayed by Android battery/network restrictions.
- Full Arabic translation coverage and full RTL/accessibility polish.
- Full device-level testing of account creation, multiple-user chats, groups, receipts, blocking, and all attachment types.
- Production security review and release signing.

## Backend and security
The Android client contains only the Supabase project URL and public publishable key in `SupabaseConfig.java`. Never put a service-role or secret key in an Android client.

The backend includes profiles, conversations, conversation members, messages, message receipts, user blocks, and typing status. RLS is enabled on these public tables. The `chat-media` Storage bucket is private, with member-based read/upload policies. Test with separate accounts before production use.

## Build
GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and Android SDK 35. Check the latest workflow run and download the `chat-app-debug` artifact only after it succeeds.

This is a substantial working development build, but it is not yet a fully tested production replacement for WhatsApp or Messenger.
