// 对话页：会话列表 / 消息流 / 输入区 / 详情栏。
import { icon } from '../icons.js';
import {
  assistants,
  conversations,
  folders,
  modelList,
  filePreviews,
  suggestions,
  workspaceTasks,
  user,
} from '../data.js';
import {
  state,
  currentAssistant,
  currentConversation,
  selectAssistant,
  selectConversation,
  newConversation,
  runtime,
  toast,
} from '../core.js';
import {
  avatar,
  userAvatar,
  esc,
  md,
  nowTime,
  wait,
  uid,
  copyText,
  openDialog,
  closeDialog,
  showMenu,
  closeMenu,
  scrollToBottom,
} from '../ui.js';

let pendingFiles = [];
let streamToken = 0;
let branchIndex = 1;
let lastPrompt = '';

/* 会话列表的多选 / 折叠 / 底部弹层状态（只属于这一页）。 */
const ARCHIVED_GROUP = '已归档';
const COLLAPSE_KEY = 'l3hub.m3.collapsedGroups';
const savedGroups = loadCollapsed();
const collapsedGroups = new Set(savedGroups || [ARCHIVED_GROUP]);
let selectionMode = false;
const selectedConvs = new Set();
let pendingDelete = [];
let sheetEl = null;
let sheetMode = 'move';
let sheetIds = [];
let sheetAssistant = '';
let sheetFolder = '';
let sheetKeepFolder = true;
let sheetFoldersOpen = true;
let sheetKeyHandler = null;

function loadCollapsed() {
  try {
    const raw = localStorage.getItem(COLLAPSE_KEY);
    if (raw === null) return null;
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string') : null;
  } catch {
    return null;
  }
}

function saveCollapsed() {
  try {
    localStorage.setItem(COLLAPSE_KEY, JSON.stringify([...collapsedGroups]));
  } catch {
    /* 隐私模式下忽略 */
  }
}

function assistantById(id) {
  return assistants.find((a) => a.id === id) || currentAssistant();
}

function convMeta(conv) {
  const a = assistantById(conv?.assistant);
  return { conv, a };
}

/* ------------------------------------------------------------------ */
/* 会话列表                                                            */
/* ------------------------------------------------------------------ */

function convMatchesQuery(c, q) {
  if (!q) return true;
  return c.title.toLowerCase().includes(q) || c.messages.some((m) => (m.text || '').toLowerCase().includes(q));
}

function visibleConversations() {
  const q = state.search.trim().toLowerCase();
  const showArchived = state.folder === ARCHIVED_GROUP;
  return conversations.filter((c) => {
    if (!convMatchesQuery(c, q)) return false;
    if (showArchived) return Boolean(c.archived);
    if (c.archived) return false;
    if (state.folder !== '全部' && c.folder !== state.folder) return false;
    return true;
  });
}

function groupedConversations() {
  const list = visibleConversations();
  if (state.folder === ARCHIVED_GROUP) {
    return list.length ? [{ label: ARCHIVED_GROUP, items: list, archived: true }] : [];
  }
  const groups = [];
  const pinned = list.filter((c) => c.pinned);
  if (pinned.length) groups.push({ label: '置顶', items: pinned });
  for (const g of ['本周', '上周', '更早']) {
    const items = list.filter((c) => !c.pinned && c.group === g);
    if (items.length) groups.push({ label: g, items });
  }
  if (state.folder === '全部') {
    const q = state.search.trim().toLowerCase();
    const archived = conversations.filter((c) => c.archived && convMatchesQuery(c, q));
    if (archived.length) groups.push({ label: ARCHIVED_GROUP, items: archived, archived: true });
  }
  return groups;
}

function convItemHtml(c) {
  const a = assistantById(c.assistant);
  const active = c.id === state.conversationId;
  const checked = selectedConvs.has(c.id);
  return `<button class="conv-item ${active ? 'active' : ''} ${c.archived ? 'archived' : ''} ${
    checked ? 'checked' : ''
  }" data-action="${selectionMode ? 'conv-select' : 'conv'}" data-conv="${esc(c.id)}"${
    selectionMode ? ` aria-pressed="${checked}"` : ''
  }>
    ${selectionMode ? `<span class="c-check">${icon(checked ? 'check_box' : 'check_box_outline_blank', { size: 18 })}</span>` : ''}
    <span class="c-main">
      <span class="c-title">${esc(c.title || '新消息')}</span>
      ${
        c.archived
          ? `<span class="c-tag">已归档${c.assistant !== state.assistantId ? ` · ${esc(a.name)}` : ''}</span>`
          : c.assistant !== state.assistantId
          ? `<span class="c-tag">${esc(a.name)}</span>`
          : ''
      }
    </span>
    ${c.archived ? `<span class="c-arch" aria-label="已归档">${icon('archive', { size: 13 })}</span>` : ''}
    ${c.pinned ? `<span class="pin">${icon('push_pin', { size: 13, fill: true })}</span>` : ''}
    ${active && state.streaming ? '<span class="run"></span>' : ''}
  </button>`;
}

function convListHtml() {
  const groups = groupedConversations();
  if (!groups.length) return '<div class="conv-empty">没有找到会话</div>';
  return groups
    .map(({ label, items, archived }, gi) => {
      const collapsed = collapsedGroups.has(label);
      return `<div class="conv-group ${archived ? 'is-archived' : ''} ${collapsed ? 'collapsed' : ''}">
        <button class="conv-group-btn" data-action="toggle-group" data-group="${esc(label)}" aria-expanded="${!collapsed}" aria-controls="conv-g-${gi}">
          <span class="cg-chev">${icon('expand_more', { size: 14 })}</span>
          ${label === '置顶' ? icon('push_pin', { size: 12 }) : archived ? icon('archive', { size: 12 }) : ''}
          <span class="cg-label">${esc(label)}</span>
          <span class="spacer"></span>
          <span class="tnum">${items.length}</span>
        </button>
      </div>
      <div class="conv-items" id="conv-g-${gi}" ${collapsed ? 'hidden' : ''}>${items.map(convItemHtml).join('')}</div>`;
    })
    .join('');
}

function selectionBarHtml() {
  if (!selectionMode) return '';
  const n = selectedConvs.size;
  return `<div class="sel-bar" id="sel-bar" role="toolbar" aria-label="批量操作">
    <div class="sel-top">
      <button class="icon-btn xs" data-action="exit-select" aria-label="退出多选">${icon('close', { size: 16 })}</button>
      <b>已选 ${n} 项</b>
      <span class="spacer"></span>
      <button class="sel-text" data-action="select-all">全选</button>
    </div>
    <div class="sel-actions">
      <button class="sel-act" data-action="batch-move">${icon('drive_file_move', { size: 15 })}<span>移动到</span></button>
      <button class="sel-act" data-action="batch-copy">${icon('content_copy', { size: 15 })}<span>复制到</span></button>
      <button class="sel-act" data-action="batch-archive">${icon('archive', { size: 15 })}<span>归档</span></button>
      <button class="sel-act" data-action="batch-retitle">${icon('edit_note', { size: 15 })}<span>重命名</span></button>
      <button class="sel-act danger" data-action="batch-delete">${icon('delete', { size: 15 })}<span>删除</span></button>
    </div>
  </div>`;
}

function refreshConvList() {
  const box = document.getElementById('conv-scroll');
  if (box) box.innerHTML = convListHtml();
  const bar = document.getElementById('sel-bar-host');
  if (bar) bar.innerHTML = selectionBarHtml();
  document.querySelector('.conv-panel')?.classList.toggle('selecting', selectionMode);
}

function convPanel() {
  const counts = conversations.filter((c) => !c.archived).length;
  return `<aside class="conv-panel ${selectionMode ? 'selecting' : ''}" aria-label="会话列表">
    <div class="conv-head">
      <h2>会话</h2>
      <button class="icon-btn sm" data-action="sort-conv" aria-label="排序方式">${icon('more_horiz', { size: 20 })}</button>
      <button class="icon-btn sm conv-close" data-action="close-list" aria-label="收起会话列表">${icon('close', { size: 20 })}</button>
    </div>
    <label class="conv-search">
      ${icon('search', { size: 18 })}
      <input id="conv-search" type="search" placeholder="搜索 ${counts} 个会话" autocomplete="off" aria-label="搜索会话">
    </label>
    <div class="folder-row" role="group" aria-label="文件夹筛选">
      ${[...folders, ARCHIVED_GROUP]
        .map(
          (f) =>
            `<md-filter-chip label="${esc(f)}" data-action="folder" data-folder="${esc(f)}" ${
              state.folder === f ? 'selected' : ''
            }></md-filter-chip>`
        )
        .join('')}
    </div>
    <div id="sel-bar-host">${selectionBarHtml()}</div>
    <div class="conv-scroll" id="conv-scroll">${convListHtml()}</div>
    <button class="icon-btn tonal" data-action="new-chat" aria-label="新建对话" style="align-self:flex-end;margin:2px 4px 0">${icon(
      'edit',
      { size: 20 }
    )}</button>
  </aside>`;
}

