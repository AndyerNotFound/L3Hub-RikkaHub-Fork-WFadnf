import * as React from "react";

import api from "~/services/api";
import type { WorkspaceDto } from "~/types";

let cachedWorkspaces: WorkspaceDto[] | null = null;
let inflight: Promise<WorkspaceDto[]> | null = null;

async function loadWorkspaces(): Promise<WorkspaceDto[]> {
  if (cachedWorkspaces) return cachedWorkspaces;
  if (!inflight) {
    inflight = api
      .get<WorkspaceDto[]>("workspaces")
      .then((workspaces) => {
        cachedWorkspaces = workspaces;
        return workspaces;
      })
      .finally(() => {
        inflight = null;
      });
  }
  return inflight;
}

export function useWorkspaces(enabled = true) {
  const [workspaces, setWorkspaces] = React.useState<WorkspaceDto[]>(
    () => cachedWorkspaces ?? [],
  );
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    if (!enabled || cachedWorkspaces) return;

    let mounted = true;
    setLoading(true);
    void loadWorkspaces()
      .then((items) => {
        if (mounted) setWorkspaces(items);
      })
      .catch(() => {
        if (mounted) setWorkspaces([]);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [enabled]);

  return { workspaces, loading };
}
