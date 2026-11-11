import * as React from "react";
import { Boxes, LoaderCircle, Star } from "lucide-react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { Button } from "~/components/ui/button";
import { Checkbox } from "~/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { useWorkspaces } from "~/hooks/use-workspaces";
import { cn } from "~/lib/utils";
import type { FolderDto } from "~/types";

interface FolderWorkspacesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  folder: FolderDto | null;
  onSave: (workspaceIds: string[], workspaceId: string | null) => Promise<void>;
}

/** 文件夹绑定一组工作区；落在这个文件夹里的会话都用这组 */
export function FolderWorkspacesDialog({
  open,
  onOpenChange,
  folder,
  onSave,
}: FolderWorkspacesDialogProps) {
  const { t } = useTranslation();
  const { workspaces, loading } = useWorkspaces(open);
  const [selected, setSelected] = React.useState<string[]>([]);
  const [primary, setPrimary] = React.useState<string | null>(null);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    const ids = folder?.workspaceIds ?? [];
    setSelected(ids);
    setPrimary(folder?.workspaceId ?? ids[0] ?? null);
  }, [folder, open]);

  const toggle = React.useCallback((id: string) => {
    setSelected((current) => {
      const next = current.includes(id) ? current.filter((item) => item !== id) : [...current, id];
      setPrimary((currentPrimary) => {
        if (currentPrimary && next.includes(currentPrimary)) return currentPrimary;
        return next[0] ?? null;
      });
      return next;
    });
  }, []);

  const handleSave = React.useCallback(
    async (workspaceIds: string[], workspaceId: string | null) => {
      setSaving(true);
      try {
        await onSave(workspaceIds, workspaceId);
        onOpenChange(false);
      } catch (error) {
        toast.error(error instanceof Error ? error.message : t("conversation_sidebar.folder_bind_failed"));
      } finally {
        setSaving(false);
      }
    },
    [onOpenChange, onSave, t],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[75svh] max-w-md gap-3 overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {t("conversation_sidebar.folder_bind_workspaces", { folder: folder?.name ?? "" })}
          </DialogTitle>
          <DialogDescription>{t("conversation_sidebar.folder_bind_description")}</DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-sm">
            <LoaderCircle className="size-4 animate-spin" />
            {t("conversation_sidebar.workspaces_loading")}
          </div>
        ) : workspaces.length === 0 ? (
          <div className="text-muted-foreground flex flex-col items-center gap-2 py-8 text-center text-sm">
            <Boxes className="size-6" />
            {t("conversation_sidebar.workspaces_empty")}
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {workspaces.map((workspace) => {
              const checked = selected.includes(workspace.id);
              const isPrimary = checked && workspace.id === primary;
              return (
                <div
                  key={workspace.id}
                  className={cn(
                    "hover:bg-muted/60 flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 transition",
                    checked && "border-primary/40 bg-primary/5",
                  )}
                  onClick={() => toggle(workspace.id)}
                >
                  <Checkbox
                    checked={checked}
                    onCheckedChange={() => toggle(workspace.id)}
                    onClick={(event) => event.stopPropagation()}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm">{workspace.name}</div>
                    <div className="text-muted-foreground text-[11px]">{workspace.type}</div>
                  </div>
                  {checked ? (
                    <button
                      type="button"
                      title={t("conversation_sidebar.workspace_set_primary")}
                      aria-label={t("conversation_sidebar.workspace_set_primary")}
                      className={cn(
                        "shrink-0 rounded p-1 transition",
                        isPrimary ? "text-amber-500" : "text-muted-foreground hover:text-foreground",
                      )}
                      onClick={(event) => {
                        event.stopPropagation();
                        setPrimary(workspace.id);
                      }}
                    >
                      <Star className={cn("size-3.5", isPrimary && "fill-current")} />
                    </button>
                  ) : null}
                </div>
              );
            })}
          </div>
        )}

        <DialogFooter className="gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={saving}
            onClick={() => {
              void handleSave([], null);
            }}
          >
            {t("conversation_sidebar.folder_unbind_workspaces")}
          </Button>
          <Button
            type="button"
            size="sm"
            disabled={saving || loading}
            onClick={() => {
              void handleSave(selected, primary ?? selected[0] ?? null);
            }}
          >
            {saving ? <LoaderCircle className="size-3.5 animate-spin" /> : t("common.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