/* ------------------------------------------------------------------ */
/* 顶栏 + 消息                                                         */
/* ------------------------------------------------------------------ */

function topBar(conv) {
  const a = assistantById(conv?.assistant);
  return `<header class="chat-top glass">
    <button class="icon-btn" data-action="open-list" aria-label="打开会话列表">${icon('menu', { size: 22 })}</button>
    <button class="model-pill" data-action="pick-model" aria-label="切换模型">
      <span class="fp">${icon('folder', { size: 15 })}${esc(conv?.folder || '未归类')}</span>
      <span class="vrule"></span>
      <span style="min-width:0">
        <span class="mp-name">${esc(conv?.title || '新对话')}</span>
        <span class="mp-model">${esc(conv?.model || a.model)}</span>
      </span>
      ${icon('expand_more', { size: 16 })}
    </button>
    <div class="top-actions">
      <button class="icon-btn desktop-only" data-action="open-palette" aria-label="搜索与命令">${icon('search', { size: 21 })}</button>
      <button class="icon-btn" data-action="toggle-theme" aria-label="切换浅色或深色">${icon('contrast', { size: 21 })}</button>
      <button class="icon-btn" data-action="open-details" aria-label="详情" aria-pressed="${state.detailsOpen}">${icon('layers', {
        size: 21,
      })}</button>
      <button class="icon-btn" data-action="chat-more" aria-label="更多选项">${icon('more_vert', { size: 21 })}</button>
    </div>
  </header>`;
}

/* ------------------------------------------------------------------ */
/* 顶栏标签栏：Inline / Show labels / Fill / Full width / Elastic       */
/* ------------------------------------------------------------------ */

const CHAT_TABS = [{ label: '对话' }, { label: '任务' }, { label: '文件' }];

function chatTabsHtml() {
  return `<div class="chat-tabs" role="tablist" aria-label="对话分区">
    <span class="chat-tab-indicator" aria-hidden="true"></span>
    ${CHAT_TABS.map(
      (t, i) => `<button class="chat-tab ${state.chatTab === i ? 'active' : ''}" role="tab" id="ctab-${i}" aria-selected="${
        state.chatTab === i
      }" aria-controls="pane-host" tabindex="${state.chatTab === i ? 0 : -1}" data-action="chat-tab" data-tab="${i}">
        ${t.label}
      </button>`
    ).join('')}
  </div>`;
}

/* 整宽圆角线指示器：宽度=整格，Elastic 时在移动途中拉长再收回。 */
if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('resize', () => positionTabIndicator());
}

let tabPos = null;

function positionTabIndicator(animate = false) {
  const bar = document.querySelector('.chat-tabs');
  const ind = bar?.querySelector('.chat-tab-indicator');
  const active = bar?.querySelector('.chat-tab.active');
  if (!bar || !ind || !active) return;
  const b = bar.getBoundingClientRect();
  const r = active.getBoundingClientRect();
  if (!r.width) return;
  const to = { x: r.left - b.left, w: r.width };
  const from = tabPos;
  ind.getAnimations?.().forEach((a) => a.cancel());
  if (animate && from && (from.x !== to.x || from.w !== to.w) && typeof ind.animate === 'function') {
    const distance = Math.abs(to.x - from.x);
    /* 往右走时右缘先冲出去，往左走时左缘先冲出去，看起来才像被拉长。 */
    const lead = to.x >= from.x ? 0.38 : 0.62;
    const midX = from.x + (to.x - from.x) * lead;
    const midW = Math.min(b.width - Math.max(0, midX), Math.max(from.w, to.w) + distance * 0.55);
    ind.animate(
      [
        { transform: `translateX(${from.x}px)`, width: `${from.w}px` },
        { transform: `translateX(${midX}px)`, width: `${midW}px`, offset: 0.55 },
        { transform: `translateX(${to.x}px)`, width: `${to.w}px` },
      ],
      { duration: 420, easing: 'cubic-bezier(.2, 0, 0, 1)' }
    );
  }
  ind.style.width = `${to.w}px`;
  ind.style.transform = `translateX(${to.x}px)`;
  tabPos = to;
}

function archivedBannerHtml(conv) {
  return `<div class="archived-banner">
    <span class="ab-ico">${icon('archive', { size: 18 })}</span>
    <div class="ab-text"><b>此会话已归档</b><span>归档的会话不能继续对话。解除归档后会回到「${esc(
      conv.folder || '未归类'
    )}」。</span></div>
    <md-text-button data-action="unarchive-current">解除归档</md-text-button>
  </div>`;
}

function archivedComposerHtml() {
  return `<div class="composer-wrap"><div class="composer-inner">
    <div class="archived-lock">${icon('lock', { size: 20 })}
      <div class="al-text"><b>输入已锁定</b><span>解除归档后才能继续这个会话。</span></div>
      <md-filled-tonal-button data-action="unarchive-current">${icon('unarchive', {
        size: 16,
        slot: 'icon',
      })}解除归档</md-filled-tonal-button>
    </div>
  </div></div>`;
}

/* ------------------------------------------------------------------ */
/* 主区三个面板                                                        */
/* ------------------------------------------------------------------ */

function taskCardHtml(t) {
  const label = { done: '已完成', running: '进行中', wait: '等待批准' }[t.state] || t.state;
  const ico =
    t.state === 'done'
      ? icon('check', { size: 15 })
      : t.state === 'running'
      ? icon('progress_activity', { size: 15, cls: 'spin' })
      : icon('warning', { size: 15 });
  return `<div class="task-card ${t.state}">
    <span class="tc-ico">${ico}</span>
    <div class="tc-text"><b>${esc(t.title)}</b><small>${esc(t.who)} · ${esc(t.meta)}</small></div>
    <span class="tc-badge">${label}</span>
  </div>`;
}

function tasksPaneHtml() {
  const done = workspaceTasks.filter((t) => t.state === 'done').length;
  return `<div class="pane pane-scroll"><div class="pane-inner">
    <div class="pane-head">
      <div>
        <span class="eyebrow">${icon('task_alt', { size: 13 })} 本轮</span>
        <h2>任务</h2>
        <p>${done} / ${workspaceTasks.length} 完成 · 子代理并行 3 / 4</p>
      </div>
      <span class="chip">进行中</span>
    </div>
    <div class="task-board">${workspaceTasks.map(taskCardHtml).join('')}</div>
    <div class="card pane-card">
      <div class="pane-card-title">${icon('verified', { size: 16 })} 待批准的工具调用</div>
      <div class="side-row"><span class="k">workspace_shell</span><span class="v">pnpm build</span></div>
      <div class="side-row"><span class="k">风险</span><span class="v">写入 demo/m3/dist</span></div>
      <div class="approve">
        <md-filled-tonal-button data-action="approve-tool">${icon('check', {
          size: 15,
          slot: 'icon',
        })}批准</md-filled-tonal-button>
        <md-text-button data-action="deny-tool">拒绝</md-text-button>
      </div>
    </div>
    <div class="card pane-card">
      <div class="pane-card-title">${icon('hub', { size: 16 })} 子代理</div>
      <div class="side-row"><span class="k">研究员</span><span class="v">已完成 · 12.4s</span></div>
      <div class="side-row"><span class="k">构建</span><span class="v">已完成 · 8.1s</span></div>
      <div class="side-row"><span class="k">并行上限</span><span class="v">3 / 4</span></div>
    </div>
  </div></div>`;
}

const PANE_FILES = [
  ['demo/m3/index.html', '2.9 KB', '刚刚'],
  ['demo/m3/js/theme.js', '8.6 KB', '3 分钟前'],
  ['demo/m3/js/app.js', '14 KB', '3 分钟前'],
  ['demo/index.html', '103 KB', '昨天'],
  ['README.md', '4.8 KB', '10月29日'],
];

