# CHAT-APP-

Native Android messaging app (Java + XML), connected to the Supabase project configured for this repository.

## Current implementation
This repository is being built as a native Android application, not a website or WebView. The first source drop establishes the Android project, Supabase Auth sign-in/sign-up, and the initial chat screen scaffold.

## Requirements
- Android Studio / Android Gradle Plugin
- JDK 17
- Android SDK 35

## Configure
The app uses the public Supabase project URL and publishable key in `app/src/main/java/com/chatapp/mobile/SupabaseConfig.java`. A publishable key is intended for client apps; never place a service-role or secret key in an Android app.

## Build
Open the repository root in Android Studio and allow Gradle sync. Build with the Android app bundle/APK task.

## Important status
Do not treat this source drop as a finished WhatsApp/Messenger replacement. Group management, media upload, realtime subscriptions, push notifications, delivery/read receipts, blocking, and production security review still need implementation and device testing.