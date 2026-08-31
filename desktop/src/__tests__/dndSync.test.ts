import { describe, it, expect } from "vitest";
import type {
  SyncMessage,
  DndStatusPayload,
  NotificationItem,
  PairingRequest,
} from "../../../protocol/types";

describe("DND & Notification Sync Protocol", () => {
  it("creates a valid pairing request message", () => {
    const payload: PairingRequest = {
      deviceInfo: {
        deviceId: "android_123",
        deviceName: "Pixel 8 Pro",
        deviceType: "android",
        appVersion: "1.0.0",
        protocolVersion: "1.0.0",
      },
      pin: "123456",
    };

    const msg: SyncMessage<PairingRequest> = {
      id: "test-uuid",
      type: "PAIR_REQUEST",
      senderId: "android_123",
      timestamp: 1725000000000,
      payload,
    };

    expect(msg.type).toBe("PAIR_REQUEST");
    expect(msg.payload.pin).toBe("123456");
    expect(msg.payload.deviceInfo.deviceType).toBe("android");
  });

  it("handles DND status update payload correctly", () => {
    const dndPayload: DndStatusPayload = {
      mode: "PRIORITY_ONLY",
      isEnabled: true,
      sourceDevice: "pixel_8",
      rawFilterCode: 2,
    };

    expect(dndPayload.isEnabled).toBe(true);
    expect(dndPayload.mode).toBe("PRIORITY_ONLY");
  });

  it("structures notification items with reply action metadata", () => {
    const notification: NotificationItem = {
      id: "sbn_987",
      packageName: "com.whatsapp",
      appName: "WhatsApp",
      title: "Alice",
      text: "Meeting at 3?",
      timestamp: Date.now(),
      isOngoing: false,
      isClearable: true,
      actions: [
        {
          id: "action_0",
          title: "Reply",
          isReply: true,
          replyPlaceholder: "Type a reply...",
        },
      ],
    };

    expect(notification.actions.length).toBe(1);
    expect(notification.actions[0].isReply).toBe(true);
    expect(notification.actions[0].replyPlaceholder).toBe("Type a reply...");
  });
});