function filesPaneHtml() {
  return `<div class="pane pane-scroll"><div class="pane-inner">
    <div class="pane-head">
      <div>
        <span class="eyebrow">${icon('folder_open', { size: 13 })} 本轮</span>
        <h2>文件</h2>
        <p>${PANE_FILES.length} 个文件被读取或改写 · 只读预览</p>
      </div>
      <md-text-button data-action="open-workspace">${icon('open_in_new', {
        size: 16,
        slot: 'icon',
      })}打开工作区</md-text-button>
    </div>
    <div class="card pane-card tight">
      ${PANE_FILES.map(
        ([path, size, when]) => `<button class="pane-file" data-action="preview-file" data-path="${esc(path)}">
          <span class="pf-ico">${icon(path.endsWith('.js') ? 'code' : 'description', { size: 18 })}</span>
          <span class="pf-text"><b>${esc(path.split('/').pop())}</b><small>${esc(path)}</small></span>
          <span class="pf-meta">${esc(size)} · ${esc(when)}</span>
          ${icon('chevron_right', { size: 16 })}
        </button>`
      ).join('')}
    </div>
    <div class="card pane-card">
      <div class="pane-card-title">${icon('terminal', { size: 16 })} 工作区</div>
      <div class="side-row"><span class="k">目录</span><span class="v">demo/m3</span></div>
      <div class="side-row"><span class="k">服务</span><span class="v">127.0.0.1:8799</span></div>
      <div class="side-row"><span class="k">权限</span><span class="v">读 / 写 / Shell</span></div>
    </div>
  </div></div>`;
}

function paneHtml(conv) {
  if (state.chatTab === 1) return `<div class="pane-host" id="pane-host">${tasksPaneHtml()}</div>`;
  if (state.chatTab === 2) return `<div class="pane-host" id="pane-host">${filesPaneHtml()}</div>`;
  return `<div class="pane-host" id="pane-host"><div class="pane pane-chat">
    ${conv?.archived ? archivedBannerHtml(conv) : ''}
    <div class="messages" id="messages">${messagesHtml(conv)}</div>
    ${conv?.archived ? archivedComposerHtml() : composerHtml(conv)}
  </div></div>`;
}

function chatMainHtml(conv) {
  return `${topBar(conv)}${chatTabsHtml()}${paneHtml(conv)}`;
}

function reasoningHtml(r, open) {
  if (!r) return '';
  return `<div class="chain ${open ? 'open' : ''}">
    <button class="chain-head" data-action="toggle-chain" aria-expanded="${Boolean(open)}">
      <span class="chain-ico">${icon('lightbulb', { size: 16, fill: true })}</span>
      <span class="chain-title">${esc(r.title || '深度思考')}</span>
      <span class="chain-time">${esc(r.seconds || '')}s</span>
      ${icon('expand_more', { size: 18, cls: 'chev' })}
    </button>
    <div class="chain-body">${md(r.content)}<div class="chain-meta"><span class="chain-status ${r.done === false ? '' : 'done'}">${
      r.done === false ? '推理中…' : '推理完成'
    }</span><span class="dim">· 可展开查看原始步骤</span></div></div>
  </div>`;
}

function toolHtml(t) {
  const running = t.status === 'running';
  return `<div class="chain">
    <button class="chain-head" data-action="toggle-chain" aria-expanded="false">
      <span class="chain-ico">${icon(t.icon || 'build', { size: 16 })}</span>
      <span class="chain-title">${esc(t.name)}</span>
      <span class="chain-status ${running ? '' : 'done'}">${
        running ? `<span class="spin">${icon('progress_activity', { size: 12 })}</span>` : icon('check', { size: 12 })
      } ${esc(t.seconds)}s</span>
      ${icon('expand_more', { size: 18, cls: 'chev' })}
    </button>
    <div class="chain-body">
      <pre>${esc(t.input)}</pre>
      <pre>${esc(t.output)}</pre>
      <div class="chain-meta"><button class="icon-btn xs" data-action="open-tool" data-tool="${esc(t.id)}" aria-label="查看工具详情">${icon(
        'open_in_new',
        { size: 15 }
      )}</button><span class="dim">在工作区运行</span></div>
    </div>
  </div>`;
}

function subagentHtml(n) {
  if (!n) return '';
  return `<div class="notice">
    <button class="notice-head" data-action="toggle-notice" aria-expanded="false">
      <span class="chain-ico">${icon('account_tree', { size: 15 })}</span>
      <span class="notice-title">${esc(n.title)}</span>
      <span class="notice-hint">${esc(n.hint)}</span>
      ${icon('expand_more', { size: 17, cls: 'chev' })}
    </button>
    <div class="notice-body">${md(n.text)}</div>
  </div>`;
}

function actionsHtml(m) {
  return `<div class="msg-actions">
    <button class="icon-btn xs" data-action="copy-msg" data-msg="${esc(m.id)}" aria-label="复制">${icon('content_copy', { size: 16 })}</button>
    <button class="icon-btn xs" data-action="regenerate" data-msg="${esc(m.id)}" aria-label="重新生成">${icon('refresh', { size: 16 })}</button>
    <button class="icon-btn xs act-extra" data-action="translate-msg" data-msg="${esc(m.id)}" aria-label="翻译">${icon('translate', { size: 16 })}</button>
    <button class="icon-btn xs act-extra" data-action="speak-msg" data-msg="${esc(m.id)}" aria-label="朗读">${icon('volume_up', { size: 16 })}</button>
    <button class="icon-btn xs" data-action="msg-more" data-msg="${esc(m.id)}" aria-label="更多">${icon('more_vert', { size: 16 })}</button>
    ${
      m.branch
        ? `<span class="branch">
            <button class="icon-btn" data-action="branch-prev" aria-label="上一条分支">${icon('chevron_left', { size: 15 })}</button>
            <span class="branch-num">${branchIndex} / 2</span>
            <button class="icon-btn" data-action="branch-next" aria-label="下一条分支">${icon('chevron_right', { size: 15 })}</button>
          </span>`
        : ''
    }
    ${state.theme.showTime === false ? '' : `<span class="act-time">${esc(m.time || '')}</span>`}
  </div>`;
}

function nerdHtml(u) {
  if (!u) return '';
  return `<div class="nerd">
    ${u.steps ? `<span>${icon('route', { size: 12 })}${u.steps} 步</span>` : ''}
    ${u.cost ? `<span>${icon('token', { size: 12 })}${esc(u.cost)}</span>` : ''}
    <span>${icon('layers', { size: 12 })}${esc(u.context)}</span>
    ${u.speed ? `<span>${icon('speed', { size: 12 })}${esc(u.speed)}</span>` : ''}
    ${u.time ? `<span>${esc(u.time)}</span>` : ''}
  </div>`;
}

function attachmentsHtml(list = []) {
  if (!list.length) return '';
  return `<div class="attach-row">${list
    .map(
      (f) => `<span class="attach"><span class="a-ico">${icon(f.kind === 'image' ? 'image' : f.kind === 'code' ? 'code' : 'description', {
        size: 14,
      })}</span><span class="a-name">${esc(f.name)}</span><span class="a-meta">${esc(f.meta || '')}</span></span>`
    )
    .join('')}</div>`;
}

function messageHtml(m, conv, index) {
  if (m.role === 'user') {
    return `<div class="msg user" data-msg="${esc(m.id)}">
      <div class="msg-col">
        <div class="msg-meta"><span class="msg-time">${esc(m.time || '')}</span><span class="msg-name">${esc(user.name)}</span>${userAvatar(
      'xs'
    )}</div>
        <div class="bubble bubble-user">${attachmentsHtml(m.attachments)}${md(m.text)}</div>
      </div>
    </div>`;
  }
  const a = assistantById(conv?.assistant || m.assistant);
  const isLast = index === conv.messages.length - 1;
  const openReasoning = Boolean(m.openReasoning);
  return `<div class="msg assistant" data-msg="${esc(m.id)}">
    <div class="msg-col">
      <div class="msg-meta"><span class="msg-name">${esc(a.name)}</span><span class="msg-model">${esc(m.model || a.model)}</span><span>·</span><span>${esc(
    m.time || ''
  )}</span></div>
      ${reasoningHtml(m.reasoning, openReasoning)}
      ${(m.tools || []).map(toolHtml).join('')}
      <div class="answer" data-bubble="${esc(m.id)}">${
        m.text ? md(m.text) : `<span class="thinking"><i></i><i></i><i></i> 正在思考</span>`
      }${m.streaming ? '<span class="type-cursor"></span>' : ''}</div>
      ${subagentHtml(m.subagent)}
      ${actionsHtml(m)}
      ${nerdHtml(m.usage)}
    </div>
  </div>`;
}

function messagesHtml(conv) {
  if (!conv || !conv.messages.length) return emptyHtml();
  return `<div class="msg-stream">
    <div class="day-divider">${conv.id.startsWith('new-') ? '新对话' : '今天 · 演示数据'}</div>
    ${conv.messages.map((m, i) => messageHtml(m, conv, i)).join('')}
  </div>`;
}

function emptyHtml() {
  const a = currentAssistant();
  return `<div class="empty">
    <div class="empty-halo">${avatar(a, 'xxl')}</div>
    <h1>${esc(a.slogan)}</h1>
    <p>${esc(a.name)} · ${esc(a.model)}。工作区、搜索、子代理都已就绪，发一句话就能开始。</p>
    <div class="suggest-row">
      ${suggestions
        .map((s) => `<md-suggestion-chip label="${esc(s)}" data-action="suggest" data-text="${esc(s)}"></md-suggestion-chip>`)
        .join('')}
    </div>
  </div>`;
}

