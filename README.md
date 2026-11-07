# CHAT-APP-

Native Android messenger source project built with Java, XML layouts, Android SDK, AndroidX, and Supabase.

## Source features currently implemented
- Android Gradle project and native Java/XML interface.
- Supabase email/password sign-up and sign-in using the public publishable key.
- Stores access/refresh tokens locally and refreshes expired access tokens when needed.
- Keeps profile presence and last-seen timestamps updated while the app is in the foreground.
- Conversation list backed by `conversation_members`.
- Search profiles by username or display name.
- Start a one-to-one conversation, reuse an existing direct chat, and block direct chat creation when either user has blocked the other.
- Create a group using a group name and comma-separated existing usernames.
- Send and retrieve text messages.
- Select and upload files to the private `chat-media` Storage bucket; attachment messages store the private object path.
- Open attachments through a time-limited signed URL.
- Refresh open conversations every five seconds using REST polling.
- Record delivery/read receipts and display sent/delivered/seen states.
- Edit or soft-delete your own messages.
- Edit profile display name, username, and bio.
- Block/unblock users from the people search.
- Manage groups: rename, add/remove members as owner, and leave a group.
- Periodic background message checks using Android WorkManager (minimum 15-minute interval); these are local polling notifications, not instant push.
- Sign out.
- GitHub Actions workflow intended to build a debug APK and upload it as an artifact.

## Not implemented or not verified yet
- True instant push notifications (the current free background alert mechanism checks periodically and can be delayed by Android battery/network restrictions).
- Native Supabase Realtime/WebSocket subscription; current refresh is periodic polling.
- Native Supabase Realtime/WebSocket subscription; foreground chat currently polls every five seconds.
- Typing indicators and real-time online presence across multiple devices.
- Arabic translation/RTL polish and a language selector.
- Device-level integration testing, attachment tests across file types, and production security review.

- Full Arabic translation and RTL polish, accessibility and device testing.
- APK build status must be checked against the latest GitHub Actions run; installation and end-to-end behavior have not been verified on a physical device.

## Backend and security
The Android client uses the Supabase project URL and public publishable key in `SupabaseConfig.java`. Never put a service-role or secret key in a mobile client.

The database has profiles, conversations, conversation members, messages, message receipts, and user blocks. Row Level Security is enabled. The conversation-member insert rule was tightened: only the conversation creator may add members. The private Storage bucket permits members of the relevant conversation to read/upload objects using the conversation UUID as the first folder. Review every policy and test with separate accounts before production use.

## Build
Open the repository root in Android Studio using JDK 17 and Android SDK 35. GitHub Actions is configured to run `gradle assembleDebug` on pushes to `main`; download the `chat-app-debug` artifact only if the workflow completes successfully.

This is a working development foundation, not yet a production-ready WhatsApp/Messenger replacement.