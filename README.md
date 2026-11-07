# CHAT-APP-

Native Android messenger source project built with Java, XML layouts, Android SDK, AndroidX, and Supabase.

## Source features currently implemented
- Android Gradle project and native Java/XML interface.
- Supabase email/password sign-up and sign-in using the public publishable key.
- Stores returned access/refresh tokens and the signed-in user ID locally.
- Conversation list backed by `conversation_members`.
- Search profiles by username or display name.
- Start a one-to-one conversation and add both members.
- Create a group using a group name and comma-separated existing usernames.
- Send and retrieve text messages.
- Select and upload files to the private `chat-media` Storage bucket; attachment messages store the private object path.
- Open attachments through a time-limited signed URL.
- Refresh open conversations every five seconds using REST polling.
- Sign out.
- GitHub Actions workflow intended to build a debug APK and upload it as an artifact.

## Not implemented or not verified yet
- Push notifications (requires Firebase project configuration and device tokens).
- Native Supabase Realtime/WebSocket subscription; current refresh is periodic polling.
- Delivery/read receipts, edit/delete message controls, block controls, profile editing, typing indicators, presence heartbeat, and full group administration.
- Automatic access-token refresh/session renewal.
- Full Arabic translation and RTL polish, accessibility and device testing.
- Successful APK build and installation have not yet been confirmed.

## Backend and security
The Android client uses the Supabase project URL and public publishable key in `SupabaseConfig.java`. Never put a service-role or secret key in a mobile client.

The database has profiles, conversations, conversation members, messages, message receipts, and user blocks. Row Level Security is enabled. The conversation-member insert rule was tightened: only the conversation creator may add members. The private Storage bucket permits members of the relevant conversation to read/upload objects using the conversation UUID as the first folder. Review every policy and test with separate accounts before production use.

## Build
Open the repository root in Android Studio using JDK 17 and Android SDK 35. GitHub Actions is configured to run `gradle assembleDebug` on pushes to `main`; download the `chat-app-debug` artifact only if the workflow completes successfully.

This is a working development foundation, not yet a production-ready WhatsApp/Messenger replacement.