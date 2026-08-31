# DND & Notification Syncer

A cross-platform system to synchronize **Do Not Disturb (DND) / Focus Mode** status and **Notifications** in real-time between **Android phones** and **Desktop computers (macOS, Windows, Linux)** over an encrypted local Wi-Fi connection.

<img width="1072" height="792" alt="Screenshot 2026-08-30 at 11 33 54 PM" src="https://github.com/user-attachments/assets/e72dda18-fff5-4ecd-a5dc-fd6aeb24b4d6" />


---

## 🌟 Features

- **Bidirectional Focus / DND Sync**:
  - Turning on DND on Android silences notifications and activates Focus mode on macOS / Windows Focus Assist / Linux notification inhibitor.
  - Toggling DND on Desktop instantly updates your Android phone's interruption filter.
- **Real-Time Notification Mirroring**:
  - Mirror Android notifications directly to your desktop tray & notification feed with app icons, titles, bodies, and categories.
- **Cross-Device Notification Actions & Inline Replies**:
  - Dismiss notifications from desktop (clears them on Android).
  - Quick-reply directly to messaging apps (SMS, WhatsApp, Signal, Slack, Telegram) from your desktop keyboard.
- **Zero-Cloud, Private LAN Discovery**:
  - Automatic device discovery via UDP broadcast (`port 47891`) on your local Wi-Fi network.
  - Fast, low-latency WebSocket communication (`ws://<ip>:47890`) with pairing PIN security.
- **Native OS Focus Mode Integrations**:
  - **macOS**: AppleScript / Shortcuts Focus integration.
  - **Windows**: WinRT Focus Assist & Quiet Hours.
  - **Linux**: D-Bus `org.freedesktop.Notifications` / Dunst / Mako / GNOME notification inhibit.

---

## 📁 Repository Structure

```
dnd-syncer/
├── desktop/                  # Tauri v2 Desktop App (macOS, Windows, Linux)
│   ├── src-tauri/            # Rust backend (WebSocket server, UDP beacon, OS focus adapters)
│   │   └── src/
│   │       ├── os/           # OS-specific Focus adapters (macOS, Windows, Linux)
│   │       ├── network/      # Discovery broadcaster & WebSocket server
│   │       ├── state.rs      # Reactive state & device store
│   │       └── commands.rs   # Tauri IPC commands
│   ├── src/                  # React + TypeScript UI (Tailwind CSS, Lucide icons)
│   └── package.json
├── mobile-android/           # Android Companion App (Kotlin, Jetpack Compose)
│   ├── app/src/main/
│   │   └── java/com/dndsync/
│   │       ├── service/      # NotificationListener & Foreground Sync Service
│   │       ├── network/      # UDP Discovery listener & OkHttp WebSocket Client
│   │       └── ui/           # Jetpack Compose UI with Permission Wizard
│   └── build.gradle.kts
└── protocol/                 # Shared communication protocol & TypeScript definitions
    └── types.ts
```

---

## 🚀 Getting Started

### 1. Running the Desktop Application

#### Prerequisites
- **Node.js**: v18+ (v24 recommended)
- **Rust**: 1.75+ (Cargo)
- **pnpm**: `npm install -g pnpm`

#### Development
```bash
# Navigate to desktop app directory
cd desktop

# Install dependencies
pnpm install

# Run in development mode (launches Tauri desktop window with hot reloading)
pnpm run tauri dev
```

To run just the frontend in browser preview mode:
```bash
pnpm run dev
```

#### Production Build
```bash
# Build desktop executable (creates .dmg on macOS, .msi/.exe on Windows, .AppImage/.deb on Linux)
pnpm run tauri build
```

---

### 2. Running the Android Companion App

#### Prerequisites
- **Android Studio** (Koala or newer)
- **JDK 17+**
- Android device running **Android 8.0 (API 26) or higher**

#### Building & Installing
1. Open the `mobile-android/` folder in Android Studio.
2. Connect your Android phone via USB debugging or Wi-Fi debugging.
3. Click **Run 'app'** or run via CLI:
   ```bash
   cd mobile-android
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

#### Setup on Phone:
1. Open the **DND Syncer** app.
2. Grant **Notification Access** (allows reading & dismissing alerts).
3. Grant **Do Not Disturb Access** (allows reading & setting DND filter).
4. Tap **Ignore Battery Optimizations** to ensure continuous background sync.
5. Scan the desktop QR code or enter the 6-digit PIN to pair!

---

## 🛡️ Protocol Overview

All events are formatted as JSON packets over WebSocket:

```typescript
export interface SyncMessage<T = unknown> {
  id: string;          // UUID v4
  type: MessageType;   // e.g. DND_STATUS_UPDATE, NOTIFICATION_POSTED
  senderId: string;    // Device ID
  targetId?: string;
  timestamp: number;
  payload: T;
}
```

Key message types:
- `PAIR_REQUEST` / `PAIR_RESPONSE`: 6-digit PIN handshake.
- `DND_STATUS_UPDATE` / `SET_DND_REQUEST`: Focus mode state synchronization.
- `NOTIFICATION_POSTED` / `NOTIFICATION_REMOVED`: Notification stream mirroring.
- `DISMISS_NOTIFICATION` / `SEND_NOTIFICATION_REPLY`: Remote interactive controls.

## More Screenshots

<img width="1072" height="792" alt="Screenshot 2026-08-30 at 11 33 18 PM" src="https://github.com/user-attachments/assets/f59891ba-8148-4773-8097-10b7d1018d66" />
<img width="1072" height="792" alt="Screenshot 2026-08-30 at 11 33 51 PM" src="https://github.com/user-attachments/assets/36b0eb47-ee25-4ba8-bd1e-e74c56d16e07" />
