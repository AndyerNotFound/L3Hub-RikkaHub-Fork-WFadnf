import * as React from "react";

import { useProviderQuota } from "~/hooks/use-provider-quota";
import { cn } from "~/lib/utils";

interface ProviderQuotaBadgeProps {
  providerId?: string | null;
  className?: string;
}

/** 渠道余额 / 订阅用量，查不到时不占位 */
export function ProviderQuotaBadge({ providerId, className }: ProviderQuotaBadgeProps) {
  const text = useProviderQuota(providerId);
  if (!text) return null;

  return (
    <span
      className={cn("text-muted-foreground shrink-0 font-mono text-[11px] tabular-nums", className)}
      title={text}
    >
      {text}
    </span>
  );
}
