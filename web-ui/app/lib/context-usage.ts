import type { MessageDto } from "~/types";

/**
 * 最近一次请求结束时的上下文占用（prompt + completion）。
 *
 * 压缩检查点之前的 usage 算的是压缩前的上下文，不能作为当前占用；
 * 只有检查点生成之后还在更新的回复才作数。找不到时返回 null。
 */
export function getContextTokens(messages: MessageDto[]): number | null {
  const checkpoint = [...messages]
    .reverse()
    .find((message) => message.isContextCheckpoint);
  const checkpointCreatedAt = checkpoint?.createdAt ?? null;

  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.isContextCheckpoint) {
      return null;
    }
    if (message.role !== "ASSISTANT") continue;

    const usage = message.lastUsage ?? message.usage;
    if (!usage) continue;

    const tokens = usage.promptTokens + usage.completionTokens;
    if (!checkpointCreatedAt) {
      return tokens;
    }
    const updatedAt = message.finishedAt ?? message.createdAt;
    return updatedAt > checkpointCreatedAt ? tokens : null;
  }

  return null;
}

export function getContextUsageFraction(used: number, limit?: number | null): number {
  if (!limit || limit <= 0) return 0;
  return Math.min(Math.max(used / limit, 0), 1);
}

export function isContextLimitReached(
  used: number | null,
  maxTokens?: number | null,
): boolean {
  if (!maxTokens || maxTokens <= 0 || used == null) return false;
  return used >= maxTokens;
}

export function formatTokenCount(value: number): string {
  return value.toLocaleString();
}

/** 最近一条带 usage 的助手回复，供用量明细使用 */
export function getLastAssistantUsage(messages: MessageDto[]) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.role !== "ASSISTANT") continue;
    const usage = message.lastUsage ?? message.usage;
    if (usage) return { message, usage };
  }
  return null;
}
