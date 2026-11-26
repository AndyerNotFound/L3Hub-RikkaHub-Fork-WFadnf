// 工作区页：文件树、终端、任务与权限。
import { icon } from '../icons.js';
import { terminalLines, workspaceFiles, workspaceTasks, permissions, filePreviews } from '../data.js';
import { toast } from '../core.js';
import { esc, openDialog, scrollToBottom } from '../ui.js';

const openDirs = new Set(['demo', 'demo/m3', 'demo/m3/js']);
let selectedPath = 'demo/m3/index.html';
let lines = [...terminalLines];

function iconFor(name) {
  if (/\.html$/.test(name)) return 'code';
  if (/\.css$/.test(name)) return 'palette';
  if (/\.js$/.test(name)) return 'code';
  if (/\.md$/.test(name)) return 'article';
  return 'description';
}

function treeHtml(nodes, depth = 0, prefix = '') {
  return nodes
    .map((n) => {
      const path = prefix ? `${prefix}/${n.name}` : n.name;
      if (n.type === 'dir') {
        const open = openDirs.has(path);
        return `<button class="file-row" data-action="toggle-dir" data-path="${esc(path)}" style="--depth:${depth}">
          ${icon(open ? 'folder_open' : 'folder', { size: 16 })}
          <span class="fname">${esc(n.name)}</span>
          <span class="fmeta">${esc(n.meta || '')}</span>
          ${icon(open ? 'expand_less' : 'expand_more', { size: 15 })}
        </button>${open && n.children ? treeHtml(n.children, depth + 1, path) : ''}`;
      }
      return `<button class="file-row ${selectedPath === path ? 'active' : ''}" data-action="file" data-path="${esc(
        path
      )}" style="--depth:${depth}">
        ${icon(iconFor(n.name), { size: 15 })}
        <span class="fname">${esc(n.name)}</span>
        <span class="fmeta">${esc(n.meta || '')}</span>
      </button>`;
    })
    .join('');
}

function fileCard() {
  return `<div class="card file-card">
    <div class="card-head"><span class="card-title">工作区</span><span class="chip">demo/m3</span><span class="spacer"></span><button class="icon-btn xs" data-action="new-file" aria-label="新建文件">${icon(
    'note_add',
    { size: 16 }
  )}</button></div>
    <div class="file-tree" id="file-tree">${treeHtml(workspaceFiles)}</div>
    <div class="file-foot">3.2 GB / 8 GB · 权限按助手设置</div>
  </div>`;
}

function terminalCard() {
  return `<div class="card term-card">
    <div class="term-head">
      <span class="traffic"><i></i><i></i><i></i></span>
      <span class="term-title">u0_a407@localhost · proot Ubuntu 24.04 · zsh</span>
      <span class="spacer"></span>
      <span class="chip">${icon('wifi', { size: 11 })}本地</span>
      <button class="icon-btn xs" data-action="clear-term" aria-label="清空终端">${icon('delete', { size: 15 })}</button>
    </div>
    <div class="term-body" id="term-body">${lines
      .map((l) => `<div class="term-line ${l.kind}">${esc(l.text)}</div>`)
      .join('')}</div>
    <form class="term-form" id="term-form" autocomplete="off">
      <span class="prompt">$</span>
      <input id="term-input" placeholder="输入命令，回车运行…" aria-label="终端命令">
      <button class="icon-btn xs" type="submit" aria-label="运行">${icon('keyboard_return', { size: 16 })}</button>
    </form>
  </div>`;
}

function tasksCard() {
  return `<div class="card task-card">
    <div class="card-head"><span class="card-title">任务</span><span class="chip">${workspaceTasks.filter((t) => t.state === 'running').length} 个运行中</span></div>
    <div class="task-list">
      ${workspaceTasks
        .map(
          (t) => `<div class="task-row ${t.state}">
            <span class="task-ico">${icon('check', { size: 12 })}</span>
            <div class="task-text">${esc(t.title)}<small>${esc(t.who)}</small></div>
            <span class="task-meta">${esc(t.meta)}</span>
          </div>`
        )
        .join('')}
    </div>
  </div>`;
}

function permsCard() {
  return `<div class="card task-card perms">
    <div class="card-head"><span class="card-title">权限</span><span class="chip">本轮</span></div>
    <div id="ws-perms">
      ${permissions
        .map(
          (p) => `<div class="perm-row">
            <div class="perm-text">${esc(p.label)}<small>${esc(p.detail)}</small></div>
            <md-switch data-perm="${esc(p.id)}" ${p.on ? 'selected' : ''} ${p.locked ? 'disabled' : ''}></md-switch>
          </div>`
        )
        .join('')}
    </div>
    <div class="ws-footnote" style="padding:8px 0 0">${icon('shield', { size: 13 })}Shell 与写入默认每次确认</div>
  </div>`;
}

