import React from "react";
import { Moon, Sun, Smartphone, Laptop, Zap, ShieldAlert, CheckCircle2, ExternalLink } from "lucide-react";
import type { DndStatusPayload } from "../../../protocol/types";

interface DndControlCardProps {
  phoneDnd: DndStatusPayload | null;
  desktopDnd: boolean;
  hasActivePhone: boolean;
  hasFullDiskAccess?: boolean;
  onOpenFullDiskAccess?: () => void;
  onToggleDnd: (enable: boolean) => void;
  autoSyncEnabled: boolean;
}

export const DndControlCard: React.FC<DndControlCardProps> = ({
  phoneDnd,
  desktopDnd,
  hasActivePhone,
  hasFullDiskAccess = true,
  onOpenFullDiskAccess,
  onToggleDnd,
  autoSyncEnabled,
}) => {
  const isPhoneDndActive = hasActivePhone && (phoneDnd?.isEnabled ?? false);
  const isAnyDndActive = hasActivePhone ? (isPhoneDndActive || desktopDnd) : desktopDnd;

  const phoneModeLabel = phoneDnd?.modeName || (phoneDnd?.mode ? phoneDnd.mode.replace("_", " ") : "Do Not Disturb");

  return (
    <div className="p-6 rounded-2xl bg-gradient-to-b from-slate-900/90 to-slate-950/90 border border-slate-800/80 shadow-2xl relative overflow-hidden flex flex-col gap-4">
      {/* Glow Effect */}
      <div
        className={`absolute -top-24 -right-24 w-60 h-60 rounded-full blur-3xl pointer-events-none transition-all duration-700 ${
          isAnyDndActive ? "bg-indigo-600/20" : "bg-emerald-600/10"
        }`}
      />

      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 relative z-0">
        {/* Left section: Master Status & Quick Switch */}
        <div className="flex items-start gap-4">
          <div
            className={`w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-500 shadow-xl ${
              isAnyDndActive
                ? "bg-indigo-600 text-white shadow-indigo-600/30 scale-105"
                : "bg-slate-800 text-slate-400 border border-slate-700"
            }`}
          >
            {isAnyDndActive ? <Moon className="w-7 h-7 animate-pulse" /> : <Sun className="w-7 h-7" />}
          </div>

          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-xl font-bold text-white tracking-tight">
                {isAnyDndActive ? "Focus Mode Active" : "Normal Mode (Focus Off)"}
              </h2>
              {autoSyncEnabled && (
                <span className="flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-md bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
                  <Zap className="w-3 h-3 text-indigo-400 fill-indigo-400" />
                  Auto-Sync On
                </span>
              )}
              {hasFullDiskAccess && (
                <span className="flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
                  <CheckCircle2 className="w-3 h-3 text-emerald-400" />
                  Full Disk Access
                </span>
              )}
            </div>
            <p className="text-sm text-slate-400 mt-0.5">
              {isAnyDndActive
                ? "Notifications are silenced and synchronized across phone and desktop."
                : "All incoming notifications and focus modes are currently unmuted."}
            </p>
          </div>
        </div>

        {/* Master Toggle Button */}
        <div>
          <button
            onClick={() => onToggleDnd(!isAnyDndActive)}
            className={`px-5 py-3 rounded-xl font-semibold text-sm transition-all duration-200 shadow-lg flex items-center gap-2 ${
              isAnyDndActive
                ? "bg-indigo-600 hover:bg-indigo-500 text-white shadow-indigo-600/25 active:scale-95 cursor-pointer"
                : "bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 active:scale-95 cursor-pointer"
            }`}
          >
            {isAnyDndActive ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            <span>{isAnyDndActive ? "Turn Focus Off" : "Turn Focus On"}</span>
          </button>
        </div>
      </div>

      {/* Permission banner for macOS Full Disk Access (shown only when not granted) */}
      {!hasFullDiskAccess && (
        <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-between gap-4 mt-2">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-amber-500/20 text-amber-400 shrink-0">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-bold text-amber-200">
                macOS Full Disk Access Required
              </div>
              <div className="text-[11px] text-amber-300/80">
                Allow Full Disk Access in macOS System Settings so DND Syncer can read Focus mode status from Control Center.
              </div>
            </div>
          </div>
          <button
            onClick={onOpenFullDiskAccess}
            className="px-3.5 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition-all shadow-md flex items-center gap-1.5 shrink-0 cursor-pointer"
          >
            <span>Open Settings</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Sub-status grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-4 border-t border-slate-800/60">
        {/* Android Device Status */}
        <div className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-slate-800/80 text-indigo-400">
              <Smartphone className="w-4 h-4" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-200">Android Phone</div>
              <div className="text-[11px] text-slate-400">
                {!hasActivePhone
                  ? "Waiting for connection..."
                  : isPhoneDndActive
                  ? `Mode: ${phoneModeLabel}`
                  : "Normal (Unmuted)"}
              </div>
            </div>
          </div>
          <span
            className={`text-xs px-2.5 py-1 rounded-full font-medium ${
              isPhoneDndActive
                ? "bg-indigo-500/20 text-indigo-300 border border-indigo-500/30"
                : "bg-slate-800 text-slate-400"
            }`}
          >
            {isPhoneDndActive ? "Silenced" : "Active"}
          </span>
        </div>

        {/* Desktop Device Status */}
        <div className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-slate-800/80 text-violet-400">
              <Laptop className="w-4 h-4" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-200">Desktop Focus / DND</div>
              <div className="text-[11px] text-slate-400">
                {desktopDnd ? "Focus Mode Active" : "Alerts Allowed"}
              </div>
            </div>
          </div>
          <span
            className={`text-xs px-2.5 py-1 rounded-full font-medium ${
              desktopDnd
                ? "bg-violet-500/20 text-violet-300 border border-violet-500/30"
                : "bg-slate-800 text-slate-400"
            }`}
          >
            {desktopDnd ? "Focus On" : "Focus Off"}
          </span>
        </div>
      </div>
    </div>
  );
};
