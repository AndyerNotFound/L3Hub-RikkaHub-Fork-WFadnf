import type { TokenUsage } from "./core";
import type { UIMessageAnnotation } from "./annotations";
import type { ConversationConfig } from "./conversation";
import type { ModelSnapshot } from "./message";
import type { UIMessagePart } from "./parts";

export interface ConversationListDto {
  id: string;
  assistantId: string;
  title: string;
  isPinned: boolean;
  folderId?: string | null;
  createAt: number;
  updateAt: number;
  isGenerating: boolean;
}

/**
 * Folder (conversation grouping within an assistant)
 * @see app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt - FolderDto
 */
export interface FolderDto {
  id: string;
  assistantId: string;
  name: string;
  sortIndex: number;
  createAt: number;
  /** 主工作区；文件夹绑定后落在里面的会话都用这组工作区 */
  workspaceId?: string | null;
  workspaceIds?: string[];
}

export interface WorkspaceDto {
  id: string;
  name: string;
  type: string;
}

export interface GuidanceDto {
  id: string;
  title: string;
  content: string;
  mode: "single" | "repeat";
}

export interface SubAgentConversationDto {
  id: string;
  parentId: string;
  assistantId: string;
  title: string;
  task?: string | null;
  state?: string | null;
  isGenerating: boolean;
  updateAt: number;
}

export interface ProviderQuotaWindowDto {
  usedPercent: number;
  windowMinutes?: number | null;
  resetsAt?: string | null;
}

export interface ProviderQuotaDto {
  type: "none" | "balance" | "usage";
  value?: string | null;
  limitReached?: boolean;
  windows?: ProviderQuotaWindowDto[];
}

/**
 * Folder list change event (pushed on the shared `/api/events` stream)
 * @see app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt - FolderListEvent
 */
export interface FolderListEventDto {
  assistantId: string;
  folders: FolderDto[];
}

export interface PagedResult<T> {
  items: T[];
  nextOffset?: number | null;
  hasMore: boolean;
}

export interface UploadedFileDto {
  id: number;
  url: string;
  fileName: string;
  mime: string;
  size: number;
}

export interface UploadFilesResponseDto {
  files: UploadedFileDto[];
}

export interface ConversationListInvalidateEventDto {
  type: "invalidate";
  assistantId: string;
  timestamp: number;
}

/**
 * Message DTO (for API response)
 * @see app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt - MessageDto
 */
export interface MessageDto {
  id: string;
  role: string;
  parts: UIMessagePart[];
  annotations?: UIMessageAnnotation[];
  createdAt: string;
  finishedAt?: string | null;
  modelId?: string | null;
  modelSnapshot?: ModelSnapshot | null;
  usage?: TokenUsage | null;
  /** 模型实际生成耗时（毫秒），不含首 token 延迟、工具执行和审批等待 */
  generationTimeMs?: number | null;
  /** 最后一次请求自己的 usage；usage 是多步工具调用的累计值 */
  lastUsage?: TokenUsage | null;
  /** 这条消息累计请求了多少次模型 */
  stepCount?: number | null;
  translation?: string | null;
  /** 压缩检查点：此消息是它之前全部历史的摘要，之前的消息不再发送给模型 */
  isContextCheckpoint?: boolean;
  /** 系统自动生成的子代理回投通知，不是用户输入 */
  isSubAgentNotice?: boolean;
}

export interface MessageNodeDto {
  id: string;
  messages: MessageDto[];
  selectIndex: number;
}

export interface ConversationDto {
  id: string;
  assistantId: string;
  title: string;
  messages: MessageNodeDto[];
  chatSuggestions: string[];
  isPinned: boolean;
  customSystemPrompt?: string | null;
  modeInjectionIds?: string[];
  lorebookIds?: string[];
  config?: ConversationConfig | null;
  workspaceCwd?: string | null;
  workspaceCwds?: Record<string, string>;
  folderId?: string | null;
  /** 子代理会话挂在哪条父会话下 */
  parentConversationId?: string | null;
  /** 派发给子代理的任务原文 */
  subAgentTask?: string | null;
  /** running / completed / failed */
  subAgentState?: string | null;
  createAt: number;
  updateAt: number;
  isGenerating: boolean;
}

export interface ConversationSnapshotEventDto {
  type: "snapshot";
  seq: number;
  conversation: ConversationDto;
  serverTime: number;
}

export interface ConversationNodeUpdateEventDto {
  type: "node_update";
  seq: number;
  conversationId: string;
  nodeId: string;
  nodeIndex: number;
  node: MessageNodeDto;
  updateAt: number;
  isGenerating: boolean;
  serverTime: number;
}

export interface ConversationErrorEventDto {
  type: "error";
  message: string;
}

export interface MessageSearchResultDto {
  nodeId: string;
  messageId: string;
  conversationId: string;
  title: string;
  updateAt: number;
  snippet: string;
}