/* ------------------------------------------------------------------ */
/* 输入区                                                              */
/* ------------------------------------------------------------------ */

function composerHtml(conv) {
  const a = assistantById(conv?.assistant);
  return `<div class="composer-wrap">
    <div class="composer-inner">
      ${state.streaming ? `<div class="queue-pill">${icon('hourglass_top', { size: 13 })}正在生成 · 点击停止可以保留已写内容</div>` : ''}
      <div class="context-line">
        <span class="ctx-num">上下文 42%</span>
        <span class="meter" aria-hidden="true"><i class="m1" style="width:28%"></i><i class="m2" style="width:14%"></i><i class="m3" style="width:8%"></i></span>
        <span class="ctx-num">84.2k / 200k</span>
      </div>
      <div class="composer" id="composer">
        <div class="attach-row" id="composer-attach">${pendingFiles
          .map(
            (f, i) => `<span class="attach"><span class="a-ico">${icon('description', { size: 14 })}</span><span class="a-name">${esc(
              f.name
            )}</span><span class="a-meta">${esc(f.meta)}</span><button class="icon-btn xs" data-action="remove-file" data-i="${i}" aria-label="移除附件">${icon(
              'close',
              { size: 13 }
            )}</button></span>`
          )
          .join('')}</div>
        <textarea id="composer-input" rows="1" placeholder="给 ${esc(a.name)} 发消息…" aria-label="消息输入"></textarea>
        <div class="composer-tools">
          <button class="icon-btn sm" data-action="attach" aria-label="添加附件">${icon('attach_file', { size: 19 })}</button>
          <button class="icon-btn sm" data-action="composer-tools" aria-label="工具">${icon('build', { size: 18 })}</button>
          <button class="model-chip" data-action="pick-model">${icon('auto_awesome', { size: 14 })}${esc(conv?.model || a.model)}
            ${icon('expand_more', { size: 13 })}</button>
          <span class="spacer"></span>
          <button class="icon-btn sm" data-action="voice" aria-label="语音输入">${icon('mic', { size: 19 })}</button>
          <button class="send ${state.streaming ? 'stop' : ''}" data-action="${state.streaming ? 'stop' : 'send'}" aria-label="${
    state.streaming ? '停止生成' : '发送'
  }">${icon(state.streaming ? 'stop_circle' : 'send', { size: 20 })}</button>
        </div>
      </div>
      <div class="hint-line">Enter 发送 · Shift + Enter 换行 · 内容会后端保存</div>
    </div>
  </div>`;
}

/* ------------------------------------------------------------------ */
/* 详情栏                                                              */
/* ------------------------------------------------------------------ */

function sideBodyHtml() {
  const a = currentAssistant();
  return `<div class="side-card">
      <div class="side-assistant">
        ${avatar(a, 'md')}
        <div style="min-width:0">
          <div class="sa-name">${esc(a.name)}</div>
          <div class="sa-sub">${esc(a.provider)} · ${esc(a.model)}</div>
        </div>
        <span class="spacer"></span>
        <button class="icon-btn sm" data-action="open-assistant" aria-label="切换助手">${icon('swap_horiz', { size: 18 })}</button>
      </div>
      <div class="chip-row" style="margin-top:10px">${a.tools
        .map((t) => `<span class="chip">${esc(t)}</span>`)
        .join('')}</div>
    </div>
    <div class="side-card">
      <div class="side-card-title">${icon('monitoring', { size: 14 })}供应商配额</div>
      <div class="side-row"><span class="k">Anthropic</span><span class="v">¥86 / ¥120</span></div>
      <div class="quota-line"><span>本月已用 72%</span><span>3 天后重置</span></div>
      <div class="meter"><i class="m1" style="width:72%"></i></div>
    </div>
    <div class="side-card">
      <div class="side-card-title">${icon('layers', { size: 14 })}上下文</div>
      <div class="quota-line"><b>84.2k</b><span>/ 200k · 42%</span></div>
      <div class="meter"><i class="m1" style="width:28%"></i><i class="m2" style="width:14%"></i><i class="m3" style="width:8%"></i></div>
      <div class="chip-row" style="margin-top:10px"><span class="chip">缓存 56.2k</span><span class="chip">输入 19.8k</span><span class="chip">输出 8.2k</span></div>
    </div>
    <div class="side-card">
      <div class="side-card-title">${icon('shield', { size: 14 })}本轮工具权限</div>
      <div class="perm-row"><div class="perm-text">允许写入工作区<small>workspace_edit_file</small></div><md-switch selected data-switch="write"></md-switch></div>
      <div class="perm-row"><div class="perm-text">自动批准读取<small>workspace_read_file</small></div><md-switch selected data-switch="read"></md-switch></div>
    </div>`;
}

function sidePanel() {
  return `<aside class="side-panel" aria-label="对话详情">
    <div class="side-head"><h2>详情</h2><span class="chip">本轮</span><button class="icon-btn sm" data-action="close-details" aria-label="收起详情">${icon(
    'close',
    { size: 19 }
  )}</button></div>
    <div class="side-body" id="side-body">${sideBodyHtml()}</div>
  </aside>`;
}

/* ------------------------------------------------------------------ */
/* 渲染 / 挂载                                                         */
/* ------------------------------------------------------------------ */

export function renderChat() {
  const conv = currentConversation();
  if (conv?.assistant) state.assistantId = conv.assistant;
  return `<div class="chat ${state.listOpen ? 'list-open' : ''} ${state.detailsOpen ? 'details-open' : ''}" id="chat">
    ${convPanel()}
    <section class="chat-main">${chatMainHtml(conv)}</section>
    ${sidePanel()}
    <div class="panel-scrim list" data-action="close-list"></div>
    <div class="panel-scrim details" data-action="close-details"></div>
  </div>`;
}

function mountChatMain() {
  const ta = document.getElementById('composer-input');
  if (ta) {
    autoGrow(ta);
    updateSendEnabled(ta.value.trim().length > 0);
    ta.addEventListener('input', () => {
      autoGrow(ta);
      updateSendEnabled(ta.value.trim().length > 0);
    });
    ta.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        submitComposer();
      }
    });
  }

  tabPos = null;
  positionTabIndicator();
  requestAnimationFrame(() => positionTabIndicator());

  const tabs = document.querySelector('.chat-tabs');
  tabs?.addEventListener('keydown', (e) => {
    const cur = Number(document.querySelector('.chat-tab.active')?.dataset.tab || 0);
    let next = null;
    if (e.key === 'ArrowRight') next = (cur + 1) % CHAT_TABS.length;
    if (e.key === 'ArrowLeft') next = (cur + CHAT_TABS.length - 1) % CHAT_TABS.length;
    if (e.key === 'Home') next = 0;
    if (e.key === 'End') next = CHAT_TABS.length - 1;
    if (next === null) return;
    e.preventDefault();
    switchChatTab(next);
    document.getElementById(`ctab-${next}`)?.focus();
  });

  const messages = document.getElementById('messages');
  if (messages) requestAnimationFrame(() => scrollToBottom(messages));
}

function refreshChatMain() {
  const main = document.querySelector('.chat-main');
  if (!main) return;
  main.innerHTML = chatMainHtml(currentConversation());
  mountChatMain();
}

/* 同轴 Y 转场：有 View Transitions 就交给它，没有就即时替换。 */
function switchChatTab(index) {
  if (index === state.chatTab) return;
  state.chatTab = index;
  document.querySelectorAll('.chat-tab').forEach((t, i) => {
    const on = i === index;
    t.classList.toggle('active', on);
    t.setAttribute('aria-selected', String(on));
    t.tabIndex = on ? 0 : -1;
  });
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
  const motionOn = document.documentElement.dataset.motion !== 'off' && !reduce;
  positionTabIndicator(motionOn);

  const conv = currentConversation();
  const swap = () => {
    const host = document.getElementById('pane-host');
    if (!host) return;
    host.outerHTML = paneHtml(conv);
    requestAnimationFrame(() => {
      positionTabIndicator();
      const box = document.getElementById('messages');
      if (box) scrollToBottom(box);
    });
  };
  if (motionOn && typeof document.startViewTransition === 'function') {
    try {
      document.startViewTransition(swap);
      return;
    } catch {
      /* 过渡被占用时退回普通切换 */
    }
  }
  swap();
  if (motionOn) document.querySelector('.pane')?.classList.add('pane-enter');
}

