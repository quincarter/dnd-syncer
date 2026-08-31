import { useState, useEffect, useCallback } from "react";
import type {
  AppSettings,
  DndStatusPayload,
  NotificationItem,
  PairedDevice,
} from "../../../protocol/types";

// Check if running inside Tauri runtime
const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

export function useDndSync() {
  const [deviceId, setDeviceId] = useState<string>("desktop-client");
  const [deviceName, setDeviceName] = useState<string>("My Desktop");
  const [pairingPin, setPairingPin] = useState<string>("849201");
  const [localIp, setLocalIp] = useState<string | null>(null);
  const [pairedDevices, setPairedDevices] = useState<PairedDevice[]>([]);
  const [activeDeviceIds, setActiveDeviceIds] = useState<string[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [phoneDndStatus, setPhoneDndStatus] = useState<DndStatusPayload | null>(null);
  const [desktopDndStatus, setDesktopDndStatus] = useState<boolean>(false);
  const [hasFullDiskAccess, setHasFullDiskAccess] = useState<boolean>(true);
  const [settings, setSettings] = useState<AppSettings>({
    autoSyncDndBidirectional: true,
    muteDesktopWhenPhoneDnd: true,
    showNotificationToasts: true,
    launchAtStartup: false,
    ignoredPackages: ["com.android.systemui", "android"],
    priorityOnlyPackages: [],
  });

  const fetchState = useCallback(async () => {
    if (isTauri) {
      try {
        const { invoke } = await import("@tauri-apps/api/core");
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const state: any = await invoke("get_state");
        setDeviceId(state.deviceId);
        setDeviceName(state.deviceName);
        setPairingPin(state.pairingPin);
        setLocalIp(state.localIp || null);
        setPairedDevices(state.pairedDevices || []);
        setActiveDeviceIds(state.activeDeviceIds || []);
        setNotifications(state.notifications || []);
        setPhoneDndStatus(state.phoneDndStatus || null);
        setDesktopDndStatus(state.desktopDndStatus || false);
        if (typeof state.hasFullDiskAccess === "boolean") {
          setHasFullDiskAccess(state.hasFullDiskAccess);
        }
        if (state.settings) {
          setSettings(state.settings);
        }
      } catch (err) {
        console.error("Failed to fetch state from Tauri backend:", err);
      }
    }
  }, []);

  useEffect(() => {
    fetchState();

    if (isTauri) {
      let unlistenList: Array<() => void> = [];

      (async () => {
        const { listen } = await import("@tauri-apps/api/event");

        const unPhoneDnd = await listen<DndStatusPayload>("phone_dnd_changed", (event) => {
          setPhoneDndStatus(event.payload);
        });

        const unDesktopDnd = await listen<boolean>("desktop_dnd_changed", (event) => {
          setDesktopDndStatus(event.payload);
        });

        const unNotifPosted = await listen<NotificationItem>("notification_posted", (event) => {
          setNotifications((prev) => {
            const filtered = prev.filter((n) => n.id !== event.payload.id);
            return [event.payload, ...filtered].slice(0, 50);
          });
        });

        const unNotifRemoved = await listen<string>("notification_removed", (event) => {
          setNotifications((prev) => prev.filter((n) => n.id !== event.payload));
        });

        const unDevicePaired = await listen<PairedDevice>("device_paired", (event) => {
          setPairedDevices((prev) => [...prev.filter((d) => d.deviceInfo.deviceId !== event.payload.deviceInfo.deviceId), event.payload]);
          setActiveDeviceIds((prev) => [...new Set([...prev, event.payload.deviceInfo.deviceId])]);
        });

        const unDeviceConnected = await listen<PairedDevice>("device_connected", (event) => {
          setActiveDeviceIds((prev) => [...new Set([...prev, event.payload.deviceInfo.deviceId])]);
        });

        const unDeviceDisconnected = await listen<string>("device_disconnected", (event) => {
          setActiveDeviceIds((prev) => prev.filter((id) => id !== event.payload));
        });

        const unPinChanged = await listen<string>("pin_changed", (event) => {
          setPairingPin(event.payload);
        });

        unlistenList = [
          unPhoneDnd,
          unDesktopDnd,
          unNotifPosted,
          unNotifRemoved,
          unDevicePaired,
          unDeviceConnected,
          unDeviceDisconnected,
          unPinChanged,
        ];
      })();

      // 500ms sync loop to guarantee UI reflects host OS status
      const timer = setInterval(() => {
        fetchState();
      }, 500);

      return () => {
        clearInterval(timer);
        unlistenList.forEach((fn) => fn());
      };
    }
  }, [fetchState]);

  const toggleDnd = async (enable: boolean) => {
    setDesktopDndStatus(enable);
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("toggle_dnd", { enabled: enable });
    } else {
      // Mock toggle in browser mode
      setPhoneDndStatus((prev) =>
        prev
          ? { ...prev, isEnabled: enable, mode: enable ? "PRIORITY_ONLY" : "OFF" }
          : { mode: enable ? "PRIORITY_ONLY" : "OFF", isEnabled: enable, sourceDevice: "mock-phone" }
      );
    }
  };

  const dismissNotification = async (notificationId: string, packageName: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== notificationId));
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("dismiss_notification", { notificationId, packageName });
    }
  };

  const replyNotification = async (
    notificationId: string,
    actionId: string,
    packageName: string,
    replyText: string
  ) => {
    setNotifications((prev) => prev.filter((n) => n.id !== notificationId));
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("reply_notification", {
        notificationId,
        actionId,
        packageName,
        replyText,
      });
    }
  };

  const regeneratePin = async () => {
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      const pin: string = await invoke("regenerate_pin");
      setPairingPin(pin);
    } else {
      const newPin = Math.floor(100000 + Math.random() * 900000).toString();
      setPairingPin(newPin);
    }
  };

  const unpairDevice = async (id: string) => {
    setPairedDevices((prev) => prev.filter((d) => d.deviceInfo.deviceId !== id));
    setActiveDeviceIds((prev) => prev.filter((devId) => devId !== id));
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("unpair_device", { deviceId: id });
    }
  };

  const updateSettings = async (newSettings: AppSettings) => {
    setSettings(newSettings);
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("update_settings", { settings: newSettings });
    }
  };

  const sendTestNotification = async () => {
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("send_test_notification");
    } else {
      const newMockNotif: NotificationItem = {
        id: `mock_${Date.now()}`,
        packageName: "com.whatsapp",
        appName: "WhatsApp",
        title: "Sarah Connor",
        text: "Are we still meeting at 3 PM for the project review?",
        subText: "2 messages",
        timestamp: Date.now(),
        isOngoing: false,
        isClearable: true,
        category: "msg",
        actions: [
          { id: "reply_0", title: "Reply", isReply: true, replyPlaceholder: "Type a reply..." },
          { id: "read_1", title: "Mark as read", isReply: false },
        ],
      };
      setNotifications((prev) => [newMockNotif, ...prev]);
    }
  };

  const openFullDiskAccessSettings = async () => {
    if (isTauri) {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("open_macos_full_disk_access");
    }
  };

  return {
    deviceId,
    deviceName,
    pairingPin,
    localIp,
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
    refresh: fetchState,
  };
}
