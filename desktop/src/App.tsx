import { useState } from "react";
import { Header } from "./components/Header";
import { DndControlCard } from "./components/DndControlCard";
import { NotificationFeed } from "./components/NotificationFeed";
import { PairingModal } from "./components/PairingModal";
import { SettingsModal } from "./components/SettingsModal";
import { useDndSync } from "./hooks/useDndSync";

export function App() {
  const {
    deviceId,
    deviceName,
    pairingPin,
    pairedDevices,
    activeDeviceIds,
    notifications,
    phoneDndStatus,
    desktopDndStatus,
    hasFullDiskAccess,
    settings,
    toggleDnd,
    dismissNotification,
    replyNotification,
    regeneratePin,
    unpairDevice,
    updateSettings,
    sendTestNotification,
    openFullDiskAccessSettings,
  } = useDndSync();

  const [isPairingOpen, setIsPairingOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  // Tauri window drag handler
  const handleStartDrag = (e: React.MouseEvent) => {
    if (e.button === 0) {
      if (typeof window !== "undefined" && "__TAURI_INTERNALS__" in window) {
        import("@tauri-apps/api/core").then(({ invoke }) => {
          invoke("start_drag").catch((err) => console.error("Drag error:", err));
        });
      }
    }
  };

  return (
    <div className="flex flex-col h-screen bg-[#090d16] text-slate-100 antialiased overflow-hidden select-none">
      {/* Top 2rem (32px) dedicated draggable titlebar area for macOS traffic lights & window grab */}
      <div
        data-tauri-drag-region
        onMouseDown={handleStartDrag}
        className="h-8 w-full bg-slate-950/80 shrink-0 select-none cursor-default border-b border-slate-800/40"
      />

      {/* Main Header */}
      <Header
        activeDeviceCount={activeDeviceIds.length}
        onOpenPairing={() => setIsPairingOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onSendTestNotification={sendTestNotification}
        onStartDrag={handleStartDrag}
      />

      {/* Main Content Area */}
      <main className="flex-1 p-6 flex flex-col gap-6 overflow-hidden max-w-6xl w-full mx-auto">
        {/* Top: DND & Focus Synchronization Card */}
        <DndControlCard
          phoneDnd={phoneDndStatus}
          desktopDnd={desktopDndStatus}
          hasActivePhone={activeDeviceIds.length > 0}
          hasFullDiskAccess={hasFullDiskAccess}
          onOpenFullDiskAccess={openFullDiskAccessSettings}
          onToggleDnd={toggleDnd}
          autoSyncEnabled={settings.autoSyncDndBidirectional}
        />

        {/* Bottom: Notification Mirroring & Actions Feed */}
        <NotificationFeed
          notifications={notifications}
          onDismiss={dismissNotification}
          onReply={replyNotification}
        />
      </main>

      {/* Modals */}
      <PairingModal
        isOpen={isPairingOpen}
        onClose={() => setIsPairingOpen(false)}
        deviceId={deviceId}
        deviceName={deviceName}
        pairingPin={pairingPin}
        pairedDevices={pairedDevices}
        activeDeviceIds={activeDeviceIds}
        onRegeneratePin={regeneratePin}
        onUnpairDevice={unpairDevice}
      />

      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        settings={settings}
        onSaveSettings={updateSettings}
      />
    </div>
  );
}

export default App;
