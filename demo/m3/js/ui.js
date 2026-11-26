// 通用小工具：图标、轻量 Markdown、弹窗、菜单、Snackbar、图表。
import { icon, mark } from './icons.js';

export { icon, mark };

export const esc = (s) =>
  String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

export const uid = () => Math.random().toString(36).slice(2, 9);

export const clamp = (n, a, b) => Math.min(b, Math.max(a, n));

export function nowTime() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

export function toneFor(id = '') {
  let h = 0;
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) % 997;
  return String((h % 3) + 1);
}

export function avatar(a, size = 'md', extra = '') {
  const emoji = typeof a === 'string' ? a : a?.emoji || '✨';
  return `<span class="avatar avatar-${size} tone-${toneFor(typeof a === 'string' ? a : a?.id || emoji)} ${extra}" aria-hidden="true">${emoji}</span>`;
}

export function userAvatar(size = 'md') {
  return `<span class="avatar avatar-${size} avatar-user" aria-hidden="true">W</span>`;
}

/* ------------------------------------------------------------------ */
/* Markdown：只覆盖演示需要的子集（粗体、行内代码、列表、代码块、链接） */
/* ------------------------------------------------------------------ */

function inlineMd(text) {
  return esc(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}

export function md(src) {
  if (!src) return '';
  const codes = [];
  const withTokens = String(src)
    .replace(/\r\n/g, '\n')
    .replace(/```([^\n`]*)\n([\s\S]*?)(```|$)/g, (_m, lang, code) => {
      const i = codes.push({ lang: lang.trim(), code: code.replace(/\n$/, '') }) - 1;
      return `\n\u0000CODE${i}\u0000\n`;
    });

  const lines = withTokens.split('\n');
  const out = [];
  let para = [];
  let list = null;

  const flushPara = () => {
    if (!para.length) return;
    out.push(`<p>${inlineMd(para.join('\n')).replace(/\n/g, '<br>')}</p>`);
    para = [];
  };
  const flushList = () => {
    if (!list) return;
    const tag = list.ordered ? 'ol' : 'ul';
    out.push(`<${tag}>${list.items.map((it) => `<li>${inlineMd(it)}</li>`).join('')}</${tag}>`);
    list = null;
  };

  for (const raw of lines) {
    const line = raw.trimEnd();
    const code = line.match(/^\u0000CODE(\d+)\u0000$/);
    if (code) {
      flushPara();
      flushList();
      const { lang, code: body } = codes[Number(code[1])] || { lang: '', code: '' };
      out.push(
        `<div class="code-block"><div class="code-head"><span class="code-lang">${esc(lang || 'text')}</span><button class="code-copy" type="button" data-action="copy-code" aria-label="复制代码">${icon(
          'content_copy',
          { size: 16 }
        )}</button></div><pre><code>${esc(body)}</code></pre></div>`
      );
      continue;
    }
    if (!line.trim()) {
      flushPara();
      flushList();
      continue;
    }
    const h = line.match(/^(#{2,4})\s+(.*)$/);
    if (h) {
      flushPara();
      flushList();
      const level = Math.min(4, h[1].length + 1);
      out.push(`<h${level}>${inlineMd(h[2])}</h${level}>`);
      continue;
    }
    const ol = line.match(/^\s*\d+\.\s+(.*)$/);
    const ul = line.match(/^\s*[-*]\s+(.*)$/);
    if (ol || ul) {
      flushPara();
      const ordered = Boolean(ol);
      if (!list || list.ordered !== ordered) {
        flushList();
        list = { ordered, items: [] };
      }
      list.items.push((ol || ul)[1]);
      continue;
    }
    flushList();
    para.push(line);
  }
  flushPara();
  flushList();
  return out.join('');
}

/* ------------------------------------------------------------------ */
/* Snackbar / 复制                                                     */
/* ------------------------------------------------------------------ */

let snackTimer = 0;

export function snack(message, { action = '', onAction = null, timeout = 3600 } = {}) {
  const el = document.getElementById('snackbar');
  if (!el) return;
  clearTimeout(snackTimer);
  el.innerHTML = `<span class="snack-text">${esc(message)}</span>${
    action ? `<button class="snack-action" type="button">${esc(action)}</button>` : ''
  }`;
  el.classList.add('is-open');
  if (action && onAction) {
    el.querySelector('.snack-action')?.addEventListener('click', () => {
      hideSnack();
      onAction();
    });
  }
  snackTimer = window.setTimeout(hideSnack, timeout);
}

export function hideSnack() {
  document.getElementById('snackbar')?.classList.remove('is-open');
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.cssText = 'position:fixed;top:-999px;opacity:0';
      document.body.append(ta);
      ta.select();
      const ok = document.execCommand('copy');
      ta.remove();
      return ok;
    } catch {
      return false;
    }
  }
}

/* ------------------------------------------------------------------ */
/* Dialog / Menu                                                       */
/* ------------------------------------------------------------------ */

let activeDialog = null;

export function closeDialog() {
  if (!activeDialog) return;
  const d = activeDialog;
  activeDialog = null;
  d.open = false;
  window.setTimeout(() => d.remove(), 240);
}

export function openDialog({ headline = '', iconName = '', content = '', actions = '', className = '', onMount, onClose } = {}) {
  closeDialog();
  const host = document.getElementById('overlay-root');
  const d = document.createElement('md-dialog');
  d.className = `app-dialog ${className}`;
  d.innerHTML = `
    ${headline ? `<div slot="headline">${headline}</div>` : ''}
    ${iconName ? `<div slot="icon">${icon(iconName)}</div>` : ''}
    <div slot="content" class="dialog-content">${content}</div>
    ${actions ? `<div slot="actions" class="dialog-actions">${actions}</div>` : ''}
  `;
  d.addEventListener('closed', () => {
    if (activeDialog === d) activeDialog = null;
    d.remove();
    onClose?.();
  });
  host.append(d);
  activeDialog = d;
  d.open = true;
  requestAnimationFrame(() => onMount?.(d));
  return d;
}

let openMenuEl = null;

export function closeMenu() {
  if (openMenuEl) {
    const m = openMenuEl;
    openMenuEl = null;
    m.open = false;
    window.setTimeout(() => m.remove(), 200);
  }
}

/**
 * 以 anchor 为锚点弹一个 M3 menu。
 * items: [{id, label, icon, selected, danger, hint, divider}]
 */
export function showMenu(anchor, items, onPick) {
  /* 输入法打开时先收起键盘，再等它落下去，否则 popover 会贴到界面底部。 */
  const active = typeof document !== 'undefined' ? document.activeElement : null;
  const editing = active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA');
  const vv = typeof window !== 'undefined' ? window.visualViewport : null;
  const keyboardOpen = Boolean(vv && vv.height > 0 && vv.height < window.innerHeight - 80);
  if (editing) active.blur();
  /* 键盘还开着时，等它落下去再定位菜单；否则 popover 会贴到可见区底部。 */
  if (keyboardOpen) {
    closeMenu();
    window.setTimeout(() => showMenuNow(anchor, items, onPick), 200);
    return null;
  }
  return showMenuNow(anchor, items, onPick);
}

function showMenuNow(anchor, items, onPick) {
  closeMenu();
  if (!anchor || anchor.isConnected === false) return;
  const menu = document.createElement('md-menu');
  menu.className = 'app-menu';
  menu.anchorElement = anchor;
  menu.positioning = 'popover';
  menu.innerHTML = items
    .map((it) => {
      if (it.divider) return '<md-divider></md-divider>';
      return `<md-menu-item data-menu-id="${esc(it.id)}" class="${it.danger ? 'menu-danger' : ''}">
        ${it.icon ? `<span slot="start" class="menu-icon">${icon(it.icon, { size: 20 })}</span>` : ''}
        <div slot="headline">${esc(it.label)}</div>
        ${it.hint ? `<span slot="end" class="menu-hint">${esc(it.hint)}</span>` : ''}
        ${it.selected ? `<span slot="end" class="menu-check">${icon('check', { size: 18 })}</span>` : ''}
      </md-menu-item>`;
    })
    .join('');
  document.getElementById('overlay-root').append(menu);
  openMenuEl = menu;
  menu.addEventListener('closed', () => {
    if (openMenuEl === menu) openMenuEl = null;
    menu.remove();
  });
  menu.addEventListener('click', (e) => {
    const item = e.target.closest?.('md-menu-item');
    if (!item) return;
    const id = item.dataset.menuId;
    menu.open = false;
    onPick?.(id);
  });
  menu.open = true;
  return menu;
}

/* ------------------------------------------------------------------ */
/* 图表                                                                */
/* ------------------------------------------------------------------ */

export function sparkline(values, { w = 132, h = 40, fill = true } = {}) {
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const span = max - min || 1;
  const step = w / Math.max(1, values.length - 1);
  const pts = values.map((v, i) => [i * step, h - 3 - ((v - min) / span) * (h - 8)]);
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `${line} L${w},${h} L0,${h} Z`;
  return `<svg class="spark" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" aria-hidden="true">
    ${fill ? `<path d="${area}" class="spark-area"/>` : ''}
    <path d="${line}" class="spark-line"/>
    <circle cx="${pts.at(-1)[0].toFixed(1)}" cy="${pts.at(-1)[1].toFixed(1)}" r="2.6" class="spark-dot"/>
  </svg>`;
}

export function stackedBars(days, { w = 560, h = 180 } = {}) {
  const pad = 6;
  const gap = 18;
  const bw = (w - pad * 2 - gap * (days.length - 1)) / days.length;
  const max = Math.max(...days.map((d) => d.input + d.output), 1);
  const groups = days
    .map((d, i) => {
      const x = pad + i * (bw + gap);
      const ih = (d.input / max) * (h - 34);
      const oh = (d.output / max) * (h - 34);
      const total = ih + oh;
      return `<g class="bar-group">
        <rect x="${x}" y="${h - 20 - total}" width="${bw}" height="${oh}" rx="${Math.min(8, bw / 2)}" class="bar-out"/>
        <rect x="${x}" y="${h - 20 - total + oh + 2}" width="${bw}" height="${Math.max(2, ih - 2)}" rx="${Math.min(8, bw / 2)}" class="bar-in"/>
        <text x="${x + bw / 2}" y="${h - 4}" text-anchor="middle" class="bar-label">${esc(d.label)}</text>
      </g>`;
    })
    .join('');
  return `<svg class="bars" viewBox="0 0 ${w} ${h}" role="img" aria-label="过去七天用量">${groups}</svg>`;
}

/* ------------------------------------------------------------------ */

export function scrollToBottom(el, smooth = false) {
  if (!el) return;
  if (typeof el.scrollTo === 'function') {
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
  } else {
    el.scrollTop = el.scrollHeight;
  }
}

export function debounce(fn, ms = 160) {
  let t = 0;
  return (...args) => {
    clearTimeout(t);
    t = window.setTimeout(() => fn(...args), ms);
  };
}
