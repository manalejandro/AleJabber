# AleJabber — XMPP/Jabber Client for Android

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](.)
[![API](https://img.shields.io/badge/API-24%2B-blue)](.)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09-blue)](https://developer.android.com/jetpack/compose)

A modern, feature-rich XMPP/Jabber messaging client for Android built with **Jetpack Compose** and **Material Design 3**. AleJabber supports multiple accounts, end-to-end encryption (OTR, OMEMO, OpenPGP), multimedia file transfers via `http_upload`, in-app audio recording, group chat rooms, and full accessibility support.

---

## Table of Contents

1. [Features](#features)
2. [Screenshots](#screenshots)
3. [Architecture](#architecture)
4. [Project Structure](#project-structure)
5. [Getting Started](#getting-started)
6. [Configuration](#configuration)
7. [Encryption](#encryption)
8. [Multimedia & Audio](#multimedia--audio)
9. [Internationalization](#internationalization)
10. [Accessibility](#accessibility)
11. [Dependencies](#dependencies)
12. [Contributing](#contributing)
13. [License](#license)

---

## Features

| Feature | Description |
|---------|-------------|
| 🔐 **OTR Encryption** | Off-the-Record messaging with perfect forward secrecy |
| 🔒 **OMEMO Encryption** | XEP-0384 multi-device end-to-end encryption (recommended) |
| 🗝️ **OpenPGP** | Asymmetric PGP encryption via XEP-0373/0374 |
| 👥 **Multi-Account** | Manage multiple XMPP accounts from different servers |
| 💬 **Group Rooms (MUC)** | Join and manage Multi-User Chat rooms (XEP-0045) |
| 📎 **File Transfer** | Upload images, audio, and files via XEP-0363 `http_upload` |
| 🎙️ **Audio Messages** | Record and send voice messages directly from the app |
| 🔔 **Smart Notifications** | Per-account notification channels with vibration/sound control |
| 🌐 **Multilingual** | English 🇬🇧, Spanish 🇪🇸, Chinese 🇨🇳 |
| ♿ **Accessible** | Full TalkBack support with content descriptions and semantic roles |
| 🎨 **Material You** | Dynamic theming with Light/Dark/System modes |
| 🔄 **Auto-Reconnect** | Automatic reconnection with random increasing delay policy |
| 💾 **Offline Storage** | Room database caches messages for offline reading |

---

## Screenshots

> _Screenshots to be added after first device deployment._

---

## Architecture

AleJabber follows **Clean Architecture** with an **MVVM** presentation layer:

```
┌─────────────────────────────────────────────────────┐
│                   Presentation Layer                  │
│   Compose Screens  ←→  ViewModels  ←→  UI State     │
├─────────────────────────────────────────────────────┤
│                    Domain Layer                       │
│      Models · Use Cases · Repository Interfaces      │
├─────────────────────────────────────────────────────┤
│                     Data Layer                        │
│   Room DB · Smack XMPP · DataStore · OkHttp          │
└─────────────────────────────────────────────────────┘
```

### Key Patterns

- **Dependency Injection**: Hilt (Dagger-based)
- **Reactive Streams**: Kotlin `Flow` + `StateFlow` + `SharedFlow`
- **Navigation**: Jetpack Navigation Compose with type-safe routes
- **Background Work**: `XmppForegroundService` keeps connections alive
- **Persistence**: Room with KSP-generated DAOs

---

## Project Structure

```
app/src/main/java/com/manalejandro/alejabber/
├── AleJabberApp.kt              # Application class with Hilt initialization
├── MainActivity.kt              # Single-Activity entry point
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room database definition
│   │   ├── dao/                 # Data Access Objects (AccountDao, MessageDao, ContactDao, RoomDao)
│   │   └── entity/              # Room entities (AccountEntity, MessageEntity, etc.)
│   ├── remote/
│   │   └── XmppConnectionManager.kt  # Smack connection lifecycle manager
│   └── repository/
│       ├── AccountRepository.kt
│       ├── ContactRepository.kt
│       ├── MessageRepository.kt
│       └── RoomRepository.kt
│
├── domain/
│   └── model/                   # Pure Kotlin domain models
│       ├── Account.kt           # XMPP account model
│       ├── Contact.kt           # Roster contact
│       ├── Message.kt           # Chat message with encryption + media metadata
│       ├── Room.kt              # MUC room
│       └── Enums.kt             # EncryptionType, MessageStatus, PresenceStatus, etc.
│
├── di/
│   └── AppModule.kt             # Hilt module: DB, OkHttp, DataStore, XmppManager
│
├── media/
│   ├── AudioRecorder.kt         # MediaRecorder wrapper with StateFlow
│   └── HttpUploadManager.kt     # XEP-0363 file upload via OkHttp
│
├── service/
│   ├── XmppForegroundService.kt # Foreground service keeping XMPP alive
│   └── BootReceiver.kt          # BroadcastReceiver to restart on boot
│
└── ui/
    ├── theme/
    │   ├── Color.kt             # Brand colors + bubble colors
    │   ├── Theme.kt             # Material3 dynamic theme with AppTheme enum
    │   └── Type.kt              # Typography scale
    ├── navigation/
    │   ├── Screen.kt            # Sealed class route definitions
    │   └── AleJabberNavGraph.kt # NavHost with all destinations
    ├── components/
    │   ├── AvatarWithStatus.kt  # Avatar + presence dot component
    │   └── EncryptionBadge.kt   # Encryption type indicator badge
    ├── accounts/
    │   ├── AccountsScreen.kt    # Account list with connect/disconnect
    │   ├── AccountsViewModel.kt
    │   └── AddEditAccountScreen.kt  # Add/edit XMPP account form
    ├── contacts/
    │   ├── ContactsScreen.kt    # Roster list with search + presence
    │   └── ContactsViewModel.kt
    ├── rooms/
    │   ├── RoomsScreen.kt       # MUC rooms list
    │   └── RoomsViewModel.kt
    ├── chat/
    │   ├── ChatScreen.kt        # Full chat UI with bubbles, media, recording
    │   └── ChatViewModel.kt
    └── settings/
        ├── SettingsScreen.kt    # App preferences
        └── SettingsViewModel.kt
```

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11+
- Android SDK 36
- A running XMPP server (e.g., [ejabberd](https://www.ejabberd.im/), [Prosody](https://prosody.im/), [Openfire](https://www.igniterealtime.org/projects/openfire/))

### Build

```bash
# Clone the repository
git clone https://github.com/manalejandro/AleJabber.git
cd AleJabber

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

The debug APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuration

### Adding an XMPP Account

1. Open the app → tap **Accounts** tab
2. Press the **+** FAB
3. Fill in:
   - **JID** — your full Jabber ID, e.g. `user@jabber.org`
   - **Password** — your account password
   - **Server** _(optional)_ — override DNS-resolved hostname
   - **Port** _(default: 5222)_ — custom port if needed
   - **TLS** — toggle to require TLS (recommended)
   - **Resource** _(default: AleJabber)_ — client resource identifier

### Gradle Properties

`gradle.properties` contains build-time flags:

| Property | Default | Description |
|----------|---------|-------------|
| `android.disallowKotlinSourceSets` | `false` | Required for KSP + AGP 9.x compatibility |
| `org.gradle.jvmargs` | `-Xmx2048m` | Gradle daemon heap size |
| `android.useAndroidX` | `true` | AndroidX migration flag |

---

## Encryption

AleJabber supports three levels of end-to-end encryption, selectable per conversation:

### OMEMO (Recommended — XEP-0384)
- Multi-device, forward-secrecy encryption based on the Signal Protocol
- Works even when the recipient is offline
- Select **OMEMO** in the encryption picker (🔒 icon in chat toolbar)

### OTR (Off-the-Record — XEP-0364)
- Classic two-party encryption with perfect forward secrecy
- Requires both parties to be online simultaneously
- Best for high-privacy one-on-one conversations

### OpenPGP (XEP-0373/0374)
- Asymmetric RSA/ECC encryption using PGP key pairs
- Works offline; keys must be exchanged in advance
- Uses Bouncy Castle (`bcpg-jdk18on`, `bcprov-jdk18on`)

### None (Plain Text)
- Messages are sent unencrypted over the TLS-secured XMPP stream
- Only use on trusted, private servers

---

## Multimedia & Audio

### File Upload (XEP-0363 `http_upload`)
1. Tap the 📎 attach button in the chat input
2. Select any file from the device storage
3. AleJabber requests an upload slot from the XMPP server
4. The file is PUT to the provided URL via OkHttp
5. The download URL is sent as a message body
6. Images are auto-rendered inline in the chat bubble

### Audio Messages
1. In the chat input, press and hold the 🎙️ microphone button
2. Speak your message — a recording timer appears
3. Release to **send**, or tap **✕** to cancel
4. Audio is recorded with `MediaRecorder` (AAC/MP4 format)
5. The recording is uploaded via `http_upload` automatically

> **Note:** Microphone permission (`RECORD_AUDIO`) is requested on first use.

---

## Internationalization

AleJabber ships with three locale bundles:

| Locale | File |
|--------|------|
| English (default) | `app/src/main/res/values/strings.xml` |
| Spanish | `app/src/main/res/values-es/strings.xml` |
| Chinese (Simplified) | `app/src/main/res/values-zh/strings.xml` |

To add a new language:
1. Create `app/src/main/res/values-<locale>/strings.xml`
2. Copy all keys from the default `strings.xml`
3. Translate each string value

---

## Accessibility

AleJabber is designed to be fully usable with Android's TalkBack screen reader:

- All interactive elements have `contentDescription` labels
- Message status icons (sent, delivered, read) announce their state
- Recording timer is announced to screen readers
- Encryption badge announces the current encryption type
- Avatar components announce contact name and presence status
- The app passes the [Accessibility Scanner](https://support.google.com/accessibility/android/answer/6376559) basic checks

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.09.00 | UI framework |
| Material3 | (via BOM) | Design system |
| Hilt | 2.59.2 | Dependency injection |
| Room | 2.7.0 | Local database |
| Navigation Compose | 2.9.0 | In-app navigation |
| Smack (XMPP) | 4.4.8 | XMPP protocol implementation |
| OkHttp | 4.12.0 | HTTP file uploads |
| Coil | 2.7.0 | Image loading |
| DataStore | 1.1.1 | Settings persistence |
| Accompanist Permissions | 0.36.0 | Runtime permission handling |
| Bouncy Castle | 1.78.1 | OpenPGP crypto |
| Coroutines | 1.9.0 | Async/reactive programming |

---

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "feat: add my feature"`
4. Push to the branch: `git push origin feature/my-feature`
5. Open a Pull Request

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for formatting: `./gradlew ktlintCheck`
- Write KDoc comments for all public functions and classes
- Add unit tests for ViewModels and Repository classes

---

## License

```
MIT License

Copyright (c) 2026 Manuel Alejandro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

