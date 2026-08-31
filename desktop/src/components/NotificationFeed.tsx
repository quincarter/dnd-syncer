import React, { useState } from "react";
import { Bell, X, Send, MessageSquare, Inbox } from "lucide-react";
import type { NotificationItem } from "../../../protocol/types";

interface NotificationFeedProps {
  notifications: NotificationItem[];
  onDismiss: (id: string, packageName: string) => void;
  onReply: (id: string, actionId: string, packageName: string, text: string) => void;
}

export const NotificationFeed: React.FC<NotificationFeedProps> = ({
  notifications,
  onDismiss,
  onReply,
}) => {
  const [replyingId, setReplyingId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState<{ [id: string]: string }>({});

  const formatTime = (timestamp: number) => {
    const diffSecs = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
    if (diffSecs < 60) return "Just now";
    const diffMins = Math.floor(diffSecs / 60);
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    return new Date(timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  };

  const handleSendReply = (notif: NotificationItem, actionId: string) => {
    const text = replyText[notif.id]?.trim();
    if (!text) return;
    onReply(notif.id, actionId, notif.packageName, text);
    setReplyText((prev) => ({ ...prev, [notif.id]: "" }));
    setReplyingId(null);
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-slate-900/40 rounded-2xl border border-slate-800/80 p-5">
      <div className="flex items-center justify-between pb-4 border-b border-slate-800/60">
        <div className="flex items-center gap-2">
          <Bell className="w-4 h-4 text-indigo-400" />
          <h3 className="text-sm font-semibold text-slate-200">Synced Notifications</h3>
          <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-medium">
            {notifications.length}
          </span>
        </div>
      </div>

      {/* Notification List */}
      <div className="flex-1 overflow-y-auto pt-3 space-y-3 pr-1">
        {notifications.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center py-16 text-center">
            <div className="w-12 h-12 rounded-2xl bg-slate-800/60 border border-slate-700/50 flex items-center justify-center text-slate-500 mb-3">
              <Inbox className="w-6 h-6" />
            </div>
            <p className="text-sm font-medium text-slate-300">All caught up!</p>
            <p className="text-xs text-slate-500 max-w-xs mt-1">
              Incoming notifications on your Android phone will appear here in real-time.
            </p>
          </div>
        ) : (
          notifications.map((notif) => {
            const isReplying = replyingId === notif.id;
            const replyAction = notif.actions.find((a) => a.isReply);

            return (
              <div
                key={notif.id}
                className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 hover:border-slate-700 transition-all duration-150 shadow-sm relative group"
              >
                {/* Dismiss button */}
                <button
                  onClick={() => onDismiss(notif.id, notif.packageName)}
                  title="Dismiss notification"
                  className="absolute top-3 right-3 p-1 rounded-lg text-slate-500 hover:text-slate-200 hover:bg-slate-800 transition-colors opacity-80 group-hover:opacity-100"
                >
                  <X className="w-4 h-4" />
                </button>

                <div className="flex items-start gap-3">
                  {/* App Icon or Package Badge */}
                  <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500/20 to-violet-500/20 border border-indigo-500/30 flex items-center justify-center text-indigo-300 font-bold text-xs shrink-0 overflow-hidden">
                    {notif.appIconBase64 ? (
                      <img
                        src={`data:image/png;base64,${notif.appIconBase64}`}
                        alt={notif.appName}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      notif.appName.charAt(0).toUpperCase()
                    )}
                  </div>

                  <div className="flex-1 min-w-0 pr-6">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-indigo-300 truncate">
                        {notif.appName}
                      </span>
                      <span className="text-[11px] text-slate-500">·</span>
                      <span className="text-[11px] text-slate-400">{formatTime(notif.timestamp)}</span>
                    </div>

                    <h4 className="text-sm font-semibold text-slate-100 mt-0.5 leading-snug break-words">
                      {notif.title}
                    </h4>
                    <p className="text-xs text-slate-300 mt-0.5 leading-relaxed break-words whitespace-pre-line">
                      {notif.text}
                    </p>

                    {notif.subText && (
                      <p className="text-[11px] text-slate-500 mt-1 italic">{notif.subText}</p>
                    )}

                    {/* Action buttons */}
                    <div className="flex flex-wrap items-center gap-2 mt-3 pt-2 border-t border-slate-800/60">
                      {replyAction && !isReplying && (
                        <button
                          onClick={() => setReplyingId(notif.id)}
                          className="px-2.5 py-1 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/30 text-indigo-300 text-xs font-medium flex items-center gap-1.5 transition-colors"
                        >
                          <MessageSquare className="w-3 h-3" />
                          Reply
                        </button>
                      )}

                      {notif.actions
                        .filter((a) => !a.isReply)
                        .map((action) => (
                          <button
                            key={action.id}
                            onClick={() => onDismiss(notif.id, notif.packageName)}
                            className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
                          >
                            {action.title}
                          </button>
                        ))}
                    </div>

                    {/* Inline Reply Input */}
                    {isReplying && replyAction && (
                      <div className="mt-3 flex items-center gap-2">
                        <input
                          type="text"
                          autoFocus
                          placeholder={replyAction.replyPlaceholder || "Type a reply..."}
                          value={replyText[notif.id] || ""}
                          onChange={(e) =>
                            setReplyText((prev) => ({ ...prev, [notif.id]: e.target.value }))
                          }
                          onKeyDown={(e) => {
                            if (e.key === "Enter") handleSendReply(notif, replyAction.id);
                            if (e.key === "Escape") setReplyingId(null);
                          }}
                          className="flex-1 bg-slate-950 border border-indigo-500/40 rounded-lg px-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                        />
                        <button
                          onClick={() => handleSendReply(notif, replyAction.id)}
                          className="p-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white transition-colors"
                          title="Send reply"
                        >
                          <Send className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setReplyingId(null)}
                          className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 transition-colors"
                          title="Cancel"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
