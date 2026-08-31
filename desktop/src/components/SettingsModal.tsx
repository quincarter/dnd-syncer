import React, { useState } from "react";
import { X, Settings, Plus } from "lucide-react";
import type { AppSettings } from "../../../protocol/types";

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  settings: AppSettings;
  onSaveSettings: (settings: AppSettings) => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  settings,
  onSaveSettings,
}) => {
  const [current, setCurrent] = useState<AppSettings>({ ...settings });
  const [newIgnoredPkg, setNewIgnoredPkg] = useState("");

  if (!isOpen) return null;

  const handleToggle = (key: keyof AppSettings) => {
    setCurrent((prev: AppSettings) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  const handleAddIgnored = () => {
    if (!newIgnoredPkg.trim()) return;
    const pkg = newIgnoredPkg.trim().toLowerCase();
    if (!current.ignoredPackages.includes(pkg)) {
      setCurrent((prev: AppSettings) => ({
        ...prev,
        ignoredPackages: [...prev.ignoredPackages, pkg],
      }));
    }
    setNewIgnoredPkg("");
  };

  const handleRemoveIgnored = (pkgToRemove: string) => {
    setCurrent((prev: AppSettings) => ({
      ...prev,
      ignoredPackages: prev.ignoredPackages.filter((p: string) => p !== pkgToRemove),
    }));
  };

  const handleSave = () => {
    onSaveSettings(current);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-slate-800 text-slate-300">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-white">Sync Preferences</h3>
              <p className="text-xs text-slate-400">Configure DND behavior and notification filters</p>
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
          {/* General Toggles */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Sync Rules
            </h4>

            {/* Bidirectional DND */}
            <div
              onClick={() => handleToggle("autoSyncDndBidirectional")}
              className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 flex items-center justify-between cursor-pointer hover:border-slate-700 transition-colors"
            >
              <div>
                <div className="text-xs font-semibold text-slate-200">
                  Bidirectional Focus Sync
                </div>
                <div className="text-[11px] text-slate-400">
                  Toggling DND on either your phone or desktop automatically syncs to the other.
                </div>
              </div>
              <div
                className={`w-10 h-6 rounded-full transition-colors relative flex items-center p-1 ${
                  current.autoSyncDndBidirectional ? "bg-indigo-600" : "bg-slate-800"
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-white transition-transform ${
                    current.autoSyncDndBidirectional ? "translate-x-4" : "translate-x-0"
                  }`}
                />
              </div>
            </div>

            {/* Mute Desktop Sounds */}
            <div
              onClick={() => handleToggle("muteDesktopWhenPhoneDnd")}
              className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 flex items-center justify-between cursor-pointer hover:border-slate-700 transition-colors"
            >
              <div>
                <div className="text-xs font-semibold text-slate-200">
                  Mute Desktop Alerts in Phone DND
                </div>
                <div className="text-[11px] text-slate-400">
                  Silence desktop sound alerts when Android phone enters DND / Focus mode.
                </div>
              </div>
              <div
                className={`w-10 h-6 rounded-full transition-colors relative flex items-center p-1 ${
                  current.muteDesktopWhenPhoneDnd ? "bg-indigo-600" : "bg-slate-800"
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-white transition-transform ${
                    current.muteDesktopWhenPhoneDnd ? "translate-x-4" : "translate-x-0"
                  }`}
                />
              </div>
            </div>

            {/* Show Desktop Toasts */}
            <div
              onClick={() => handleToggle("showNotificationToasts")}
              className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 flex items-center justify-between cursor-pointer hover:border-slate-700 transition-colors"
            >
              <div>
                <div className="text-xs font-semibold text-slate-200">
                  Desktop Toast Banners
                </div>
                <div className="text-[11px] text-slate-400">
                  Display native OS notification toasts for mirrored phone alerts.
                </div>
              </div>
              <div
                className={`w-10 h-6 rounded-full transition-colors relative flex items-center p-1 ${
                  current.showNotificationToasts ? "bg-indigo-600" : "bg-slate-800"
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-white transition-transform ${
                    current.showNotificationToasts ? "translate-x-4" : "translate-x-0"
                  }`}
                />
              </div>
            </div>
          </div>

          {/* Ignored Packages / App Filter */}
          <div>
            <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              Ignored Apps (Blacklist)
            </h4>
            <p className="text-[11px] text-slate-500 mb-3">
              Notifications from these Android package names will never be mirrored to your desktop.
            </p>

            <div className="flex gap-2 mb-3">
              <input
                type="text"
                placeholder="e.g. com.example.spammyapp"
                value={newIgnoredPkg}
                onChange={(e) => setNewIgnoredPkg(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleAddIgnored()}
                className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              <button
                onClick={handleAddIgnored}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 rounded-xl transition-colors flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" />
                Add
              </button>
            </div>

            <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto p-1">
              {current.ignoredPackages.map((pkg: string) => (
                <span
                  key={pkg}
                  className="px-2.5 py-1 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 flex items-center gap-1.5"
                >
                  {pkg}
                  <button
                    onClick={() => handleRemoveIgnored(pkg)}
                    className="text-slate-500 hover:text-red-400 transition-colors"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-slate-800 bg-slate-950 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-colors"
          >
            Save Changes
          </button>
        </div>
      </div>
    </div>
  );
};
