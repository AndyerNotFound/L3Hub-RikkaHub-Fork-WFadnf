// L3Hub · Material 3 Web Preview
// 入口：加载 @material/web 组件、壳层、路由、命令面板与全局事件。
import '@material/web/button/filled-button.js';
import '@material/web/button/filled-tonal-button.js';
import '@material/web/button/outlined-button.js';
import '@material/web/button/text-button.js';
import '@material/web/dialog/dialog.js';
import '@material/web/menu/menu.js';
import '@material/web/menu/menu-item.js';
import '@material/web/divider/divider.js';
import '@material/web/chips/filter-chip.js';
import '@material/web/chips/suggestion-chip.js';
import '@material/web/chips/chip-set.js';
import '@material/web/switch/switch.js';
import '@material/web/slider/slider.js';
import '@material/web/textfield/outlined-text-field.js';
import '@material/web/tabs/tabs.js';
import '@material/web/tabs/primary-tab.js';
import '@material/web/labs/segmentedbutton/outlined-segmented-button.js';
import '@material/web/labs/segmentedbuttonset/outlined-segmented-button-set.js';

import { icon, mark } from './icons.js';
import { assistants, conversations, quickCommands, user } from './data.js';
import { PALETTES, STYLES, paletteById } from './theme.js';
import {
  state,
  runtime,
  bus,
  setTheme,
  toggleTheme,
  navigate,
  currentAssistant,
  currentConversation,
  selectAssistant,
  selectConversation,
  newConversation,
  toast,
} from './core.js';
import { openDialog, closeDialog, snack, avatar, userAvatar, esc, closeMenu } from './ui.js';
import { renderChat, mountChat, chatAction, stopStreaming } from './views/chat.js';
import { renderAssistants, assistantsAction } from './views/assistants.js';
import { renderModels, modelsAction } from './views/models.js';
import { renderWorkspace, mountWorkspace, workspaceAction } from './views/workspace.js';
import { renderSettings, mountSettings, settingsAction, resetSettingsPage } from './views/settings.js';

const NAV = [
  { id: 'chat', label: '对话', icon: 'forum' },
  { id: 'assistants', label: '助手', icon: 'smart_toy' },
  { id: 'models', label: '模型', icon: 'hub' },
  { id: 'workspace', label: '工作区', icon: 'terminal' },
  { id: 'settings', label: '设置', icon: 'settings' },
];

const ROUTES = {
  chat: { title: '对话', render: renderChat, mount: mountChat, action: chatAction },
  assistants: { title: '助手', render: renderAssistants, action: assistantsAction },
  models: { title: '模型', render: renderModels, action: modelsAction },
  workspace: { title: '工作区', render: renderWorkspace, mount: mountWorkspace, action: workspaceAction },
  settings: { title: '设置', render: renderSettings, mount: mountSettings, action: settingsAction },
};

/* ------------------------------------------------------------------ */
/* 壳层                                                                */
/* ------------------------------------------------------------------ */

