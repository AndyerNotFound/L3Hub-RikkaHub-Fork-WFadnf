/**
 * Display settings
 * @see app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt - DisplaySetting
 */
export interface DisplaySetting {
  userNickname: string;
  userAvatar?: AssistantAvatar;
  showUserAvatar: boolean;
  showModelIcon?: boolean;
  showModelName: boolean;
  showTokenUsage: boolean;
  showThinkingContent: boolean;
  autoCloseThinking: boolean;
  codeBlockAutoWrap: boolean;
  codeBlockAutoCollapse: boolean;
  showLineNumbers: boolean;
  sendOnEnter: boolean;
  enableAutoScroll: boolean;
  /** 输入框上方的上下文用量样式；OFF 关闭整块 */
  contextMeterStyle?: "OFF" | "BAR" | "RING";
  /** 用量显示绝对值还是百分比 */
  contextMeterAbsolute?: boolean;
  fontSizeRatio: number;
  pasteLongTextAsFile: boolean;
  pasteLongTextThreshold: number;
  [key: string]: unknown;
}

export interface AssistantTag {
  id: string;
  name: string;
}

export interface AssistantAvatar {
  type?: string;
  content?: string;
  url?: string;
  [key: string]: unknown;
}

export interface AssistantQuickMessage {
  title: string;
  content: string;
}

export interface QuickMessage {
  id: string;
  title: string;
  content: string;
}

export interface ModeInjectionProfile {
  id: string;
  name: string;
  enabled?: boolean;
  [key: string]: unknown;
}

export interface LorebookProfile {
  id: string;
  name: string;
  description?: string;
  enabled?: boolean;
  [key: string]: unknown;
}

export interface AssistantProfile {
  id: string;
  chatModelId?: string | null;
  reasoningLevel?: string | null;
  enableWebSearch?: boolean;
  mcpServers?: string[];
  modeInjectionIds?: string[];
  lorebookIds?: string[];
  allowConversationSystemPrompt?: boolean;
  name: string;
  avatar?: AssistantAvatar;
  useAssistantAvatar?: boolean;
  tags: string[];
  quickMessageIds?: string[];
  [key: string]: unknown;
}

export interface McpToolOption {
  enable: boolean;
  name: string;
  description?: string | null;
  needsApproval?: boolean;
  [key: string]: unknown;
}

export interface McpCommonOptions {
  enable: boolean;
  name: string;
  tools: McpToolOption[];
  [key: string]: unknown;
}

export interface McpServerConfig {
  id: string;
  type?: string;
  commonOptions: McpCommonOptions;
  [key: string]: unknown;
}

export type ModelType = "CHAT" | "IMAGE" | "EMBEDDING";
export type ModelModality = "TEXT" | "IMAGE";
export type ModelAbility = "TOOL" | "REASONING";

export interface BuiltInTool {
  type?: string;
  [key: string]: unknown;
}

export interface ProviderModel {
  id: string;
  modelId: string;
  displayName: string;
  type: ModelType;
  inputModalities?: ModelModality[];
  outputModalities?: ModelModality[];
  abilities?: ModelAbility[];
  tools?: BuiltInTool[];
  /** 上下文窗口大小（token） */
  contextLength?: number;
  /** 性能最佳点门限，超过后提醒 */
  warnContextTokens?: number | null;
  /** 性能骤降点门限，到达后禁止发送 */
  maxContextTokens?: number | null;
  enforceMaxContext?: boolean;
  [key: string]: unknown;
}

export interface ProviderProfile {
  id: string;
  enabled: boolean;
  name: string;
  models: ProviderModel[];
  [key: string]: unknown;
}

export interface SearchServiceOption {
  id: string;
  type?: string;
  [key: string]: unknown;
}

/**
 * App settings (streamed via SSE)
 * @see app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt - Settings
 */
export interface Settings {
  dynamicColor: boolean;
  themeId: string;
  developerMode: boolean;
  displaySetting: DisplaySetting;
  favoriteModels: string[];
  chatModelId: string;
  assistantId: string;
  providers: ProviderProfile[];
  assistants: AssistantProfile[];
  assistantTags: AssistantTag[];
  modeInjections?: ModeInjectionProfile[];
  lorebooks?: LorebookProfile[];
  mcpServers: McpServerConfig[];
  searchServices: SearchServiceOption[];
  quickMessages?: QuickMessage[];
  /** 引导提示词的预设，和快捷消息分开存 */
  guideMessages?: QuickMessage[];
  searchServiceSelected: number;
  [key: string]: unknown;
}
