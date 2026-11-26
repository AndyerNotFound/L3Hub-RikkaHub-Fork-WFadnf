// 共享状态与主题控制。views 只依赖这里，避免和 app.js 互相 import。
import { applyTheme, loadTheme, saveTheme, isDark, watchSystemTheme } from './theme.js';
import { assistants, conversations } from './data.js';

export const state = {
  route: 'chat',
  theme: loadTheme(),
  assistantId: 'neko',
  conversationId: 'workspace-refactor',
  folder: '全部',
  search: '',
  chatTab: 0,
  thinking: '标准',
  detailsTab: 0,
  detailsOpen: false,
  listOpen: false,
  navOpen: false,
  streaming: false,
  lastReplyText: '',
};

export const bus = new EventTarget();

// app.js 在启动后把自己的渲染函数挂进来，避免循环依赖。
export const runtime = {
  render: () => {},
  renderMessages: () => {},
  openDetails: () => {},
  closePanels: () => {},
  stopStreaming: () => {},
  focusComposer: () => {},
};

export function setTheme(patch, { animate = true, silent = false } = {}) {
  Object.assign(state.theme, patch);
  applyTheme(state.theme, { animate });
  saveTheme(state.theme);
  if (!silent) bus.dispatchEvent(new CustomEvent('theme', { detail: { ...state.theme } }));
}

export function toggleTheme() {
  const dark = isDark(state.theme.mode);
  setTheme({ mode: dark ? 'light' : 'dark' });
  bus.dispatchEvent(new CustomEvent('toast', { detail: dark ? '已切换到浅色' : '已切换到深色' }));
  return !dark;
}

export function currentAssistant() {
  return assistants.find((a) => a.id === state.assistantId) || assistants[0];
}

export function currentConversation() {
  return conversations.find((c) => c.id === state.conversationId) || null;
}

export function selectConversation(id) {
  const conv = conversations.find((c) => c.id === id);
  if (!conv) return false;
  state.conversationId = id;
  state.chatTab = 0;
  if (conv.assistant) state.assistantId = conv.assistant;
  return true;
}

export function selectAssistant(id) {
  const a = assistants.find((x) => x.id === id);
  if (!a) return false;
  state.assistantId = id;
  return true;
}

export function newConversation() {
  const a = currentAssistant();
  const id = `new-${Date.now().toString(36)}`;
  state.chatTab = 0;
  state.folder = '全部';
  const conv = {
    id,
    title: '新对话',
    folder: '未归类',
    pinned: false,
    assistant: a.id,
    model: a.model,
    updated: '刚刚',
    group: '本周',
    messages: [],
  };
  conversations.unshift(conv);
  state.conversationId = id;
  return conv;
}

export function navigate(route) {
  if (!['chat', 'assistants', 'models', 'workspace', 'settings'].includes(route)) route = 'chat';
  if (state.route === route && location.hash === `#/${route}`) return;
  location.hash = `#/${route}`;
}

export function toast(message, opts) {
  bus.dispatchEvent(new CustomEvent('snack', { detail: { message, opts } }));
}

// 系统主题变化时，仅跟随模式下重新生成色板。
watchSystemTheme(() => {
  if (state.theme.mode === 'system') applyTheme(state.theme);
});

applyTheme(state.theme, { animate: false });