function navHtml() {
  const a = currentAssistant();
  const recents = conversations.slice(0, 4);
  return `<div class="nav-head">
    <a href="#/chat" class="brand" data-action="go" data-route="chat" aria-label="L3Hub 首页">
      ${mark(38)}
      <span class="brand-text"><span class="brand-name">L3Hub</span><span class="brand-sub">Material 3 Preview</span></span>
    </a>
    <button class="icon-btn nav-close" data-action="close-nav" aria-label="关闭导航">${icon('close', { size: 20 })}</button>
  </div>
  <md-filled-button class="nav-new" data-action="new-chat">
    ${icon('edit', { size: 18, slot: 'icon' })}<span>新对话</span>
  </md-filled-button>
  <button class="icon-btn tonal nav-new-mini" data-action="new-chat" aria-label="新对话">${icon('edit', { size: 21 })}</button>

  <div class="nav-list">
    ${NAV.map(
      (n) => `<button class="nav-item ${state.route === n.id ? 'active' : ''}" data-action="go" data-route="${n.id}" ${
        state.route === n.id ? 'aria-current="page"' : ''
      }>
        <span class="nav-indicator">${icon(n.icon, { size: 22, fill: state.route === n.id })}</span>
        <span class="nav-label">${n.label}</span>
        ${n.id === 'chat' && state.streaming ? '<span class="nav-badge">·</span>' : ''}
      </button>`
    ).join('')}
  </div>

  <div class="nav-section"><span>最近</span><button class="icon-btn xs" data-action="open-palette" aria-label="搜索会话">${icon(
    'search',
    { size: 15 }
  )}</button></div>
  <div class="nav-recents">
    ${recents
      .map(
        (c) => `<button class="recent-item ${c.id === state.conversationId && state.route === 'chat' ? 'active' : ''}" data-action="open-conv" data-conv="${esc(
          c.id
        )}">
          <span>${esc(c.title)}</span>${c.pinned ? icon('push_pin', { size: 12 }) : ''}
        </button>`
      )
      .join('')}
  </div>

  <div class="nav-foot">
    <button class="assistant-switch" data-action="open-assistant-picker">
      ${avatar(a, 'sm')}
      <span class="as-text"><span class="as-name">${esc(a.name)}</span><span class="as-model">${esc(a.model)}</span></span>
      ${icon('expand_more', { size: 16 })}
    </button>
    <button class="user-row" data-action="go" data-route="settings">
      ${userAvatar('sm')}
      <span class="u-text"><span class="u-name">${esc(user.name)}</span><span class="u-sub">L.3.3.1.4 · 本地预览</span></span>
      ${icon('settings', { size: 17 })}
    </button>
  </div>`;
}

function mobileTopHtml() {
  const r = ROUTES[state.route];
  const chat = state.route === 'chat';
  const title = chat ? currentConversation()?.title || '新对话' : r.title;
  return `<button class="icon-btn" data-action="open-nav" aria-label="打开导航">${icon('menu', { size: 22 })}</button>
    <span class="mt-title">${esc(title)}</span>
    ${
      chat
        ? `<button class="icon-btn" data-action="open-list" aria-label="会话列表">${icon('view_agenda', { size: 20 })}</button>
           <button class="icon-btn" data-action="open-details" aria-label="详情">${icon('layers', { size: 20 })}</button>`
        : ''
    }
    <button class="icon-btn" data-action="open-palette" aria-label="搜索">${icon('search', { size: 20 })}</button>
    <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 20 })}</button>`;
}

function shell() {
  document.getElementById('app').innerHTML = `
    <aside class="nav" id="nav" aria-label="主导航"></aside>
    <div class="nav-scrim" data-action="close-nav"></div>
    <div class="main">
      <header class="mobile-top glass" id="mobile-top"></header>
      <div id="view"></div>
    </div>`;
}