export function renderWorkspace() {
  return `<div class="page"><div class="page-inner">
    <header class="page-head">
      <div>
        <span class="eyebrow">${icon('terminal', { size: 13 })} Workspace</span>
        <h1 class="page-title">工作区</h1>
        <p class="page-sub">一个 proot Linux，文件、终端和子代理都在同一个目录里。权限按助手分开，不共享。</p>
        <div class="ws-head-line">
          <span class="pill"><span class="dot ok"></span>运行中</span>
          <span class="pill">Ubuntu 24.04</span>
          <span class="pill">Node 22</span>
          <span class="pill">Python 3.12</span>
          <span class="pill">12 分 34 秒</span>
        </div>
      </div>
      <div class="head-actions">
        <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 21 })}</button>
        <md-outlined-button data-action="sync-workspace">${icon('sync', { size: 18, slot: 'icon' })}同步</md-outlined-button>
        <md-filled-button data-action="focus-term">${icon('terminal', { size: 18, slot: 'icon' })}打开终端</md-filled-button>
      </div>
    </header>

    <div class="ws-grid">
      <div class="ws-col">${fileCard()}<div class="ws-footnote">${icon('lock', { size: 13 })}目录已限制在 WorkSpace/L3Hub</div></div>
      <div class="ws-col">${terminalCard()}<div class="ws-footnote">${icon('shield', { size: 13 })}演示不会真的执行构建；命令在本地沙箱里回放</div></div>
      <div class="ws-col tasks">${tasksCard()}${permsCard()}</div>
    </div>
  </div></div>`;
}

export function mountWorkspace() {
  const body = document.getElementById('term-body');
  scrollToBottom(body);
  const form = document.getElementById('term-form');
  form?.addEventListener('submit', (e) => {
    e.preventDefault();
    const input = document.getElementById('term-input');
    const cmd = input.value.trim();
    if (!cmd) return;
    input.value = '';
    runCommand(cmd);
  });
  document.getElementById('ws-perms')?.addEventListener('change', (e) => {
    const sw = e.target.closest('md-switch');
    if (!sw) return;
    const p = permissions.find((x) => x.id === sw.dataset.perm);
    if (p) {
      p.on = sw.selected;
      toast(`${p.label} · ${sw.selected ? '已允许' : '已关闭'}`);
    }
  });
}

function runCommand(cmd) {
  const clean = cmd.replace(/^\$\s*/, '');
  lines.push({ kind: 'cmd', text: clean });
  let out = [];
  if (clean === 'clear') {
    lines = [];
    redraw();
    return;
  }
  if (clean === 'help') {
    out = [
      { kind: 'out', text: '可用命令: ls, tree, git status, node -v, pnpm build, clear, help' },
    ];
  } else if (clean === 'ls') {
    out = [{ kind: 'out', text: 'app.css   assets   index.html   js   vendor' }];
  } else if (clean === 'tree') {
    out = [
      { kind: 'out', text: 'demo/m3\n├── index.html\n├── app.css\n├── js/\n│   ├── app.js\n│   ├── theme.js\n│   └── views/\n└── vendor/  (1.9 MB)' },
    ];
  } else if (clean.startsWith('git status')) {
    out = [
      { kind: 'out', text: 'On branch wfadnf\nYour branch is up to date with origin/wfadnf.' },
      { kind: 'dim', text: 'nothing to commit, working tree clean' },
    ];
  } else if (clean === 'node -v') {
    out = [{ kind: 'out', text: 'v26.13.1' }];
  } else if (clean.includes('build') || clean.includes('pnpm')) {
    out = [
      { kind: 'dim', text: '构建被权限策略拦下：需要你批准 workspace_shell。' },
      { kind: 'acc', text: '→ 在右侧「权限」里打开 Shell，或在对话里批准这一次调用。' },
    ];
  } else {
    out = [{ kind: 'dim', text: `zsh: command not found: ${clean.split(' ')[0]}` }];
  }
  lines.push(...out);
  redraw();
}

function redraw() {
  const body = document.getElementById('term-body');
  if (!body) return;
  body.innerHTML = lines.map((l) => `<div class="term-line ${l.kind}">${esc(l.text)}</div>`).join('');
  scrollToBottom(body);
}

function openFile(path) {
  selectedPath = path;
  const tree = document.getElementById('file-tree');
  if (tree) tree.innerHTML = treeHtml(workspaceFiles);
  const preview = filePreviews[path];
  openDialog({
    headline: path.split('/').pop(),
    iconName: 'description',
    content: `<p class="dialog-lead"><span class="mono">${esc(path)}</span> · 只读预览</p>
      ${
        preview
          ? `<div class="dialog-code">${esc(preview.join('\n'))}</div>`
          : '<div class="dialog-code">这个文件没有收录进演示预览。可以在工作区里用 workspace_read_file 读取。</div>'
      }`,
    actions: `<md-text-button data-action="close-dialog">关闭</md-text-button><md-filled-tonal-button data-action="open-in-term">${icon(
      'terminal',
      { size: 16, slot: 'icon' }
    )}在终端打开</md-filled-tonal-button>`,
  });
}

export function workspaceAction(action, el) {
  switch (action) {
    case 'toggle-dir': {
      const p = el.dataset.path;
      if (openDirs.has(p)) openDirs.delete(p);
      else openDirs.add(p);
      const tree = document.getElementById('file-tree');
      if (tree) tree.innerHTML = treeHtml(workspaceFiles);
      return true;
    }
    case 'file':
      openFile(el.dataset.path);
      return true;
    case 'clear-term':
      lines = [];
      redraw();
      toast('终端已清空');
      return true;
    case 'focus-term':
      document.getElementById('term-input')?.focus();
      return true;
    case 'sync-workspace':
      toast('工作区已同步 · 3 个文件有改动');
      return true;
    case 'new-file':
      toast('新建文件会先在工作区里确认路径');
      return true;
    case 'open-in-term': {
      document.querySelector('md-dialog')?.close?.();
      const input = document.getElementById('term-input');
      if (input) {
        input.value = `cat ${selectedPath}`;
        input.focus();
      }
      return true;
    }
    default:
      return false;
  }
}