export function mountChat() {
  mountChatMain();

  const input = document.getElementById('conv-search');
  if (input) {
    input.value = state.search;
    input.addEventListener('input', (e) => {
      state.search = e.target.value;
      refreshConvList();
    });
  }

  /* 长按在 WebView 里不可靠，用双击 / 双指点开菜单。 */
  const scroll = document.getElementById('conv-scroll');
  let lastTap = { id: '', t: 0 };
  scroll?.addEventListener('pointerup', (e) => {
    if (e.pointerType === 'mouse' || selectionMode) return;
    const item = e.target.closest?.('.conv-item');
    if (!item) return;
    const now = Date.now();
    if (lastTap.id === item.dataset.conv && now - lastTap.t < 380) {
      lastTap = { id: '', t: 0 };
      /* 等这一下 click 走完再开菜单，否则 popover 会把它当成外部点击。 */
      window.setTimeout(() => openConvMenu(item), 60);
    } else {
      lastTap = { id: item.dataset.conv, t: now };
    }
  });
  scroll?.addEventListener('dblclick', (e) => {
    if (selectionMode) return;
    const item = e.target.closest?.('.conv-item');
    if (item) openConvMenu(item);
  });

  const fileInput = document.createElement('input');
  fileInput.type = 'file';
  fileInput.hidden = true;
  fileInput.id = 'file-input';
  fileInput.addEventListener('change', () => {
    const f = fileInput.files?.[0];
    if (!f) return;
    pendingFiles.push({ name: f.name, meta: `${Math.max(1, Math.round(f.size / 1024))} KB` });
    renderPendingFiles();
    fileInput.value = '';
    toast(`已添加附件 ${f.name}`);
  });
  document.getElementById('view').append(fileInput);

  runtime.renderMessages = renderMessagesOnly;
  runtime.focusComposer = focusComposer;
  runtime.stopStreaming = () => stopStreaming(false);
}

function renderPendingFiles() {
  const wrap = document.getElementById('composer-attach');
  if (!wrap) return;
  wrap.innerHTML = pendingFiles
    .map(
      (f, i) => `<span class="attach"><span class="a-ico">${icon('description', { size: 14 })}</span><span class="a-name">${esc(
        f.name
      )}</span><span class="a-meta">${esc(f.meta)}</span><button class="icon-btn xs" data-action="remove-file" data-i="${i}" aria-label="移除附件">${icon(
        'close',
        { size: 13 }
      )}</button></span>`
    )
    .join('');
}

function renderMessagesOnly() {
  const conv = currentConversation();
  const box = document.getElementById('messages');
  if (!box) return;
  box.classList.add('no-anim');
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 120;
  box.innerHTML = messagesHtml(conv);
  if (nearBottom) scrollToBottom(box);
  updateSendEnabled(Boolean(document.getElementById('composer-input')?.value.trim()));
}

function updateSendEnabled(enabled) {
  const btn = document.querySelector('.send');
  if (!btn || state.streaming) return;
  btn.disabled = !enabled;
}

function updateComposerStreaming() {
  const btn = document.querySelector('.send');
  if (!btn) return;
  btn.classList.toggle('stop', state.streaming);
  btn.dataset.action = state.streaming ? 'stop' : 'send';
  btn.innerHTML = icon(state.streaming ? 'stop_circle' : 'send', { size: 20 });
  btn.setAttribute('aria-label', state.streaming ? '停止生成' : '发送');
  if (!state.streaming) updateSendEnabled(Boolean(document.getElementById('composer-input')?.value.trim()));
  const pill = document.querySelector('.composer-wrap .queue-pill');
  if (state.streaming && !pill) {
    document.querySelector('.composer-inner')?.insertAdjacentHTML(
      'afterbegin',
      `<div class="queue-pill">${icon('hourglass_top', { size: 13 })}正在生成 · 点击停止可以保留已写内容</div>`
    );
  } else if (!state.streaming && pill) {
    pill.remove();
  }
}

function focusComposer() {
  const ta = document.getElementById('composer-input');
  ta?.focus();
}

function autoGrow(el) {
  el.style.height = 'auto';
  el.style.height = `${Math.min(168, Math.max(26, el.scrollHeight))}px`;
}

function submitComposer() {
  const ta = document.getElementById('composer-input');
  if (!ta) return;
  const text = ta.value.trim();
  if (!text || state.streaming) return;
  ta.value = '';
  autoGrow(ta);
  sendMessage(text);
}

/* ------------------------------------------------------------------ */
/* 流式回复                                                            */
/* ------------------------------------------------------------------ */

function replyFor(prompt) {
  const p = prompt.toLowerCase();
  if (p.includes('构建') || p.includes('build') || p.includes('工作区')) {
    return '可以。工作区已经挂到 `demo/m3`，我先跑一轮静态检查：\n\n1. 用 `node --check` 过一遍所有 ES module；\n2. 校验 import map 里每个前缀都能落到 vendor 文件；\n3. 再起一个 `python3 -m http.server 8765` 给你在浏览器里点。\n\n构建命令只写进任务队列，不会在 Termux 里直接执行编译——这条我们之前说好了。';
  }
  if (p.includes('发布') || p.includes('release')) {
    return '我按「用户能感知的变化」来写，不罗列 commit：\n\n- 全新的 M3 Web 预览，支持三档布局与实时主题；\n- 对话、助手、模型、工作区、设置五个路由都能点；\n- 窄屏会把会话列表和详情收成浮层，不再挤压正文。\n\n版本号仍然跟 `versionCode` 一起走，这一版是演示，不动 Android 的版本。';
  }
  if (p.includes('颜色') || p.includes('主题') || p.includes('配色')) {
    return '配色全部由种子色生成，界面里没有手写十六进制。\n\n换一套调色板时，48 个 color role 会一起重算，`@material/web` 的组件也会跟着变——它们读的是同一批 `--md-sys-color-*`。\n\n现在这套是 **星紫 + 色调**，你可以在设置里换成鲜活、表现或单色。';
  }
  if (p.includes('三栏') || p.includes('布局') || p.includes('收紧')) {
    return '三栏的宽度我已经收过一轮：\n\n- 会话列表 296，正文自适应，详情 344；\n- 低于 1500 时详情变浮层，低于 1180 时会话列表也收起来；\n- 900 以下顶栏只留必要的按钮，正文左右各 12px。\n\n如果你在真机上还觉得挤，我可以把会话列表再压到 272。';
  }
  return '收到。我会先把这件事拆成能验证的几步：读一遍相关文件、确认现有约束，再动手改。\n\n这一轮我会保留 `demo/` 下的旧原型不动，新东西全部写在 `demo/m3/`；需要动到构建或版本号的地方会先问你。\n\n你可以继续补一句期望的效果，我就按那个方向往下做。';
}

/* 首条消息给新会话起个名字，同步列表、最近列表和顶栏。 */
function nameFromPrompt(text) {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return '';
  const head = clean.split(/[，。！？!?,.;；、\n]/)[0].trim() || clean;
  return head.length > 16 ? `${head.slice(0, 16)}…` : head;
}

function renameConversation(conv, text) {
  const title = nameFromPrompt(text);
  if (!title) return;
  conv.title = title;
  for (const el of document.querySelectorAll('.conv-item')) {
    if (el.dataset.conv === conv.id) {
      const t = el.querySelector('.c-title');
      if (t) t.textContent = conv.title;
    }
  }
  for (const el of document.querySelectorAll('.recent-item')) {
    if (el.dataset.conv === conv.id) {
      const t = el.querySelector('span');
      if (t) t.textContent = conv.title;
    }
  }
  const mp = document.querySelector('.chat-top .mp-name');
  if (mp) mp.textContent = conv.title;
}

export async function sendMessage(text) {
  const conv = currentConversation();
  if (!conv) return;
  if (conv.archived) {
    toast('会话已归档 · 解除归档后才能继续');
    return;
  }
  const a = currentAssistant();
  if (!conv.messages.length && (!conv.title || conv.title === '新对话')) renameConversation(conv, text);
  lastPrompt = text;
  conv.messages.push({
    id: uid(),
    role: 'user',
    time: nowTime(),
    text,
    attachments: pendingFiles.map((f) => ({ ...f, kind: 'file' })),
  });
  pendingFiles = [];
  renderPendingFiles();
  state.streaming = true;
  state.listOpen = false;
  document.getElementById('chat')?.classList.remove('list-open');
  renderMessagesOnly();
  updateComposerStreaming();
  scrollToBottom(document.getElementById('messages'), true);

  const mine = ++streamToken;
  const reply = {
    id: uid(),
    role: 'assistant',
    assistant: a.id,
    model: conv.model || a.model,
    time: nowTime(),
    text: '',
    streaming: true,
    reasoning: {
      title: '深度思考 · 先看约束，再给方案',
      seconds: '1.2',
      done: false,
      content: '这条请求涉及工作区与界面两端。先确认不会碰构建流程，再决定改哪一层；能只改 CSS 的不动 JS。',
    },
    usage: { context: '86.4k / 200k', cost: '¥0.009', speed: '40 tok/s', steps: 2, time: '3.8s' },
  };
  conv.messages.push(reply);
  renderMessagesOnly();
  scrollToBottom(document.getElementById('messages'), true);

  await wait(850);
  if (mine !== streamToken) return;
  reply.reasoning.done = true;
  renderMessagesOnly();

  await wait(420);
  if (mine !== streamToken) return;
  const full = replyFor(text);
  let i = 0;
  while (i < full.length) {
    if (mine !== streamToken) return;
    i += 2 + Math.floor(Math.random() * 3);
    reply.text = full.slice(0, i);
    const bubble = document.querySelector(`[data-bubble="${reply.id}"]`);
    if (bubble) {
      bubble.innerHTML = `${md(reply.text)}<span class="type-cursor"></span>`;
      const box = document.getElementById('messages');
      if (box.scrollHeight - box.scrollTop - box.clientHeight < 160) scrollToBottom(box);
    }
    await wait(18);
  }
  reply.text = full;
  reply.streaming = false;
  state.streaming = false;
  renderMessagesOnly();
  updateComposerStreaming();
  toast('回复完成 · 可继续追问');
}

