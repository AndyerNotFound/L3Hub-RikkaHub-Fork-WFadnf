import * as React from "react";
import { LoaderCircle, Megaphone } from "lucide-react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { Button } from "~/components/ui/button";
import { Popover, PopoverContent, PopoverTitle, PopoverTrigger } from "~/components/ui/popover";
import { Textarea } from "~/components/ui/textarea";
import { cn } from "~/lib/utils";
import type { QuickMessage } from "~/types";

import type { GuidanceMode } from "~/components/chat/guidance-panel";

interface GuidanceButtonProps {
  disabled?: boolean;
  presets?: QuickMessage[];
  onAdd: (content: string, title?: string, mode?: GuidanceMode) => Promise<void>;
}

/** 生成过程中追加一句指令，插进正在跑的工具间隙，不打断这一轮 */
export function GuidanceButton({ disabled = false, presets = [], onAdd }: GuidanceButtonProps) {
  const { t } = useTranslation("input");
  const [open, setOpen] = React.useState(false);
  const [content, setContent] = React.useState("");
  const [title, setTitle] = React.useState("");
  const [mode, setMode] = React.useState<GuidanceMode>("single");
  const [submitting, setSubmitting] = React.useState(false);

  const canSubmit = content.trim().length > 0 && !submitting;

  const handleAdd = React.useCallback(async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await onAdd(content, title, mode);
      setContent("");
      setTitle("");
      setOpen(false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("guidance.add_failed"));
    } finally {
      setSubmitting(false);
    }
  }, [canSubmit, content, mode, onAdd, t, title]);

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled) {
          setOpen(false);
          return;
        }
        setOpen(nextOpen);
      }}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          disabled={disabled}
          title={t("guidance.button")}
          aria-label={t("guidance.button")}
          className="size-8 rounded-full text-muted-foreground hover:text-foreground"
        >
          <Megaphone className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent side="top" align="start" className="w-[min(92vw,22rem)] space-y-3 p-3">
        <div className="space-y-1">
          <PopoverTitle className="text-sm">{t("guidance.title")}</PopoverTitle>
          <p className="text-muted-foreground text-[11px] leading-snug">{t("guidance.description")}</p>
        </div>

        {presets.length > 0 ? (
          <div className="flex max-h-20 flex-wrap gap-1 overflow-y-auto">
            {presets.map((preset, index) => (
              <button
                key={`${preset.id}-${index}`}
                type="button"
                className="bg-muted/60 hover:bg-muted max-w-full truncate rounded-full border px-2.5 py-1 text-[11px] transition"
                title={preset.content}
                onClick={() => {
                  setTitle(preset.title);
                  setContent(preset.content);
                }}
              >
                {preset.title.trim() || preset.content.slice(0, 24)}
              </button>
            ))}
          </div>
        ) : null}

        <Textarea
          value={content}
          onChange={(event) => {
            setContent(event.target.value);
            if (!event.target.value.trim()) setTitle("");
          }}
          rows={3}
          placeholder={t("guidance.placeholder")}
          className="max-h-32 min-h-16 resize-none text-xs"
        />

        <div className="flex items-center justify-between gap-2">
          <div className="bg-muted/60 inline-flex rounded-full border p-0.5">
            {(["single", "repeat"] as const).map((value) => (
              <button
                key={value}
                type="button"
                className={cn(
                  "rounded-full px-2.5 py-0.5 text-[11px] transition",
                  mode === value
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground",
                )}
                onClick={() => setMode(value)}
              >
                {t(`guidance.${value}`)}
              </button>
            ))}
          </div>
          <Button
            type="button"
            size="sm"
            className="h-7 rounded-full px-3 text-xs"
            disabled={!canSubmit}
            onClick={() => {
              void handleAdd();
            }}
          >
            {submitting ? <LoaderCircle className="size-3.5 animate-spin" /> : t("guidance.add")}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
