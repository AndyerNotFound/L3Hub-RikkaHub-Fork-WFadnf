import * as React from "react";
import { useTranslation } from "react-i18next";

import { Popover, PopoverContent, PopoverTitle, PopoverTrigger } from "~/components/ui/popover";
import {
  formatTokenCount,
  getContextTokens,
  getContextUsageFraction,
  getLastAssistantUsage,
  isContextLimitReached,
} from "~/lib/context-usage";
import { cn } from "~/lib/utils";
import { useAppStore } from "~/stores";
import type { MessageDto, ProviderModel } from "~/types";

const ALERT_FRACTION = 0.8;

interface ContextUsageMeterProps {
  messages: MessageDto[];
  model: ProviderModel | null;
  className?: string;
}

function percentText(used: number, limit?: number | null) {
  if (!limit || limit <= 0) return "0%";
  return `${Math.round((used / limit) * 100)}%`;
}

function meterColor(fraction: number): string {
  if (fraction >= 1) return "bg-destructive";
  if (fraction >= ALERT_FRACTION) return "bg-amber-500";
  return "bg-primary";
}

function MeterRow({
  label,
  used,
  limit,
}: {
  label: string;
  used: number;
  limit: number;
}) {
  const fraction = getContextUsageFraction(used, limit);
  const alert = fraction >= ALERT_FRACTION;

  return (
    <div className="flex items-center gap-2 text-[11px] leading-none">
      <span
        className={cn(
          "w-9 shrink-0 font-medium",
          alert ? "text-amber-600 dark:text-amber-500" : "text-muted-foreground",
        )}
      >
        {label}
      </span>
      <div className="bg-foreground/15 h-1.5 min-w-0 flex-1 overflow-hidden rounded-full">
        <div
          className={cn("h-full rounded-full transition-[width] duration-300", meterColor(fraction))}
          style={{ width: `${fraction * 100}%` }}
        />
      </div>
      <span
        className={cn(
          "shrink-0 font-mono tabular-nums",
          alert ? "text-amber-600 dark:text-amber-500" : "text-muted-foreground",
        )}
      >
        {formatTokenCount(used)} / {formatTokenCount(limit)} · {percentText(used, limit)}
      </span>
    </div>
  );
}

function UsageDetails({
  messages,
  model,
  used,
}: {
  messages: MessageDto[];
  model: ProviderModel | null;
  used: number | null;
}) {
  const { t } = useTranslation("page");
  const last = getLastAssistantUsage(messages);

  return (
    <div className="space-y-2 text-xs">
      <PopoverTitle className="text-sm">{t("context_meter.usage_title")}</PopoverTitle>
      {used != null ? (
        <div className="text-muted-foreground flex items-center justify-between">
          <span>{t("context_meter.context_used")}</span>
          <span className="text-foreground font-mono tabular-nums">
            {formatTokenCount(used)}
            {model?.contextLength ? ` / ${formatTokenCount(model.contextLength)}` : ""}
          </span>
        </div>
      ) : null}
      {last ? (
        <>
          <div className="text-muted-foreground flex items-center justify-between">
            <span>{t("context_meter.prompt_tokens")}</span>
            <span className="text-foreground font-mono tabular-nums">
              {formatTokenCount(last.usage.promptTokens)}
            </span>
          </div>
          <div className="text-muted-foreground flex items-center justify-between">
            <span>{t("context_meter.completion_tokens")}</span>
            <span className="text-foreground font-mono tabular-nums">
              {formatTokenCount(last.usage.completionTokens)}
            </span>
          </div>
          {last.usage.cachedTokens > 0 ? (
            <div className="text-muted-foreground flex items-center justify-between">
              <span>{t("context_meter.cached_tokens")}</span>
              <span className="text-foreground font-mono tabular-nums">
                {formatTokenCount(last.usage.cachedTokens)}
              </span>
            </div>
          ) : null}
        </>
      ) : (
        <p className="text-muted-foreground">{t("context_meter.no_usage")}</p>
      )}
    </div>
  );
}

function ContextUsageRing({
  used,
  warnTokens,
  maxTokens,
  absolute,
  children,
}: {
  used: number;
  warnTokens?: number | null;
  maxTokens?: number | null;
  absolute: boolean;
  children: React.ReactNode;
}) {
  const limit = maxTokens && maxTokens > 0 ? maxTokens : (warnTokens ?? 0);
  const fraction = getContextUsageFraction(used, limit);
  const percent = percentText(used, limit);
  const strokeColor =
    fraction >= 1 ? "stroke-destructive" : fraction >= ALERT_FRACTION ? "stroke-amber-500" : "stroke-primary";

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="text-muted-foreground hover:bg-muted hover:text-foreground relative inline-flex size-8 shrink-0 items-center justify-center rounded-full transition"
          aria-label={percent}
          title={percent}
        >
          <svg className="size-7 -rotate-90" viewBox="0 0 36 36">
            <circle className="stroke-foreground/15" cx="18" cy="18" r="15" fill="none" strokeWidth="3" />
            <circle
              className={cn("transition-[stroke-dashoffset] duration-300", strokeColor)}
              cx="18"
              cy="18"
              r="15"
              fill="none"
              strokeWidth="3"
              strokeLinecap="round"
              strokeDasharray={`${2 * Math.PI * 15}`}
              strokeDashoffset={`${2 * Math.PI * 15 * (1 - fraction)}`}
            />
          </svg>
          <span className="text-[9px] font-medium tabular-nums">
            {absolute ? Math.round(fraction * 100) : percent.replace("%", "")}
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent side="top" align="end" className="w-56">
        {children}
      </PopoverContent>
    </Popover>
  );
}

export function ContextUsageMeter({ messages, model, className }: ContextUsageMeterProps) {
  const { t } = useTranslation("page");
  const displaySetting = useAppStore((state) => state.settings?.displaySetting);
  const style = displaySetting?.contextMeterStyle ?? "BAR";
  const absolute = displaySetting?.contextMeterAbsolute ?? false;

  const used = React.useMemo(() => getContextTokens(messages), [messages]);
  const warnTokens = model?.warnContextTokens ?? null;
  const maxTokens = model?.maxContextTokens ?? null;
  const limitReached = isContextLimitReached(used, maxTokens);

  if (!model || style === "OFF" || (warnTokens == null && maxTokens == null)) {
    return null;
  }

  const usedTokens = used ?? 0;
  const details = <UsageDetails messages={messages} model={model} used={used} />;

  if (style === "RING") {
    return (
      <ContextUsageRing
        used={usedTokens}
        warnTokens={warnTokens}
        maxTokens={maxTokens}
        absolute={absolute}
        children={details}
      />
    );
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(
            "hover:bg-muted/60 w-full space-y-1 rounded-lg px-1 py-0.5 text-left transition-colors",
            className,
          )}
        >
          {warnTokens != null ? (
            <MeterRow label="WARN" used={usedTokens} limit={warnTokens} />
          ) : null}
          {maxTokens != null ? <MeterRow label="MAX" used={usedTokens} limit={maxTokens} /> : null}
          {limitReached ? (
            <p className="text-destructive text-[11px] leading-tight">
              {t("context_meter.limit_reached")}
            </p>
          ) : null}
        </button>
      </PopoverTrigger>
      <PopoverContent side="top" align="start" className="w-56">
        {details}
      </PopoverContent>
    </Popover>
  );
}