export function stopStreaming(quiet = false) {
  if (!state.streaming) return;
  streamToken++;
  const conv = currentConversation();
  const last = conv?.messages[conv.messages.length - 1];
  if (last?.role === 'assistant' && last.streaming) {
    last.streaming = false;
    if (!last.text) last.text = '（已停止）';
  }
  state.streaming = false;
  renderMessagesOnly();
  updateComposerStreaming();
  if (!quiet) toast('已停止生成');
}

/* ------------------------------------------------------------------ */
/* 交互                                                                */
/* ------------------------------------------------------------------ */

function findMessage(id) {
  return currentConversation()?.messages.find((m) => m.id === id);
}

function openToolDialog(toolId) {
  const conv = currentConversation();
  let tool = null;
  for (const m of conv?.messages || []) {
    tool = (m.tools || []).find((t) => t.id === toolId);
    if (tool) break;
  }
  if (!tool) return;
  openDialog({
    headline: '工具调用',
    iconName: 'build',
    content: `<p class="dialog-lead">${esc(tool.name)} · ${esc(tool.seconds)}s · 由 ${esc(
      currentAssistant().name
    )} 发起</p>
      <div class="dialog-section"><h4>入参</h4><div class="dialog-code">${esc(tool.input)}</div></div>
      <div class="dialog-section"><h4>输出</h4><div class="dialog-code">${esc(tool.output)}</div></div>`,
    actions: `<md-text-button data-action="close-dialog">关闭</md-text-button><md-filled-tonal-button data-action="open-workspace">到工作区查看</md-filled-tonal-button>`,
  });
}

function openModelMenu(anchor) {
  const conv = currentConversation();
  showMenu(
    anchor,
    modelList.slice(0, 8).map((m) => ({
      id: m.id,
      label: m.name,
      icon: 'auto_awesome',
      hint: m.context,
      selected: (conv?.model || currentAssistant().model) === m.name,
    })),
    (id) => {
      const m = modelList.find((x) => x.id === id);
      if (!m || !conv) return;
      conv.model = m.name;
      const el = document.querySelector('.model-pill .mp-model');
      if (el) el.textContent = m.name;
      document.querySelectorAll('.composer .model-chip').forEach((n) => {
        n.innerHTML = `${icon('auto_awesome', { size: 14 })}${esc(m.name)} ${icon('expand_more', { size: 13 })}`;
      });
      toast(`模型已切换到 ${m.name}`);
    }
  );
}

function openChatMore(anchor) {
  showMenu(anchor, [
    { id: 'map', label: '对话地图', icon: 'account_tree' },
    { id: 'export', label: '导出为 Markdown', icon: 'download' },
    { id: 'fork', label: '创建分支', icon: 'call_split' },
    { divider: true, id: 'd1' },
    { id: 'delete', label: '删除对话', icon: 'delete', danger: true },
  ], (id) => {
    if (id === 'export') {
      const conv = currentConversation();
      const body = conv?.messages.map((m) => `${m.role === 'user' ? '你' : '助手'}: ${m.text}`).join('\n\n') || '';
      copyText(body).then((ok) => toast(ok ? '对话已复制为 Markdown' : '复制失败'));
    } else if (id === 'delete') {
      const c = currentConversation();
      if (c) confirmDelete([c.id]);
    } else if (id === 'fork') {
      toast('已创建分支（演示）');
    } else if (id === 'map') {
      toast('对话地图：3 个分支 · 12 步工具调用');
    }
  });
}

function openComposerTools(anchor) {
  showMenu(anchor, [
    { id: 'search', label: '联网搜索', icon: 'travel_explore' },
    { id: 'workspace', label: '工作区', icon: 'terminal' },
    { id: 'memory', label: '记忆库', icon: 'psychology' },
    { id: 'subagent', label: '派发子代理', icon: 'account_tree' },
  ], (id) => {
    const names = { search: '联网搜索', workspace: '工作区', memory: '记忆库', subagent: '子代理' };
    toast(`本轮已启用 ${names[id]}`);
  });
}

function openSortMenu(anchor) {
  showMenu(anchor, [
    { id: 'update', label: '按更新时间', icon: 'schedule', selected: true },
    { id: 'create', label: '按创建时间', icon: 'history' },
  ], (id) => toast(id === 'update' ? '已按更新时间排序' : '已按创建时间排序'));
}


/* ------------------------------------------------------------------ */
/* 会话操作：菜单 / 多选 / 移动复制 / 归档 / 删除                        */
/* ------------------------------------------------------------------ */

function convById(id) {
  return conversations.find((c) => c.id === id) || null;
}

function enterSelection(id) {
  selectionMode = true;
  selectedConvs.clear();
  if (id) selectedConvs.add(id);
  refreshConvList();
}

function exitSelection() {
  selectionMode = false;
  selectedConvs.clear();
  refreshConvList();
}

function toggleConvSelection(id) {
  if (selectedConvs.has(id)) selectedConvs.delete(id);
  else selectedConvs.add(id);
  refreshConvList();
}

function selectAllVisible() {
  const items = visibleConversations();
  const all = items.length > 0 && items.every((c) => selectedConvs.has(c.id));
  selectedConvs.clear();
  if (!all) items.forEach((c) => selectedConvs.add(c.id));
  refreshConvList();
}

function currentSelection() {
  const ids = [...selectedConvs].filter((id) => convById(id));
  if (!ids.length) toast('先选会话');
  return ids;
}

function openConvMenu(anchor) {
  const conv = convById(anchor?.dataset?.conv);
  if (!conv) return;
  showMenu(
    anchor,
    [
      { id: 'select', label: '多选', icon: 'check_box' },
      { id: 'move', label: '移动到…', icon: 'drive_file_move' },
      { id: 'copy', label: '复制到…', icon: 'content_copy' },
      { id: 'retitle', label: '重新生成标题', icon: 'edit_note' },
      { divider: true, id: 'd1' },
      { id: 'archive', label: conv.archived ? '解除归档' : '归档', icon: conv.archived ? 'unarchive' : 'archive' },
      { id: 'delete', label: '删除', icon: 'delete', danger: true },
    ],
    (action) => {
      if (action === 'select') enterSelection(conv.id);
      else if (action === 'move' || action === 'copy') openTransferSheet(action, [conv.id]);
      else if (action === 'retitle') retitleConversations([conv.id]);
      else if (action === 'archive') setArchived([conv.id], !conv.archived);
      else if (action === 'delete') confirmDelete([conv.id]);
    }
  );
}

function retitleConversations(ids) {
  let n = 0;
  for (const id of ids) {
    const c = convById(id);
    if (!c) continue;
    const first = c.messages.find((m) => m.role === 'user');
    const title = first ? nameFromPrompt(first.text) : '';
    if (title) {
      c.title = title;
      n++;
    }
  }
  if (!n) {
    toast('这些会话还没有能命名的消息');
    return;
  }
  toast(`已重新生成 ${n} 个标题`);
  refreshConvList();
  refreshChatMain();
  document.querySelectorAll('.recent-item').forEach((el) => {
    const item = convById(el.dataset.conv);
    const label = el.querySelector('span');
    if (item && label) label.textContent = item.title;
  });
}

function setArchived(ids, value) {
  let n = 0;
  for (const id of ids) {
    const c = convById(id);
    if (!c || Boolean(c.archived) === value) continue;
    c.archived = value;
    c.updated = '刚刚';
    n++;
  }
  if (value) collapsedGroups.add(ARCHIVED_GROUP);
  if (!value && state.folder === ARCHIVED_GROUP) state.folder = '全部';
  saveCollapsed();
  exitSelection();
  runtime.render();
  toast(value ? `已归档 ${n} 个会话 · 在「${ARCHIVED_GROUP}」分组里` : `已解除归档 ${n} 个会话`);
}

