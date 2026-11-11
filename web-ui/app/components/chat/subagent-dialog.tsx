import * as React from "react";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { useTranslation } from "react-i18next";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { useSubAgents } from "~/hooks/use-subagents";
import { cn } from "~/lib/utils";

interface SubAgentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  conversationId: string | null;
  onOpenConversation: (id: string) => void;
}

function stateOf(node: { state?: string | null; isGenerating: boolean }): "running" | "failed" | "completed" {
  if (node.isGenerating || node.state === "running") return "running";
  if (node.state === "failed") return "failed";
  return "completed";
}

/** 当前会话下的子代理任务树，点一行跳到子会话 */
export function SubAgentDialog({
  open,
  onOpenChange,
  conversationId,
  onOpenConversation,
}: SubAgentDialogProps) {
  const { t } = useTranslation("page");
  const { settings } = useCurrentAssistant();
  const { nodes, loading } = useSubAgents(conversationId, open);

  const assistantName = React.useCallback(
    (assistantId: string) =>
      settings?.assistants.find((assistant) => assistant.id === assistantId)?.name ?? assistantId,
    [settings],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[75svh] max-w-lg gap-3 overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("subagents.title")}</DialogTitle>
          <DialogDescription>{t("subagents.description")}</DialogDescription>
        </DialogHeader>

        {loading && nodes.length === 0 ? (
          <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-sm">
            <Loader2 className="size-4 animate-spin" />
            {t("subagents.loading")}
          </div>
        ) : nodes.length === 0 ? (
          <div className="text-muted-foreground py-8 text-center text-sm">
            {t("subagents.empty")}
          </div>
        ) : (
          <div className="flex flex-col gap-1.5">
            {nodes.map((node) => {
              const state = stateOf(node);
              return (
                <button
                  key={node.id}
                  type="button"
                  className="hover:bg-muted/60 flex w-full items-start gap-2 rounded-xl border px-3 py-2 text-left transition"
                  style={{ marginLeft: node.depth * 14 }}
                  onClick={() => {
                    onOpenConversation(node.id);
                    onOpenChange(false);
                  }}
                >
                  {state === "running" ? (
                    <Loader2 className="text-primary mt-0.5 size-4 shrink-0 animate-spin" />
                  ) : state === "failed" ? (
                    <XCircle className="text-destructive mt-0.5 size-4 shrink-0" />
                  ) : (
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-500" />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-sm font-medium">{node.title}</span>
                      <span
                        className={cn(
                          "shrink-0 rounded-full border px-1.5 py-0.5 text-[10px]",
                          state === "running"
                            ? "border-primary/40 text-primary"
                            : state === "failed"
                              ? "border-destructive/40 text-destructive"
                              : "text-muted-foreground",
                        )}
                      >
                        {t(`subagents.state_${state}`)}
                      </span>
                    </div>
                    <div className="text-muted-foreground mt-0.5 line-clamp-2 text-xs">
                      {node.task?.trim() || assistantName(node.assistantId)}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
