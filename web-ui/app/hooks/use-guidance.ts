import * as React from "react";

import api from "~/services/api";
import type { GuidanceDto } from "~/types";

export type GuidanceMode = GuidanceDto["mode"];

/** 待发出的引导：和消息队列一样是运行时状态，不落库 */
export function useGuidance(conversationId: string | null) {
  const [guidances, setGuidances] = React.useState<GuidanceDto[]>([]);
  const [loading, setLoading] = React.useState(false);
  const requestEpochRef = React.useRef(0);

  const refresh = React.useCallback(async () => {
    if (!conversationId) {
      setGuidances([]);
      return;
    }
    const epoch = ++requestEpochRef.current;
    setLoading(true);
    try {
      const list = await api.get<GuidanceDto[]>(`conversations/${conversationId}/guidance`);
      if (epoch === requestEpochRef.current) {
        setGuidances(list);
      }
    } catch {
      if (epoch === requestEpochRef.current) {
        setGuidances([]);
      }
    } finally {
      if (epoch === requestEpochRef.current) {
        setLoading(false);
      }
    }
  }, [conversationId]);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const add = React.useCallback(
    async (content: string, title = "", mode: GuidanceMode = "single") => {
      if (!conversationId || content.trim().length === 0) return;
      const list = await api.post<GuidanceDto[]>(`conversations/${conversationId}/guidance`, {
        content,
        title,
        mode,
      });
      setGuidances(list);
    },
    [conversationId],
  );

  const setMode = React.useCallback(
    async (guidanceId: string, mode: GuidanceMode) => {
      if (!conversationId) return;
      const list = await api.post<GuidanceDto[]>(
        `conversations/${conversationId}/guidance/${guidanceId}/mode`,
        { mode },
      );
      setGuidances(list);
    },
    [conversationId],
  );

  const remove = React.useCallback(
    async (guidanceId: string) => {
      if (!conversationId) return;
      const list = await api.delete<GuidanceDto[]>(
        `conversations/${conversationId}/guidance/${guidanceId}`,
      );
      setGuidances(list);
    },
    [conversationId],
  );

  return { guidances, loading, refresh, add, setMode, remove };
}