function parseRoute() {
  const raw = location.hash.replace(/^#\/?/, '').split('?')[0];
  return ROUTES[raw] ? raw : 'chat';
}

let lastRoute = null;

function render() {
  const app = document.getElementById('app');
  app.dataset.route = state.route;
  document.getElementById('nav').innerHTML = navHtml();
  document.getElementById('mobile-top').innerHTML = mobileTopHtml();
  app.classList.toggle('nav-open', state.navOpen);

  if (lastRoute && lastRoute !== state.route) {
    closeMenu();
    closeDialog();
    if (state.streaming) stopStreaming(true);
  }
  if (state.route === 'settings' && lastRoute !== 'settings') resetSettingsPage();
  lastRoute = state.route;

  const view = document.getElementById('view');
  view.innerHTML = ROUTES[state.route].render();
  view.classList.remove('view-enter');
  void view.offsetWidth;
  view.classList.add('view-enter');
  ROUTES[state.route].mount?.();
  runtime.render = render;
  runtime.openAssistantPicker = openAssistantPicker;
  runtime.closePanels = closePanels;
  document.title = `L3Hub · ${ROUTES[state.route].title}`;
  syncModeControl();
}

function go(route) {
  state.navOpen = false;
  state.listOpen = false;
  state.detailsOpen = false;
  syncModeControl();
  if (state.route === route && location.hash === `#/${route}`) {
    render();
    return;
  }
  if (state.route === route) {
    render();
    return;
  }
  location.hash = `#/${route}`;
}

function syncModeControl() {
  const set = document.querySelector('[data-mode-set]');
  if (!set) return;
  const modes = ['system', 'light', 'dark'];
  [...set.querySelectorAll('md-outlined-segmented-button')].forEach((b, i) => {
    b.selected = modes[i] === state.theme.mode;
  });
}

function closePanels() {
  state.listOpen = false;
  state.detailsOpen = false;
  document.getElementById('chat')?.classList.remove('list-open', 'details-open');
  closeMenu();
}

function newChat() {
  if (state.streaming) stopStreaming(true);
  newConversation();
  state.navOpen = false;
  state.listOpen = false;
  if (state.route !== 'chat') {
    location.hash = '#/chat';
    setTimeout(() => runtime.focusComposer?.(), 320);
  } else {
    render();
    setTimeout(() => runtime.focusComposer?.(), 120);
  }
}

/* ------------------------------------------------------------------ */
/* 助手选择                                                            */
/* ------------------------------------------------------------------ */

function openAssistantPicker() {
  openDialog({
    headline: '切换助手',
    iconName: 'smart_toy',
    content: `<p class="dialog-lead">选择后会为这个助手新建一个对话；旧会话与它的记忆保持不变。</p>
      ${assistants
        .map(
          (a) => `<button class="assistant-pick ${a.id === state.assistantId ? 'active' : ''}" data-action="assistant-pick" data-id="${esc(
            a.id
          )}">
            ${avatar(a, 'sm')}
            <span style="min-width:0"><span class="ap-name">${esc(a.name)}</span><span class="ap-sub">${esc(a.model)} · ${esc(
            a.provider
          )}</span></span>
            ${a.id === state.assistantId ? `<span class="ap-check">${icon('check', { size: 18 })}</span>` : ''}
          </button>`
        )
        .join('')}`,
    actions: `<md-text-button data-action="close-dialog">取消</md-text-button><md-text-button data-action="open-assistants-page">管理助手</md-text-button>`,
  });
  runtime.openAssistantPicker = openAssistantPicker;
}

/* ------------------------------------------------------------------ */
/* 命令面板                                                            */
/* ------------------------------------------------------------------ */

let paletteActive = 0;
let paletteResults = [];

function paletteData(query) {
  const q = query.trim().toLowerCase();
  const cmds = quickCommands
    .filter((c) => !q || c.label.toLowerCase().includes(q) || c.id.includes(q))
    .map((c) => ({ command: `cmd:${c.id}`, label: c.label, hint: c.hint, icon: c.icon, group: c.group }));
  const convs = conversations
    .filter((c) => q && c.title.toLowerCase().includes(q))
    .slice(0, 5)
    .map((c) => ({ command: `conv:${c.id}`, label: c.title, hint: c.folder, icon: 'chat_bubble', group: '会话' }));
  const asts = assistants
    .filter((a) => q && (a.name.toLowerCase().includes(q) || a.tags.join('').includes(q)))
    .slice(0, 4)
    .map((a) => ({ command: `assistant:${a.id}`, label: a.name, hint: a.model, icon: 'smart_toy', group: '助手' }));
  const items = [...cmds, ...convs, ...asts];
  if (!q) items.push(...conversations.slice(0, 3).map((c) => ({ command: `conv:${c.id}`, label: c.title, hint: '最近', icon: 'history', group: '会话' })));
  return items;
}

