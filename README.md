# CHAT-APP-

A native Android messaging project using Java, XML layouts, Android SDK, and Supabase.

## Implemented in source
- Native Android Gradle project (Java, XML, AndroidX).
- Supabase email/password sign-up and sign-in using the public publishable key.
- Local persistence of the returned access/refresh tokens and signed-in user ID.
- Conversation list from `conversation_members`.
- Search for profiles by username or display name.
- Create a one-to-one conversation and add both members.
- Create a group by group name and comma-separated existing usernames.
- Send and retrieve text messages from Supabase.
- Five-second message refresh while a conversation is open.
- Sign out.

## Not implemented / not yet verified
- Media picker, uploads, and in-app media preview.
- Push notifications (requires Firebase project configuration and device tokens).
- Native Supabase Realtime/WebSocket subscriptions; current chat refresh uses periodic REST polling.
- Message delivery/read receipts, edit/delete UI, block UI, profile editing, typing indicators, presence heartbeat, and group administration.
- Token refresh/automatic session renewal.
- Accessibility, localization, full RTL polish, and broad device testing.
- A successful Android APK build has not yet been confirmed.

## Backend
This app is configured for the Supabase project URL and its public publishable key in `SupabaseConfig.java`. Never put a service-role or secret key in this Android project.

The database contains profiles, conversations, conversation members, messages, message receipts, and user blocks. Row Level Security is enabled. The conversation-member insert policy was tightened so only the conversation creator can add members. Review all policies and storage rules before production use.

## Build
Open the repository root in Android Studio with JDK 17 and Android SDK 35. GitHub Actions is configured to run `gradle assembleDebug` on pushes to `main` and upload the debug APK as an artifact if the build succeeds.

This is an actively developed source project, not yet a production-ready messenger.