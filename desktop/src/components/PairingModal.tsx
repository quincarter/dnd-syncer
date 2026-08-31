import React from "react";
import { QRCodeSVG } from "qrcode.react";
import { X, RefreshCw, Smartphone, Trash2, Wifi } from "lucide-react";
import type { PairedDevice } from "../../../protocol/types";

interface PairingModalProps {
  isOpen: boolean;
  onClose: () => void;
  deviceId: string;
  deviceName: string;
  pairingPin: string;
  pairedDevices: PairedDevice[];
  activeDeviceIds: string[];
  onRegeneratePin: () => void;
  onUnpairDevice: (id: string) => void;
}

export const PairingModal: React.FC<PairingModalProps> = ({
  isOpen,
  onClose,
  deviceId,
  deviceName,
  pairingPin,
  pairedDevices,
  activeDeviceIds,
  onRegeneratePin,
  onUnpairDevice,
}) => {
  if (!isOpen) return null;

  const qrValue = JSON.stringify({
    magic: "DND_SYNC_PAIR",
    deviceId,
    deviceName,
    pin: pairingPin,
    port: 47890,
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-indigo-600/20 text-indigo-400">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-white">Pair Android Phone</h3>
              <p className="text-xs text-slate-400">Connect via QR code or 6-digit PIN</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-6 overflow-y-auto">
          {/* QR Code & PIN Container */}
          <div className="flex flex-col sm:flex-row items-center gap-6 p-4 rounded-xl bg-slate-950/60 border border-slate-800/80">
            <div className="p-3 bg-white rounded-xl shadow-md shrink-0">
              <QRCodeSVG value={qrValue} size={130} />
            </div>

            <div className="flex-1 text-center sm:text-left">
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Pairing PIN Code
              </div>
              <div className="text-3xl font-mono font-bold tracking-widest text-indigo-400 my-1">
                {pairingPin}
              </div>
              <p className="text-xs text-slate-400 mb-3">
                Scan this QR code in the DND Syncer Android app or enter the 6-digit PIN.
              </p>
              <button
                onClick={onRegeneratePin}
                className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-300 hover:text-white transition-colors inline-flex items-center gap-1.5"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                Generate New PIN
              </button>
            </div>
          </div>

          {/* LAN Discovery Notice */}
          <div className="p-3 rounded-xl bg-indigo-950/30 border border-indigo-800/40 flex items-center gap-3 text-xs text-indigo-300">
            <Wifi className="w-4 h-4 text-indigo-400 shrink-0" />
            <span>Make sure both your desktop and Android phone are on the same Wi-Fi network.</span>
          </div>

          {/* Paired Devices List */}
          <div>
            <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Paired Devices ({pairedDevices.length})
            </h4>

            {pairedDevices.length === 0 ? (
              <div className="p-4 rounded-xl bg-slate-950/40 border border-slate-800 text-center text-xs text-slate-500">
                No Android phones paired yet.
              </div>
            ) : (
              <div className="space-y-2">
                {pairedDevices.map((device) => {
                  const isOnline = activeDeviceIds.includes(device.deviceInfo.deviceId);
                  return (
                    <div
                      key={device.deviceInfo.deviceId}
                      className="p-3 rounded-xl bg-slate-950/40 border border-slate-800 flex items-center justify-between"
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`w-2.5 h-2.5 rounded-full ${
                            isOnline ? "bg-emerald-400" : "bg-slate-600"
                          }`}
                        />
                        <div>
                          <div className="text-xs font-semibold text-slate-200">
                            {device.deviceInfo.deviceName}
                          </div>
                          <div className="text-[11px] text-slate-500">
                            {isOnline ? "Connected & Synced" : "Offline"} · Paired{" "}
                            {new Date(device.pairedAt).toLocaleDateString()}
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => onUnpairDevice(device.deviceInfo.deviceId)}
                        title="Unpair device"
                        className="p-1.5 rounded-lg text-slate-500 hover:text-red-400 hover:bg-slate-800 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-slate-800 bg-slate-950 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