function paletteListHtml(query) {
  paletteResults = paletteData(query);
  paletteActive = 0;
  if (!paletteResults.length) return '<div class="palette-empty">没有找到匹配项。试试「模型」或「新对话」。</div>';
  let lastGroup = '';
  return paletteResults
    .map((it, i) => {
      const group = it.group !== lastGroup ? `<div class="palette-group">${esc(it.group)}</div>` : '';
      lastGroup = it.group;
      return `${group}<button class="palette-item ${i === 0 ? 'active' : ''}" data-command="${esc(it.command)}">
        <span class="pi-ico">${icon(it.icon || 'chevron_right', { size: 17 })}</span>
        <span class="pi-label">${esc(it.label)}</span>
        ${it.hint ? `<span class="pi-hint">${esc(it.hint)}</span>` : ''}
      </button>`;
    })
    .join('');
}

function openPalette() {
  openDialog({
    headline: '命令面板',
    className: 'palette-dialog',
    content: `<div class="palette-search">${icon('search', { size: 19 })}<input id="palette-input" placeholder="搜索会话、助手或命令…" autocomplete="off" aria-label="命令面板搜索"></div>
      <div class="palette-list" id="palette-list"></div>
      <div class="dialog-foot"><span>↑↓ 选择</span><span>Enter 打开</span><span>Esc 关闭</span><span class="spacer"></span><span class="kbd">⌘K</span></div>`,
    actions: '',
    onMount(dialog) {
      const input = dialog.querySelector('#palette-input');
      const list = dialog.querySelector('#palette-list');
      list.innerHTML = paletteListHtml('');
      input.addEventListener('input', () => {
        list.innerHTML = paletteListHtml(input.value);
      });
      input.addEventListener('keydown', (e) => {
        const items = [...list.querySelectorAll('.palette-item')];
        if (!items.length) return;
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          paletteActive = (paletteActive + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
          items.forEach((it, i) => it.classList.toggle('active', i === paletteActive));
          items[paletteActive].scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter') {
          e.preventDefault();
          items[paletteActive]?.click();
        }
      });
      setTimeout(() => input.focus(), 80);
    },
  });
}

function runCommand(command) {
  if (!command) return;
  if (command.startsWith('conv:')) {
    selectConversation(command.slice(5));
    go('chat');
    return;
  }
  if (command.startsWith('assistant:')) {
    selectAssistant(command.slice(10));
    toast(`已切换到 ${currentAssistant().name}`);
    render();
    return;
  }
  const id = command.replace(/^cmd:/, '');
  if (id === 'new-chat') return newChat();
  if (id === 'go-assistants') return go('assistants');
  if (id === 'go-models') return go('models');
  if (id === 'go-workspace') return go('workspace');
  if (id === 'go-settings') return go('settings');
  if (id === 'toggle-theme') return toggleTheme();
  if (id === 'toggle-details') {
    state.detailsOpen = !state.detailsOpen;
    if (state.route !== 'chat') go('chat');
    else {
      document.getElementById('chat')?.classList.toggle('details-open', state.detailsOpen);
    }
    return;
  }
  if (id === 'cycle-palette') {
    const i = Math.max(0, PALETTES.findIndex((p) => p.id === state.theme.palette));
    const p = PALETTES[(i + 1) % PALETTES.length];
    setTheme({ palette: p.id, seed: p.seed, style: p.style });
    toast(`调色板：${p.name}`);
    syncModeControl();
    return;
  }
}

/* ------------------------------------------------------------------ */
/* 全局事件                                                            */
/* ------------------------------------------------------------------ */

