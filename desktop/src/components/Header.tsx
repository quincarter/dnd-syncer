import React from "react";
import { Smartphone, QrCode, Settings, Moon, Sparkles } from "lucide-react";

interface HeaderProps {
  activeDeviceCount: number;
  onOpenPairing: () => void;
  onOpenSettings: () => void;
  onSendTestNotification: () => void;
  onStartDrag: (e: React.MouseEvent) => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeDeviceCount,
  onOpenPairing,
  onOpenSettings,
  onSendTestNotification,
  onStartDrag,
}) => {
  return (
    <header
      data-tauri-drag-region
      onMouseDown={onStartDrag}
      className="h-16 border-b border-slate-800/80 bg-slate-950/70 backdrop-blur-md px-6 flex items-center justify-between z-10 sticky top-0 select-none cursor-default"
    >
      {/* Title area */}
      <div data-tauri-drag-region onMouseDown={onStartDrag} className="flex items-center space-x-3 cursor-default">
        <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center shadow-lg shadow-indigo-500/20 shrink-0">
          <Moon className="w-5 h-5 text-white" />
        </div>
        <div data-tauri-drag-region onMouseDown={onStartDrag}>
          <h1 data-tauri-drag-region onMouseDown={onStartDrag} className="text-base font-semibold tracking-tight text-white flex items-center gap-2">
            DND Syncer
            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              v1.0
            </span>
          </h1>
          <p data-tauri-drag-region onMouseDown={onStartDrag} className="text-xs text-slate-400">Desktop & Android notification bridge</p>
        </div>
      </div>

      {/* Action buttons (stop drag propagation on interactive elements) */}
      <div className="flex items-center space-x-3" onMouseDown={(e) => e.stopPropagation()}>
        {/* Connection status indicator */}
        <div
          onClick={onOpenPairing}
          className={`cursor-pointer px-3 py-1.5 rounded-full border text-xs font-medium flex items-center gap-2 transition-colors ${
            activeDeviceCount > 0
              ? "bg-emerald-950/40 text-emerald-300 border-emerald-800/50 hover:bg-emerald-900/40"
              : "bg-amber-950/40 text-amber-300 border-amber-800/50 hover:bg-amber-900/40"
          }`}
        >
          <span
            className={`w-2 h-2 rounded-full animate-pulse ${
              activeDeviceCount > 0 ? "bg-emerald-400" : "bg-amber-400"
            }`}
          />
          <Smartphone className="w-3.5 h-3.5" />
          <span>
            {activeDeviceCount > 0
              ? `${activeDeviceCount} Phone${activeDeviceCount > 1 ? "s" : ""} Connected`
              : "No Phone Connected"}
          </span>
        </div>

        {/* Test Notification Button */}
        <button
          onClick={onSendTestNotification}
          title="Send sample test notification"
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors flex items-center gap-1.5 text-xs font-medium cursor-pointer"
        >
          <Sparkles className="w-4 h-4 text-violet-400" />
          <span className="hidden sm:inline">Test Alert</span>
        </button>

        {/* Pair Device Button */}
        <button
          onClick={onOpenPairing}
          title="Pair new Android device"
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors flex items-center gap-1.5 text-xs font-medium cursor-pointer"
        >
          <QrCode className="w-4 h-4 text-indigo-400" />
          <span className="hidden sm:inline">Pair Device</span>
        </button>

        {/* Settings Button */}
        <button
          onClick={onOpenSettings}
          title="Settings"
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
        >
          <Settings className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};
