import * as React from "react";

import api from "~/services/api";
import type { SubAgentConversationDto } from "~/types";

interface SubAgentNode extends SubAgentConversationDto {
  depth: number;
}

const MAX_DEPTH = 2;

async function loadChildren(
  conversationId: string,
  depth: number,
): Promise<SubAgentNode[]> {
  const children = await api.get<SubAgentConversationDto[]>(
    `conversations/${conversationId}/subagents`,
  );
  const nodes: SubAgentNode[] = children.map((child) => ({ ...child, depth }));
  if (depth + 1 >= MAX_DEPTH) return nodes;

  for (const child of children) {
    const grandChildren = await loadChildren(child.id, depth + 1);
    nodes.push(...grandChildren);
  }
  return nodes;
}

/** 当前会话下的子代理任务树，打开面板时轮询 */
export function useSubAgents(conversationId: string | null, enabled: boolean) {
  const [nodes, setNodes] = React.useState<SubAgentNode[]>([]);
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    if (!conversationId || !enabled) {
      setNodes([]);
      return;
    }

    let mounted = true;
    const load = () => {
      void loadChildren(conversationId, 0)
        .then((items) => {
          if (mounted) setNodes(items);
        })
        .catch(() => {
          if (mounted) setNodes([]);
        })
        .finally(() => {
          if (mounted) setLoading(false);
        });
    };

    setLoading(true);
    load();
    const timer = window.setInterval(load, 5000);

    return () => {
      mounted = false;
      window.clearInterval(timer);
    };
  }, [conversationId, enabled]);

  return { nodes, loading };
}