function confirmDelete(ids) {
  if (!ids.length) return;
  pendingDelete = ids;
  openDialog({
    headline: ids.length > 1 ? `删除 ${ids.length} 个会话` : '删除会话',
    iconName: 'delete',
    content: `<p class="dialog-lead">删除后不能恢复，会话里的消息会一起消失。</p>
      <div class="dialog-list">${ids
        .slice(0, 5)
        .map((id) => `<div class="dl-row">${icon('chat_bubble', { size: 15 })}<span>${esc(convById(id)?.title || '未命名')}</span></div>`)
        .join('')}${ids.length > 5 ? `<div class="dl-more">还有 ${ids.length - 5} 个</div>` : ''}</div>`,
    actions: `<md-text-button data-action="close-dialog">取消</md-text-button><md-filled-button data-action="confirm-delete">删除</md-filled-button>`,
  });
}

/* ---------------------------- Bottom Sheet ---------------------------- */

function openTransferSheet(mode, ids) {
  if (!ids.length) return;
  closeTransferSheet(true);
  sheetMode = mode;
  sheetIds = ids;
  sheetAssistant = currentAssistant().id;
  sheetFolder = '';
  sheetKeepFolder = true;
  sheetFoldersOpen = true;
  const layer = document.createElement('div');
  layer.className = 'sheet-layer';
  layer.innerHTML = `<div class="sheet-scrim" data-action="sheet-close"></div><section class="bottom-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title">${sheetHtml()}</section>`;
  document.getElementById('overlay-root').append(layer);
  sheetEl = layer;
  document.body.classList.add('sheet-open');
  sheetKeyHandler = (e) => {
    if (e.key === 'Escape') closeTransferSheet();
  };
  document.addEventListener('keydown', sheetKeyHandler);
  requestAnimationFrame(() => layer.classList.add('open'));
}

function refreshSheet() {
  const panel = sheetEl?.querySelector('.bottom-sheet');
  if (panel) panel.innerHTML = sheetHtml();
}

function closeTransferSheet(instant = false) {
  const layer = sheetEl;
  if (!layer) return;
  sheetEl = null;
  document.body.classList.remove('sheet-open');
  if (sheetKeyHandler) {
    document.removeEventListener('keydown', sheetKeyHandler);
    sheetKeyHandler = null;
  }
  if (instant) {
    layer.remove();
    return;
  }
  layer.classList.remove('open');
  window.setTimeout(() => layer.remove(), 220);
}

function sheetHtml() {
  const modeLabel = sheetMode === 'move' ? '移动到' : '复制到';
  const a = assistants.find((x) => x.id === sheetAssistant) || assistants[0];
  return `<div class="sheet-grab" aria-hidden="true"></div>
    <header class="sheet-head">
      <div><span class="eyebrow">${sheetIds.length} 个会话</span><h2 id="sheet-title">${modeLabel}</h2></div>
      <button class="icon-btn" data-action="sheet-close" aria-label="关闭">${icon('close', { size: 20 })}</button>
    </header>
    <div class="sheet-body">
      <h4 class="sheet-label">目标助手 <span>单选必选</span></h4>
      <div class="sheet-chips" role="radiogroup" aria-label="目标助手">
        ${assistants
          .map(
            (x) => `<md-filter-chip label="${esc(x.name)}" data-action="sheet-assistant" data-id="${esc(x.id)}" ${
              x.id === sheetAssistant ? 'selected' : ''
            }></md-filter-chip>`
          )
          .join('')}
      </div>
      <p class="sheet-hint">${esc(a.name)} · ${esc(a.model)} · 记忆跟着助手走</p>
      <button class="sheet-expander" data-action="sheet-folders" aria-expanded="${sheetFoldersOpen}">
        ${icon('folder', { size: 18 })}<span>目标文件夹</span><span class="spacer"></span>
        <span class="se-value">${sheetKeepFolder ? '保留原文件夹' : esc(sheetFolder)}</span>
        ${icon('expand_more', { size: 18, cls: 'chev' })}
      </button>
      <div class="sheet-folder-list" ${sheetFoldersOpen ? '' : 'hidden'}>
        <button class="sheet-folder ${sheetKeepFolder ? 'active' : ''}" data-action="sheet-folder" data-folder="">
          <span class="sf-radio">${icon(sheetKeepFolder ? 'check_circle' : 'radio_button_unchecked', { size: 18 })}</span>
          <span class="sf-text">保留原文件夹<small>每个会话留在原来的分类</small></span>
        </button>
        ${folders
          .filter((f) => f !== '全部' && f !== ARCHIVED_GROUP)
          .map(
            (f) => `<button class="sheet-folder ${!sheetKeepFolder && sheetFolder === f ? 'active' : ''}" data-action="sheet-folder" data-folder="${esc(
              f
            )}">
            <span class="sf-radio">${icon(
              !sheetKeepFolder && sheetFolder === f ? 'check_circle' : 'radio_button_unchecked',
              { size: 18 }
            )}</span>
            <span class="sf-text">${esc(f)}<small>${conversations.filter((c) => c.folder === f && !c.archived).length} 个会话</small></span>
          </button>`
          )
          .join('')}
      </div>
    </div>
    <footer class="sheet-foot">
      <md-text-button data-action="sheet-close">取消</md-text-button>
      <md-filled-button data-action="sheet-confirm">${modeLabel} ${sheetIds.length} 个</md-filled-button>
    </footer>`;
}

function applyTransfer() {
  const target = assistants.find((a) => a.id === sheetAssistant) || assistants[0];
  const ids = [...sheetIds];
  const folder = sheetKeepFolder ? null : sheetFolder;
  let n = 0;
  if (sheetMode === 'move') {
    for (const id of ids) {
      const c = convById(id);
      if (!c) continue;
      c.assistant = target.id;
      if (folder) c.folder = folder;
      c.updated = '刚刚';
      n++;
    }
    toast(`已移动 ${n} 个会话到 ${target.name}${folder ? ' · ' + folder : ''}`);
  } else {
    const copies = [];
    let i = 0;
    for (const id of ids) {
      const c = convById(id);
      if (!c) continue;
      const copy = JSON.parse(JSON.stringify(c));
      copy.id = `copy-${Date.now().toString(36)}-${i++}`;
      copy.title = `${c.title} · 副本`;
      copy.assistant = target.id;
      copy.pinned = false;
      copy.archived = false;
      copy.group = '本周';
      copy.updated = '刚刚';
      if (folder) copy.folder = folder;
      copies.push(copy);
      n++;
    }
    conversations.unshift(...copies);
    toast(`已复制 ${n} 个会话到 ${target.name}${folder ? ' · ' + folder : ''}`);
  }
  closeTransferSheet();
  exitSelection();
  runtime.render();
}

function deletePendingConversations() {
  const ids = pendingDelete;
  pendingDelete = [];
  if (!ids.length) return 0;
  const set = new Set(ids);
  for (let i = conversations.length - 1; i >= 0; i--) {
    if (set.has(conversations[i].id)) conversations.splice(i, 1);
  }
  if (set.has(state.conversationId)) {
    const next = conversations.find((c) => !c.archived) || conversations[0];
    if (next) selectConversation(next.id);
    else newConversation();
  }
  return ids.length;
}

function openFilePreview(path) {
  const lines = filePreviews[path];
  openDialog({
    headline: path.split('/').pop() || '文件预览',
    iconName: 'description',
    content: `<p class="dialog-lead">${esc(path)} · 只读预览</p>${
      lines ? `<div class="dialog-code">${esc(lines.join('\\n'))}</div>` : '<p class="dialog-lead">演示数据里没有这个文件的内容。</p>'
    }`,
    actions: `<md-text-button data-action="close-dialog">关闭</md-text-button><md-filled-tonal-button data-action="open-workspace">打开工作区</md-filled-tonal-button>`,
  });
}

