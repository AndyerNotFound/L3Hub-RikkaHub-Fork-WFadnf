import * as React from "react";

import api from "~/services/api";
import type { ProviderQuotaDto } from "~/types";

const CACHE_TTL_MS = 2 * 60 * 1000;
const quotaCache = new Map<string, { at: number; value: ProviderQuotaDto }>();

function formatWindow(minutes?: number | null): string {
  if (!minutes || minutes <= 0) return "";
  if (minutes % (24 * 60) === 0) return `${minutes / (24 * 60)}d`;
  if (minutes % 60 === 0) return `${minutes / 60}h`;
  return `${minutes}m`;
}

/** 一行文字：余额直接显示，用量窗口写成 "5h 32% · 7d 12%" */
export function formatProviderQuota(quota: ProviderQuotaDto | null): string | null {
  if (!quota || quota.type === "none") return null;
  if (quota.type === "balance") {
    return quota.value ?? null;
  }

  const parts = (quota.windows ?? []).map((window) => {
    const label = formatWindow(window.windowMinutes);
    const percent = `${Math.round(window.usedPercent)}%`;
    return label ? `${label} ${percent}` : percent;
  });
  return parts.length > 0 ? parts.join(" · ") : null;
}

export function useProviderQuota(providerId: string | null | undefined, enabled = true) {
  const [quota, setQuota] = React.useState<ProviderQuotaDto | null>(() => {
    if (!providerId) return null;
    const cached = quotaCache.get(providerId);
    return cached && Date.now() - cached.at < CACHE_TTL_MS ? cached.value : null;
  });

  React.useEffect(() => {
    if (!providerId || !enabled) {
      setQuota(null);
      return;
    }

    const cached = quotaCache.get(providerId);
    if (cached && Date.now() - cached.at < CACHE_TTL_MS) {
      setQuota(cached.value);
      return;
    }

    let mounted = true;
    api
      .get<ProviderQuotaDto>(`providers/${providerId}/quota`)
      .then((value) => {
        quotaCache.set(providerId, { at: Date.now(), value });
        if (mounted) setQuota(value);
      })
      .catch(() => {
        if (mounted) setQuota(null);
      });

    return () => {
      mounted = false;
    };
  }, [enabled, providerId]);

  return React.useMemo(() => formatProviderQuota(quota), [quota]);
}