function handleGlobal(action, el) {
  switch (action) {
    case 'go':
      go(el.dataset.route);
      return true;
    case 'open-nav':
      state.navOpen = true;
      document.getElementById('app').classList.add('nav-open');
      return true;
    case 'close-nav':
      state.navOpen = false;
      document.getElementById('app').classList.remove('nav-open');
      return true;
    case 'new-chat':
      newChat();
      return true;
    case 'toggle-theme':
      toggleTheme();
      syncModeControl();
      return true;
    case 'open-palette':
      openPalette();
      return true;
    case 'open-assistant-picker':
      openAssistantPicker();
      return true;
    case 'assistant-pick':
      selectAssistant(el.dataset.id);
      newConversation();
      closeDialog();
      if (state.route === 'chat') render();
      else go('chat');
      toast(`已切换到 ${currentAssistant().name}，新开了一个对话`);
      return true;
    case 'open-assistants-page':
      closeDialog();
      go('assistants');
      return true;
    case 'open-conv':
      if (state.streaming) stopStreaming(true);
      selectConversation(el.dataset.conv);
      go('chat');
      return true;
    case 'close-dialog':
      closeDialog();
      return true;
    case 'command': {
      const cmd = el.dataset.command;
      closeDialog();
      runCommand(cmd);
      return true;
    }
    case 'copy-code':
    case 'copy-msg':
    case 'send':
    case 'stop':
    case 'suggest':
    case 'open-list':
    case 'close-list':
    case 'open-details':
    case 'close-details':
      return false;
    default:
      return false;
  }
}

function onClick(e) {
  // 命令面板条目没有 data-action，先单独处理。
  const cmd = e.target.closest?.('[data-command]');
  if (cmd) {
    closeDialog();
    runCommand(cmd.dataset.command);
    return;
  }

  const actionEl = e.target.closest?.('[data-action]');
  if (actionEl) {
    const action = actionEl.dataset.action;

    if (handleGlobal(action, actionEl, e)) return;

    const handler = ROUTES[state.route]?.action;
    if (handler?.(action, actionEl, e)) return;

    // 一些跨页的轻量动作
    if (action === 'close-nav') return;
  }
}

function onKeydown(e) {
  const meta = e.metaKey || e.ctrlKey;
  if (meta && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    openPalette();
    return;
  }
  if (e.key === 'Escape') {
    if (state.navOpen) {
      state.navOpen = false;
      document.getElementById('app').classList.remove('nav-open');
      return;
    }
    if (state.listOpen || state.detailsOpen) {
      closePanels();
      return;
    }
    closeMenu();
  }
}

/* ------------------------------------------------------------------ */
/* 启动                                                                */
/* ------------------------------------------------------------------ */

function boot() {
  shell();
  state.route = parseRoute();
  if (state.theme.density) document.documentElement.dataset.density = state.theme.density;
  render();
  runtime.render = render;

  bus.addEventListener('snack', (e) => snack(e.detail.message, e.detail.opts || {}));
  bus.addEventListener('toast', (e) => snack(e.detail));

  window.addEventListener('hashchange', () => {
    const next = parseRoute();
    if (next === state.route) return;
    state.route = next;
    state.navOpen = false;
    state.listOpen = false;
    state.detailsOpen = false;
    document.getElementById('app')?.classList.remove('nav-open');
    render();
    document.querySelector('#view .page')?.scrollTo({ top: 0 });
  });

  document.addEventListener('click', onClick);
  document.addEventListener('keydown', onKeydown);
  document.addEventListener('change', (e) => {
    const sw = e.target.closest?.('[data-switch]');
    if (sw) {
      const names = { write: '工作区写入', read: '自动批准读取' };
      toast(`${names[sw.dataset.switch] || '权限'} · ${sw.selected ? '已开启' : '已关闭'}`);
    }
  });

  // 首次进入的详情栏状态
  if (state.detailsOpen) document.getElementById('chat')?.classList.add('details-open');

  const bootEl = document.getElementById('boot');
  const finish = () => {
    bootEl?.classList.add('boot-out');
    setTimeout(() => bootEl?.remove(), 420);
  };
  const fonts = document.fonts?.ready || Promise.resolve();
  Promise.race([fonts, new Promise((r) => setTimeout(r, 900))]).then(() => setTimeout(finish, 160));
}

boot();