export function chatAction(action, el, event) {
  switch (action) {
    case 'conv': {
      if (event?.detail > 1) return true;
      if (state.streaming) stopStreaming(true);
      pendingFiles = [];
      if (!selectConversation(el.dataset.conv)) return true;
      document.querySelectorAll('.conv-item').forEach((n) => n.classList.toggle('active', n.dataset.conv === el.dataset.conv));
      document.querySelectorAll('.recent-item').forEach((n) => n.classList.toggle('active', n.dataset.conv === el.dataset.conv));
      state.listOpen = false;
      document.getElementById('chat')?.classList.remove('list-open');
      refreshChatMain();
      return true;
    }
    case 'conv-select':
      toggleConvSelection(el.dataset.conv);
      return true;
    case 'toggle-group': {
      const g = el.dataset.group;
      if (collapsedGroups.has(g)) collapsedGroups.delete(g);
      else collapsedGroups.add(g);
      saveCollapsed();
      refreshConvList();
      return true;
    }
    case 'exit-select':
      exitSelection();
      return true;
    case 'select-all':
      selectAllVisible();
      return true;
    case 'chat-tab':
      switchChatTab(Number(el.dataset.tab) || 0);
      return true;
    case 'batch-move':
    case 'batch-copy': {
      const ids = currentSelection();
      if (ids.length) openTransferSheet(action === 'batch-move' ? 'move' : 'copy', ids);
      return true;
    }
    case 'batch-archive': {
      const ids = currentSelection();
      if (!ids.length) return true;
      const allArchived = ids.every((id) => convById(id)?.archived);
      setArchived(ids, !allArchived);
      return true;
    }
    case 'batch-retitle': {
      const ids = currentSelection();
      if (ids.length) {
        retitleConversations(ids);
        exitSelection();
      }
      return true;
    }
    case 'batch-delete': {
      const ids = currentSelection();
      if (ids.length) confirmDelete(ids);
      return true;
    }
    case 'sheet-assistant':
      sheetAssistant = el.dataset.id;
      refreshSheet();
      return true;
    case 'sheet-folder':
      sheetKeepFolder = !el.dataset.folder;
      sheetFolder = el.dataset.folder || '';
      refreshSheet();
      return true;
    case 'sheet-folders':
      sheetFoldersOpen = !sheetFoldersOpen;
      refreshSheet();
      return true;
    case 'sheet-close':
      closeTransferSheet();
      return true;
    case 'sheet-confirm':
      applyTransfer();
      return true;
    case 'unarchive-current': {
      const c = currentConversation();
      if (c) setArchived([c.id], false);
      return true;
    }
    case 'confirm-delete': {
      const n = deletePendingConversations();
      closeDialog();
      exitSelection();
      runtime.render();
      toast(`已删除 ${n} 个会话`);
      return true;
    }
    case 'preview-file':
      openFilePreview(el.dataset.path);
      return true;
    case 'folder': {
      state.folder = el.dataset.folder;
      if (state.folder === ARCHIVED_GROUP) {
        collapsedGroups.delete(ARCHIVED_GROUP);
        saveCollapsed();
      }
      document.querySelectorAll('[data-folder]').forEach((c) => {
        c.selected = c.dataset.folder === state.folder;
      });
      refreshConvList();
      return true;
    }
    case 'open-list':
      state.listOpen = true;
      document.getElementById('chat')?.classList.add('list-open');
      return true;
    case 'close-list':
      state.listOpen = false;
      document.getElementById('chat')?.classList.remove('list-open');
      return true;
    case 'open-details':
      state.detailsOpen = true;
      document.getElementById('chat')?.classList.add('details-open');
      return true;
    case 'close-details':
      state.detailsOpen = false;
      document.getElementById('chat')?.classList.remove('details-open');
      return true;
    case 'toggle-chain': {
      const card = el.closest('.chain');
      const open = card?.classList.toggle('open');
      el.setAttribute('aria-expanded', String(Boolean(open)));
      return true;
    }
    case 'toggle-notice': {
      const card = el.closest('.notice');
      const open = card?.classList.toggle('open');
      el.setAttribute('aria-expanded', String(Boolean(open)));
      return true;
    }
    case 'open-tool':
      openToolDialog(el.dataset.tool);
      return true;
    case 'pick-model':
      openModelMenu(el.closest('.model-pill, .model-chip') || el);
      return true;
    case 'chat-more':
      openChatMore(el.closest('.icon-btn') || el);
      return true;
    case 'sort-conv':
      openSortMenu(el.closest('.icon-btn') || el);
      return true;
    case 'composer-tools':
      openComposerTools(el.closest('.icon-btn') || el);
      return true;
    case 'suggest': {
      const ta = document.getElementById('composer-input');
      if (ta) {
        ta.value = el.dataset.text || '';
        autoGrow(ta);
        updateSendEnabled(true);
        ta.focus();
      }
      return true;
    }
    case 'send':
      submitComposer();
      return true;
    case 'stop':
      stopStreaming(false);
      return true;
    case 'attach':
      document.getElementById('file-input')?.click();
      return true;
    case 'remove-file': {
      const i = Number(el.dataset.i);
      pendingFiles.splice(i, 1);
      renderPendingFiles();
      return true;
    }
    case 'voice':
      toast('语音输入还没有接到 Web 端');
      return true;
    case 'copy-msg': {
      const m = findMessage(el.dataset.msg);
      copyText(m?.text || '').then((ok) => toast(ok ? '已复制消息' : '复制失败'));
      return true;
    }
    case 'copy-code': {
      const code = el.closest('.code-block')?.querySelector('pre')?.textContent || '';
      copyText(code).then((ok) => toast(ok ? '代码已复制' : '复制失败'));
      return true;
    }
    case 'regenerate': {
      if (state.streaming) return true;
      const conv = currentConversation();
      const idx = conv.messages.findIndex((m) => m.id === el.dataset.msg);
      if (idx < 0) return true;
      if (idx !== conv.messages.length - 1) {
        toast('演示里只重新生成最后一条');
        return true;
      }
      conv.messages.splice(idx, 1);
      const prompt = [...conv.messages].reverse().find((m) => m.role === 'user')?.text || lastPrompt || '继续';
      renderMessagesOnly();
      rerun(prompt);
      return true;
    }
    case 'translate-msg':
      toast('已加入翻译队列');
      return true;
    case 'speak-msg':
      toast('TTS：正在朗读当前消息');
      return true;
    case 'msg-more': {
      const anchor = el.closest('.icon-btn') || el;
      showMenu(anchor, [
        { id: 'select', label: '选择复制', icon: 'content_paste' },
        { id: 'edit', label: '编辑', icon: 'edit' },
        { id: 'share', label: '分享', icon: 'share' },
        { id: 'fork', label: '创建分支', icon: 'call_split' },
        { id: 'fav', label: '添加收藏', icon: 'star' },
        { divider: true, id: 'd' },
        { id: 'del', label: '删除', icon: 'delete', danger: true },
      ], (id) => toast(id === 'del' ? '演示里不真删消息' : '已执行：' + id));
      return true;
    }
    case 'branch-prev':
    case 'branch-next': {
      branchIndex = branchIndex === 1 ? 2 : 1;
      document.querySelectorAll('.branch-num').forEach((n) => (n.textContent = `${branchIndex} / 2`));
      toast(`分支 ${branchIndex} / 2`);
      return true;
    }
    case 'approve-tool':
      toast('已批准 workspace_shell · pnpm build');
      el.closest('.pane-card, .side-card')?.querySelector('.approve')?.remove();
      return true;
    case 'deny-tool':
      toast('已拒绝本次工具调用');
      el.closest('.pane-card, .side-card')?.querySelector('.approve')?.remove();
      return true;
    case 'open-assistant':
      runtime.openAssistantPicker?.();
      return true;
    case 'open-workspace':
      runtime.closePanels?.();
      location.hash = '#/workspace';
      return true;
    default:
      return false;
  }
}

// 重新生成：不新增用户消息，直接重跑一次助手回合。
async function rerun(prompt) {
  const conv = currentConversation();
  if (!conv) return;
  if (conv.archived) {
    toast('会话已归档 · 解除归档后才能继续');
    return;
  }
  const a = currentAssistant();
  state.streaming = true;
  updateComposerStreaming();
  const mine = ++streamToken;
  const reply = {
    id: uid(),
    role: 'assistant',
    assistant: a.id,
    model: conv.model || a.model,
    time: nowTime(),
    text: '',
    streaming: true,
    reasoning: { title: '深度思考 · 重新检查上下文', seconds: '0.9', done: true, content: '保留上一轮的工具结果，只重写正文。' },
    usage: { context: '86.4k / 200k', cost: '¥0.006', speed: '42 tok/s', steps: 1, time: '2.9s' },
  };
  conv.messages.push(reply);
  renderMessagesOnly();
  await wait(600);
  if (mine !== streamToken) return;
  const full = replyFor(prompt);
  for (let i = 0; i < full.length; i += 3) {
    if (mine !== streamToken) return;
    reply.text = full.slice(0, i + 3);
    const bubble = document.querySelector(`[data-bubble="${reply.id}"]`);
    if (bubble) bubble.innerHTML = `${md(reply.text)}<span class="type-cursor"></span>`;
    await wait(16);
  }
  reply.text = full;
  reply.streaming = false;
  state.streaming = false;
  renderMessagesOnly();
  updateComposerStreaming();
}

// 供 app.js 的全局动作复用
export function chatOpenDetails() {
  state.detailsOpen = true;
  document.getElementById('chat')?.classList.add('details-open');
}

export { pendingFiles };
