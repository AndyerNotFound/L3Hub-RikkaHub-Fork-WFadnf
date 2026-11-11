import * as React from "react";
import { Megaphone, X } from "lucide-react";
import { useTranslation } from "react-i18next";

import { cn } from "~/lib/utils";
import type { GuidanceDto } from "~/types";

export type GuidanceMode = GuidanceDto["mode"];

interface GuidancePanelProps {
  guidances: GuidanceDto[];
  onToggleMode: (id: string, mode: GuidanceMode) => Promise<void>;
  onRemove: (id: string) => Promise<void>;
  className?: string;
}

/** 生效中的引导：一条一行，点模式切换单轮/多轮，右侧叉取消 */
export function GuidancePanel({
  guidances,
  onToggleMode,
  onRemove,
  className,
}: GuidancePanelProps) {
  const { t } = useTranslation("input");
  const [pendingId, setPendingId] = React.useState<string | null>(null);

  if (guidances.length === 0) return null;

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      {guidances.map((guidance) => {
        const repeat = guidance.mode === "repeat";
        const label = guidance.title.trim() || guidance.content.trim();
        return (
          <div
            key={guidance.id}
            className="border-primary/25 bg-primary/5 flex items-center gap-2 rounded-xl border px-3 py-1.5 text-xs"
          >
            <Megaphone className="text-primary size-3.5 shrink-0" />
            <span className="min-w-0 flex-1 truncate" title={guidance.content}>
              {label}
            </span>
            <button
              type="button"
              disabled={pendingId === guidance.id}
              className={cn(
                "shrink-0 rounded-full border px-2 py-0.5 text-[11px] transition",
                repeat
                  ? "border-primary/50 bg-primary/15 text-primary"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
              title={t("guidance.mode_toggle_hint")}
              onClick={() => {
                setPendingId(guidance.id);
                void onToggleMode(guidance.id, repeat ? "single" : "repeat").finally(() =>
                  setPendingId(null),
                );
              }}
            >
              {repeat ? t("guidance.repeat") : t("guidance.single")}
            </button>
            <button
              type="button"
              aria-label={t("guidance.remove")}
              title={t("guidance.remove")}
              disabled={pendingId === guidance.id}
              className="text-muted-foreground hover:text-foreground shrink-0 rounded p-0.5 transition"
              onClick={() => {
                setPendingId(guidance.id);
                void onRemove(guidance.id).finally(() => setPendingId(null));
              }}
            >
              <X className="size-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
