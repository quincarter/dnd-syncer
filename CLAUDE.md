# DND & Notification Syncer

Cross-platform (Android + macOS/Windows/Linux desktop) real-time sync of Do Not
Disturb / Focus state and notifications over a local encrypted-LAN WebSocket.
Zero cloud — pairing and sync happen entirely over the local network.

## Repo layout (polyglot monorepo, no root package.json)

```
desktop/                        Tauri v2 app (macOS/Windows/Linux)
  src/                           React 18 + TypeScript UI (Tailwind, Vite, Vitest)
    hooks/useDndSync.ts           Single hook wrapping all Tauri invoke/listen calls
    components/                   DndControlCard, NotificationFeed, PairingModal, SettingsModal, Header
  src-tauri/src/                 Rust backend
    state.rs                      AppState — shared app state + sync/broadcast logic
    commands.rs                   #[tauri::command] handlers called from the frontend
    network/server.rs             WebSocket server (port 47890), message routing
    network/discovery.rs          UDP broadcast beacon (port 47891) for LAN discovery
    os/{macos,windows,linux}.rs   Per-OS Focus/DND adapters behind OsFocusAdapter trait
    storage.rs                    Persisted config (pairing, paired devices, settings)
mobile-android/                 Android companion app (Kotlin, Jetpack Compose)
  app/src/main/java/com/dndsync/
    service/DndNotificationListenerService.kt   Reads/sets DND, mirrors notifications
    service/DndSyncForegroundService.kt         Owns the WebSocket client, keeps app alive
    network/DndWebSocketClient.kt               WS client, mirrors network/server.rs message handling
    network/DiscoveryClient.kt                  UDP discovery client
    model/Models.kt                             Kotlin mirror of protocol/types.ts (hand-written, NOT generated)
protocol/types.ts               Canonical protocol source of truth (TS) — see below
```

## The protocol is manually mirrored in three places — keep them in sync

`protocol/types.ts` is the documented source of truth for `SyncMessage`,
`MessageType`, and all payload shapes. It is **not code-generated** into the
other two languages:

- Rust: `desktop/src-tauri/src/types.rs` (serde, `camelCase` on the wire)
- Kotlin: `mobile-android/app/src/main/java/com/dndsync/model/Models.kt` (Gson)

Any change to a message type or payload shape must be applied to **all
three** files by hand, plus the message-routing `match`/`when` in
`desktop/src-tauri/src/network/server.rs` and
`mobile-android/.../network/DndWebSocketClient.kt::handleIncomingMessage`.
Field names must match exactly across TS/Rust/Kotlin (camelCase on the wire).

## Core architectural patterns

- **Echo avoidance**: state changes that originated from a device must not be
  relayed back to that same device. See `AppState::broadcast_message_except`
  and the `exclude_device_id` parameter threaded through
  `handle_phone_dnd_update` in `state.rs`.
- **Convergence on connect**: whenever a device pairs or re-authenticates, the
  desktop pushes its *live* OS focus state (queried fresh via
  `get_os_adapter().get_focus_mode()`, not the last-cached value) — see
  `AppState::current_dnd_sync_message`. Don't rely on cached state alone when
  wiring up new connection paths.
- **Transition guard**: `AppState.last_toggled_at` suppresses re-reading the
  OS focus state for 2000ms after a local toggle, so a user's own click isn't
  immediately overwritten by a slower OS-level read. See `get_state` in
  `commands.rs`.
- **Shared state**: all `AppState` fields are `Arc<RwLock<T>>`, cloned per
  connection task. Hold read locks only as long as needed; don't hold a lock
  across an `.await` that doesn't need it.
- **Browser-mock mode**: `desktop/src/hooks/useDndSync.ts` checks
  `"__TAURI_INTERNALS__" in window` and falls back to mocked local state when
  running outside Tauri (`pnpm run dev`, i.e. `mise run dev:web`). Frontend
  changes should keep both code paths working.
- **OS adapters**: platform-specific Focus/DND logic lives behind the
  `OsFocusAdapter` trait (`os/mod.rs`) and is selected at compile time via
  `#[cfg(target_os = ...)]` in `get_os_adapter()`. New platform behavior goes
  in the matching `os/<platform>.rs`, not in `state.rs` or `commands.rs`.

## Build, dev, test (via mise — preferred)

```bash
mise install               # toolchains: node 24, pnpm 11, rust stable, java 17, gradle 8.13
mise run install            # pnpm install (desktop)
mise run dev                # Tauri dev (desktop app window)
mise run dev:web            # frontend only, in browser, mocked backend
mise run build               # desktop + android
mise run test                 # desktop (vitest) + android (gradle test)
mise run lint                  # tsc --noEmit + android lint
mise run android:install        # build debug APK and adb install
```

Manual equivalents live under `desktop/` (`pnpm run tauri dev`, `pnpm test`,
`pnpm run lint`) and `mobile-android/` (`./gradlew assembleDebug`, `gradle test`).

CI (`.github/workflows/build-all.yml`) builds desktop on macOS/Windows/Linux
via `tauri-apps/tauri-action` and Android via Gradle, on every push/PR to
`main`. Releases are managed by `release-please`
(`release-please-config.json`, `.release-please-manifest.json`).

## Conventions

- Rust: `snake_case` fields with `#[serde(rename_all = "camelCase")]` to match
  the wire protocol; message handling is a big `match` on `MessageType` in
  `network/server.rs`.
- Kotlin: message dispatch is a `when` on the raw `"type"` string in
  `DndWebSocketClient.handleIncomingMessage` — add new cases there for new
  `MessageType` values.
- TS: `protocol/types.ts` types are imported directly by
  `desktop/src/__tests__/*.test.ts` — treat them as the spec when writing
  tests, not `types.rs`/`Models.kt`.
- Ignore `.history/` (local VS Code history snapshots) and `mobile-android/.idea`,
  `.gradle`, `.kotlin` build/IDE artifacts — never edit or read them for context.
- No root-level lint/build/test command exists; always scope work (and any
  commands you run) to `desktop/` or `mobile-android/` explicitly, or use the
  `mise run` tasks which do this for you.
