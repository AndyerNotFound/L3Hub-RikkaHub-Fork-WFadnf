// 设置页：外观实时生效，其余是能点的演示开关。
import { icon, mark } from '../icons.js';
import { DEFAULT_THEME, PALETTES, STYLES, paletteById, rolePreview, isDark } from '../theme.js';
import { state, setTheme, runtime, toast, bus } from '../core.js';
import { user } from '../data.js';
import { esc, openDialog, closeDialog, showMenu, userAvatar } from '../ui.js';

const MODES = [
  { id: 'system', label: '跟随系统', icon: 'contrast' },
  { id: 'light', label: '浅色', icon: 'light_mode' },
  { id: 'dark', label: '深色', icon: 'dark_mode' },
];

let themeSyncBound = false;

function swatchesHtml() {
  const t = state.theme;
  const customActive = t.palette === 'custom';
  const dark = isDark(t.mode);
  return `<div class="swatches">
    ${PALETTES.map((p) => {
      const active = t.palette === p.id;
      return `<button class="swatch ${active ? 'active' : ''}" data-action="palette" data-palette="${p.id}" aria-label="${esc(p.name)}">
        <span class="sw-bar">
          <i style="background:${rolePreview(p.seed, p.style, 'primary', dark)}"></i>
          <i style="background:${rolePreview(p.seed, p.style, 'tertiary-container', dark)}"></i>
          <i style="background:${rolePreview(p.seed, p.style, 'surface-container-high', dark)}"></i>
        </span>
        <span class="sw-name">${esc(p.name)}</span>
        ${active ? `<span class="sw-check">${icon('check', { size: 12 })}</span>` : ''}
      </button>`;
    }).join('')}
    <label class="swatch ${customActive ? 'active' : ''}" data-custom-swatch style="cursor:pointer">
      <span class="sw-bar" style="background:conic-gradient(from 210deg, var(--md-sys-color-primary), var(--md-sys-color-tertiary), var(--md-sys-color-error), var(--md-sys-color-primary))"></span>
      <span class="sw-name">自定义种子色</span>
      <input type="color" id="seed-picker" value="${esc(t.seed)}" aria-label="选择种子色" style="position:absolute;inset:0;opacity:0;cursor:pointer">
    </label>
  </div>`;
}

function stylesHtml() {
  return `<div class="style-grid">
    ${STYLES.map(
      (s) => `<button class="style-chip ${state.theme.style === s.id ? 'active' : ''}" data-action="style" data-style="${s.id}">${esc(s.name)}</button>`
    ).join('')}
  </div>`;
}

function appearanceSection(head = true) {
  const t = state.theme;
  return `<section class="settings-section" id="sec-appearance">
    ${head ? `<div class="settings-section-head"><h2>外观</h2><p>改这里会立刻写进 48 个 color role，Material Web 组件一起变。</p></div>` : ''}
    <div class="card">
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">颜色模式</div><div class="setting-desc">跟随系统时会监听系统的深浅色变化。</div></div>
        <div class="setting-control seg-wrap">
          <md-outlined-segmented-button-set aria-label="颜色模式" data-mode-set>
            ${MODES.map(
              (m) => `<md-outlined-segmented-button label="${m.label}" ${t.mode === m.id ? 'selected' : ''}></md-outlined-segmented-button>`
            ).join('')}
          </md-outlined-segmented-button-set>
        </div>
      </div>
      <div class="setting-row" style="flex-direction:column;align-items:stretch;gap:6px">
        <div class="sr-text"><div class="setting-name">调色板</div><div class="setting-desc">六套预设，或直接挑一个种子色。</div></div>
        ${swatchesHtml()}
      </div>
      <div class="setting-row" style="flex-direction:column;align-items:stretch;gap:6px">
        <div class="sr-text"><div class="setting-name">配色风格</div><div class="setting-desc">同一颗种子，不同的色相偏移与彩度。</div></div>
        ${stylesHtml()}
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">对比度</div><div class="setting-desc">提高后正文更黑、描边更清楚。</div></div>
        <div class="setting-control wide">
          <md-slider id="contrast-slider" min="-1" max="1" step="0.25" value="${t.contrast}" ticks aria-label="对比度"></md-slider>
          <span class="val" id="contrast-val">${Number(t.contrast).toFixed(2)}</span>
        </div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">圆角</div><div class="setting-desc">影响卡片、气泡和会话项；顶栏标签本身不参与圆角。</div></div>
        <div class="setting-control wide">
          <md-slider id="radius-slider" min="8" max="36" step="2" value="${t.radius}" ticks aria-label="圆角"></md-slider>
          <span class="val" id="radius-val">${t.radius}px</span>
        </div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">模糊效果</div><div class="setting-desc">顶栏与会话列表使用 backdrop-filter，关闭后更省电。</div></div>
        <div class="setting-control"><md-switch id="blur-switch" ${t.blur ? 'selected' : ''} aria-label="模糊效果"></md-switch></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">界面动效</div><div class="setting-desc">关闭后所有过渡降为瞬时，仍保留状态反馈。</div></div>
        <div class="setting-control"><md-switch id="motion-switch" ${t.motion ? 'selected' : ''} aria-label="界面动效"></md-switch></div>
      </div>
      <div class="setting-row" style="flex-direction:column;align-items:stretch;gap:6px">
        <div class="sr-text"><div class="setting-name">预览</div><div class="setting-desc">这里用的就是对话页的同一套 role。</div></div>
        <div class="settings-preview">
          <div class="pv-bubble">这一段是用户气泡，用 primary-container。</div>
          <div class="pv-ai">这一段是助手正文，用 surface-container-low 加一条 hairline。${''}</div>
          <div class="pv-tools"><span class="chip primary">Claude Sonnet 4.5</span><span class="chip">工作区</span><span class="chip">记忆</span></div>
        </div>
      </div>
    </div>
  </section>`;
}

function generalSection(head = true) {
  return `<section class="settings-section" id="sec-general">
    ${head ? `<div class="settings-section-head"><h2>通用</h2><p>只保留会在这一版演示里生效的开关。</p></div>` : ''}
    <div class="card">
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">界面语言</div><div class="setting-desc">简中 / 繁中 / English。演示文案目前只维护简中。</div></div>
        <div class="setting-control"><md-text-button data-action="language">${icon('language', { size: 16, slot: 'icon' })}简体中文</md-text-button></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">列表密度</div><div class="setting-desc">紧凑模式会收掉会话条目的上下留白。</div></div>
        <div class="setting-control seg-wrap">
          <md-outlined-segmented-button-set aria-label="列表密度" data-density-set>
            <md-outlined-segmented-button label="舒适" ${state.theme.density !== 'compact' ? 'selected' : ''}></md-outlined-segmented-button>
            <md-outlined-segmented-button label="紧凑" ${state.theme.density === 'compact' ? 'selected' : ''}></md-outlined-segmented-button>
          </md-outlined-segmented-button-set>
        </div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">显示消息时间</div><div class="setting-desc">在操作行末尾显示每条消息的时间。</div></div>
        <div class="setting-control"><md-switch id="time-switch" ${state.theme.showTime !== false ? 'selected' : ''} aria-label="显示消息时间"></md-switch></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">心跳</div><div class="setting-desc">长会话里定期让模型确认一次任务状态。</div></div>
        <div class="setting-control"><md-switch id="heartbeat-switch" selected aria-label="心跳"></md-switch></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">通知</div><div class="setting-desc">生成完成、子代理回传时发系统通知。</div></div>
        <div class="setting-control"><md-switch id="notify-switch" aria-label="通知"></md-switch></div>
      </div>
    </div>
  </section>`;
}

function dataSection(head = true) {
  return `<section class="settings-section" id="sec-data">
    ${head ? `<div class="settings-section-head"><h2>数据</h2><p>演示的“数据”只有主题偏好和会话数组，导出的是一个真的 JSON。</p></div>` : ''}
    <div class="card">
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">自动备份</div><div class="setting-desc">每周把会话、助手与设置打包成一份可导入的备份。</div></div>
        <div class="setting-control"><md-switch id="backup-switch" selected aria-label="自动备份"></md-switch></div>
      </div>
      <div class="setting-row" style="flex-direction:column;align-items:stretch;gap:8px">
        <div class="sr-text"><div class="setting-name">存储</div><div class="setting-desc">会话 12 MB · 附件 86 MB · 工作区 3.2 GB</div></div>
        <div class="storage-line"><span class="meter" style="flex:1"><i class="m1" style="width:38%"></i><i class="m3" style="width:16%"></i></span><span>3.3 GB / 8 GB</span></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">导出备份</div><div class="setting-desc">下载一个 JSON，包含主题偏好与当前演示会话。</div></div>
        <div class="setting-control"><md-outlined-button data-action="export-backup">${icon('download', { size: 16, slot: 'icon' })}导出</md-outlined-button></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">清理缓存</div><div class="setting-desc">只清演示缓存，不动任何工作区文件。</div></div>
        <div class="setting-control"><md-text-button data-action="clear-cache">清理</md-text-button></div>
      </div>
    </div>
  </section>`;
}

function aboutSection(head = true) {
  return `<section class="settings-section" id="sec-about">
    ${head ? `<div class="settings-section-head"><h2>关于</h2><p>这是 L3Hub 的 Web 前端演示，不是 Android 包。</p></div>` : ''}
    <div class="card">
      <div class="about-hero">
        ${mark(56)}
        <div><h3>L3Hub · Material 3 Preview</h3><p>基于 RikkaHub 的二次开发版 · 纯 M3 前端实验</p></div>
      </div>
      <div class="about-grid">
        <div class="about-cell"><span>演示版本</span><b>web-preview-01</b></div>
        <div class="about-cell"><span>Android 版本</span><b>L.3.3.1.4 (399)</b></div>
        <div class="about-cell"><span>Material Web</span><b>2.5.0</b></div>
        <div class="about-cell"><span>颜色工具</span><b>MCU 0.4.0</b></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">恢复默认外观</div><div class="setting-desc">回到星紫 + 色调 + 跟随系统。</div></div>
        <div class="setting-control"><md-outlined-button data-action="reset-theme">${icon('undo', { size: 16, slot: 'icon' })}恢复默认</md-outlined-button></div>
      </div>
      <div class="setting-row">
        <div class="sr-text"><div class="setting-name">源码与文档</div><div class="setting-desc">本地演示；仓库里的 demo/m3 就是这一份。</div></div>
        <div class="setting-control about-links">
          <md-text-button data-action="open-github">${icon('open_in_new', { size: 15, slot: 'icon' })}GitHub</md-text-button>
          <md-text-button data-action="open-readme">${icon('article', { size: 15, slot: 'icon' })}README</md-text-button>
        </div>
      </div>
    </div>
  </section>`;
}

/* ------------------------------------------------------------------ */
/* 分级设置：首页 → 偏好设置 → 主题 / 常规 / 界面 / 通知 / 网络          */
/* ------------------------------------------------------------------ */

export function resetSettingsPage() {
  page = 'home';
}

let page = 'home';

const PAGE_TITLES = {
  home: '设置',
  preferences: '偏好设置',
  theme: '主题',
  general: '常规',
  ui: '界面偏好设置',
  advanced: '高级外观设置',
  notification: '通知',
  network: '网络',
  data: '数据备份',
  about: '关于',
};

const PAGE_PARENT = {
  preferences: 'home',
  theme: 'preferences',
  general: 'preferences',
  ui: 'preferences',
  advanced: 'preferences',
  notification: 'preferences',
  network: 'preferences',
  data: 'home',
  about: 'home',
};

function sgRow({ ico, label, sub, to, route, action, dataLabel }) {
  let attrs = '';
  if (to) attrs = `data-action="settings-go" data-page="${to}"`;
  else if (route) attrs = `data-action="go" data-route="${route}"`;
  else if (action) attrs = `data-action="${action}"${dataLabel ? ` data-label="${esc(dataLabel)}"` : ''}`;
  return `<button class="sg-row" ${attrs}>
    <span class="sg-ico">${icon(ico, { size: 20 })}</span>
    <span class="sg-text"><b>${esc(label)}</b>${sub ? `<small>${esc(sub)}</small>` : ''}</span>
    <span class="sg-chev">${icon('chevron_right', { size: 18 })}</span>
  </button>`;
}

function sgGroup(title, rows) {
  return `<section class="sg-group">${title ? `<h2>${esc(title)}</h2>` : ''}<div class="sg-card">${rows.join('')}</div></section>`;
}

function colorModeRow() {
  const t = state.theme;
  return `<div class="sg-row sg-row-col">
    <div class="sg-row-top">
      <span class="sg-ico">${icon('contrast', { size: 20 })}</span>
      <span class="sg-text"><b>颜色模式</b><small>跟随系统时会监听系统的深浅色变化</small></span>
    </div>
    <md-outlined-segmented-button-set data-mode-set aria-label="颜色模式">
      ${MODES.map(
        (m) => `<md-outlined-segmented-button label="${m.label}" ${t.mode === m.id ? 'selected' : ''}></md-outlined-segmented-button>`
      ).join('')}
    </md-outlined-segmented-button-set>
  </div>`;
}

function switchRow(label, desc, key, checked) {
  return `<div class="setting-row">
    <div class="sr-text"><div class="setting-name">${esc(label)}</div><div class="setting-desc">${esc(desc)}</div></div>
    <div class="setting-control"><md-switch data-demo-switch="${esc(label)}" ${checked ? 'selected' : ''} aria-label="${esc(
    label
  )}"></md-switch></div>
  </div>`;
}

function settingsHome() {
  return `<div class="settings-profile">
      <button class="settings-avatar" data-action="edit-name" aria-label="编辑昵称">${userAvatar('lg')}</button>
      <button class="settings-name" data-action="edit-name">${esc(user.name)}</button>
      <span class="settings-handle">${esc(user.handle)}</span>
    </div>
    ${sgGroup('通用设置', [
      colorModeRow(),
      sgRow({ ico: 'tune', label: '偏好设置', sub: '主题、通知、界面和常规设置', to: 'preferences' }),
      sgRow({ ico: 'smart_toy', label: '助手', sub: '设置个性化助手（智能体）', route: 'assistants' }),
      sgRow({ ico: 'extension', label: '扩展管理', sub: '管理提示词注入、技能等扩展', action: 'settings-stub', dataLabel: '扩展管理' }),
    ])}
    ${sgGroup('模型与服务', [
      sgRow({ ico: 'auto_awesome', label: '默认模型和提示词', sub: '设置各个功能的默认模型', action: 'settings-stub', dataLabel: '默认模型和提示词' }),
      sgRow({ ico: 'hub', label: '提供商', sub: '配置 AI 提供商', route: 'models' }),
      sgRow({ ico: 'search', label: '搜索服务', sub: '设置搜索服务', action: 'settings-stub', dataLabel: '搜索服务' }),
      sgRow({ ico: 'mic', label: '语音服务', sub: '配置语音合成与语音识别服务提供商', action: 'settings-stub', dataLabel: '语音服务' }),
      sgRow({ ico: 'image', label: '媒体', sub: '图像与视频生成提供商、素材上传', action: 'settings-stub', dataLabel: '媒体' }),
      sgRow({ ico: 'account_tree', label: 'MCP', sub: '配置 MCP 服务器', action: 'settings-stub', dataLabel: 'MCP' }),
      sgRow({ ico: 'build', label: '工具覆写', sub: '查看并覆写工具定义与说明', action: 'settings-stub', dataLabel: '工具覆写' }),
      sgRow({ ico: 'terminal', label: 'Web 服务器', sub: '允许你通过网页访问 L3Hub', action: 'settings-stub', dataLabel: 'Web 服务器' }),
    ])}
    ${sgGroup('数据设置', [
      sgRow({ ico: 'database', label: '数据备份', sub: '备份和恢复应用数据', to: 'data' }),
      sgRow({ ico: 'storage', label: '存储管理', sub: '会话 12 MB · 附件 86 MB · 工作区 3.2 GB', to: 'data' }),
    ])}
    ${sgGroup('关于', [
      sgRow({ ico: 'article', label: '关于', sub: '关于本应用', to: 'about' }),
      sgRow({ ico: 'menu_book', label: '使用文档', sub: '查看应用使用说明和帮助文档', action: 'open-readme' }),
      sgRow({ ico: 'history', label: '请求日志', sub: '查看 HTTP 请求日志', action: 'settings-stub', dataLabel: '请求日志' }),
      sgRow({ ico: 'share', label: '分享', sub: '分享本应用给朋友', action: 'settings-stub', dataLabel: '分享' }),
    ])}`;
}

function settingsPreferences() {
  return sgGroup('外观与行为', [
    sgRow({ ico: 'tune', label: '常规', sub: '交互行为、滚动、输入设置', to: 'general' }),
    sgRow({ ico: 'palette', label: '主题', sub: '动态配色、主题、深色模式', to: 'theme' }),
    sgRow({ ico: 'article', label: '界面偏好设置', sub: '消息显示、字体、代码块', to: 'ui' }),
    sgRow({ ico: 'contrast', label: '高级外观设置', sub: '背景、材质和聊天样式', to: 'advanced' }),
    sgRow({ ico: 'notifications', label: '通知', sub: '更新提醒、消息生成通知、后台悬浮窗', to: 'notification' }),
    sgRow({ ico: 'wifi', label: '网络', sub: 'User-Agent 和网络请求设置', to: 'network' }),
  ]);
}

function uiSection() {
  return `<section class="settings-section"><div class="card">
    ${switchRow('显示模型图标', '在消息头部显示模型图标。', 'show-model', true)}
    ${switchRow('代码块自动换行', '长代码不横向滚动。', 'code-wrap', false)}
    ${switchRow('折叠长消息', '超过 8 行的消息默认收起。', 'collapse', false)}
  </div></section>`;
}

function advancedSection() {
  return `<section class="settings-section"><div class="card">
    ${switchRow('透明输入区', '输入区背景跟随页面，不使用容器色。', 'transparent-input', false)}
    ${switchRow('气泡背景模糊', '气泡后方使用模糊材质。', 'bubble-blur', state.theme.blur)}
    ${switchRow('全宽组件', '消息与输入区贴边排列。', 'full-width', false)}
  </div></section>`;
}

function notificationSection() {
  return `<section class="settings-section"><div class="card">
    ${switchRow('生成完成通知', '助手回复结束时发一条系统通知。', 'done-notify', true)}
    ${switchRow('更新提醒', '有新版本时提醒。', 'update-notify', false)}
    ${switchRow('后台悬浮窗', '退到后台时用小窗显示生成进度。', 'floating-window', false)}
  </div></section>`;
}

function networkSection() {
  return `<section class="settings-section"><div class="card">
    <div class="setting-row" style="flex-direction:column;align-items:stretch;gap:8px">
      <div class="sr-text"><div class="setting-name">User-Agent</div><div class="setting-desc">覆盖请求头里的 UA，空值用默认。</div></div>
      <md-outlined-text-field id="ua-input" label="User-Agent" value="L3Hub/1.0 (Web Preview)" style="width:100%"></md-outlined-text-field>
    </div>
    ${switchRow('跟随系统代理', '使用系统的网络代理设置。', 'system-proxy', true)}
  </div></section>`;
}

function themePage() {
  return `${appearanceSection(false)}
    <div class="settings-actions">
      <md-outlined-button data-action="reset-theme">${icon('undo', { size: 16, slot: 'icon' })}恢复默认外观</md-outlined-button>
    </div>`;
}

function settingsBody() {
  switch (page) {
    case 'preferences':
      return settingsPreferences();
    case 'theme':
      return themePage();
    case 'general':
      return generalSection(false);
    case 'ui':
      return uiSection();
    case 'advanced':
      return advancedSection();
    case 'notification':
      return notificationSection();
    case 'network':
      return networkSection();
    case 'data':
      return dataSection(false);
    case 'about':
      return aboutSection(false);
    default:
      return settingsHome();
  }
}

function settingsBarHtml() {
  const home = page === 'home';
  return `<header class="settings-bar ${home ? 'is-home' : ''}">
    ${
      home
        ? `<span class="sb-brand">${mark(26)}</span>`
        : `<button class="icon-btn" data-action="settings-back" aria-label="返回">${icon('arrow_back', { size: 22 })}</button>`
    }
    <h1>${esc(PAGE_TITLES[page] || '设置')}</h1>
    <span class="spacer"></span>
    <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 21 })}</button>
  </header>`;
}

export function renderSettings() {
  return `<div class="page"><div class="settings-inner">
    ${settingsBarHtml()}
    <div class="settings-body">${settingsBody()}</div>
  </div></div>`;
}

export function mountSettings() {
  const modeSet = document.querySelector('[data-mode-set]');
  modeSet?.addEventListener('segmented-button-set-selection', (e) => {
    const mode = MODES[e.detail.index]?.id;
    if (!mode) return;
    setTheme({ mode });
    toast(`颜色模式：${MODES[e.detail.index].label}`);
  });

  const densitySet = document.querySelector('[data-density-set]');
  densitySet?.addEventListener('segmented-button-set-selection', (e) => {
    const density = e.detail.index === 1 ? 'compact' : 'comfortable';
    setTheme({ density });
    document.documentElement.dataset.density = density;
    toast(density === 'compact' ? '列表已改为紧凑' : '列表已改为舒适');
  });
  if (state.theme.density) document.documentElement.dataset.density = state.theme.density;

  const contrast = document.getElementById('contrast-slider');
  contrast?.addEventListener('input', () => {
    const v = Number(contrast.value);
    const label = document.getElementById('contrast-val');
    if (label) label.textContent = v.toFixed(2);
  });
  contrast?.addEventListener('change', () => {
    setTheme({ contrast: Number(contrast.value) });
    toast(`对比度 ${Number(contrast.value).toFixed(2)}`);
  });

  const radius = document.getElementById('radius-slider');
  radius?.addEventListener('input', () => {
    const v = Number(radius.value);
    document.documentElement.style.setProperty('--l3-radius', `${v}px`);
    const label = document.getElementById('radius-val');
    if (label) label.textContent = `${v}px`;
  });
  radius?.addEventListener('change', () => {
    setTheme({ radius: Number(radius.value) }, { animate: false });
  });

  const blur = document.getElementById('blur-switch');
  blur?.addEventListener('change', () => {
    setTheme({ blur: blur.selected });
    toast(blur.selected ? '模糊效果已开启' : '模糊效果已关闭');
  });

  const motion = document.getElementById('motion-switch');
  motion?.addEventListener('change', () => {
    setTheme({ motion: motion.selected }, { animate: false });
    toast(motion.selected ? '动效已开启' : '动效已关闭');
  });

  const time = document.getElementById('time-switch');
  time?.addEventListener('change', () => {
    setTheme({ showTime: time.selected });
    toast(time.selected ? '消息时间已显示' : '消息时间已隐藏');
  });

  const seed = document.getElementById('seed-picker');
  seed?.addEventListener('input', () => {
    setTheme({ palette: 'custom', seed: seed.value }, { animate: true });
    document.querySelectorAll('.swatch').forEach((s) => s.classList.remove('active'));
    seed.closest('.swatch')?.classList.add('active');
  });

  document.querySelectorAll('[data-demo-switch]').forEach((sw) => {
    sw.addEventListener('change', () => {
      toast(`${sw.dataset.demoSwitch}：${sw.selected ? '已开启' : '已关闭'}`);
    });
  });
  document.getElementById('ua-input')?.addEventListener('change', () => toast('User-Agent 已保存（演示）'));

  document.querySelectorAll('.settings-nav [data-section]').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.settings-nav [data-section]').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById(btn.dataset.section)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });
  // 从命令面板或顶栏切主题后，同步回分段按钮（只绑一次）。
  if (!themeSyncBound) {
    themeSyncBound = true;
    bus.addEventListener('theme', () => {
      const modes = ['system', 'light', 'dark'];
      document.querySelectorAll('[data-mode-set] md-outlined-segmented-button').forEach((b, i) => {
        b.selected = modes[i] === state.theme.mode;
      });
    });
  }
}

export function settingsAction(action, el) {
  switch (action) {
    case 'settings-go': {
      const next = el.dataset.page;
      if (PAGE_TITLES[next]) {
        page = next;
        runtime.render();
      }
      return true;
    }
    case 'settings-back': {
      page = PAGE_PARENT[page] || 'home';
      runtime.render();
      return true;
    }
    case 'settings-stub':
      toast(`${el.dataset.label || '这个入口'}：演示里还没接`);
      return true;
    case 'edit-name':
      openDialog({
        headline: '编辑昵称',
        iconName: 'edit',
        content: `<md-outlined-text-field id="name-input" label="昵称" value="${esc(user.name)}" style="width:100%"></md-outlined-text-field>`,
        actions: `<md-text-button data-action="close-dialog">取消</md-text-button><md-filled-button data-action="save-name">保存</md-filled-button>`,
      });
      return true;
    case 'save-name': {
      const value = document.getElementById('name-input')?.value?.trim();
      if (value) user.name = value;
      closeDialog();
      runtime.render();
      toast(value ? '昵称已更新' : '昵称没有变化');
      return true;
    }
    case 'palette': {
      const p = paletteById(el.dataset.palette);
      if (!p) return true;
      setTheme({ palette: p.id, seed: p.seed, style: p.style });
      document.querySelectorAll('.swatch[data-palette]').forEach((s) => s.classList.toggle('active', s.dataset.palette === p.id));
      document.querySelectorAll('.style-chip').forEach((s) => s.classList.toggle('active', s.dataset.style === p.style));
      toast(`调色板：${p.name}`);
      return true;
    }
    case 'style': {
      setTheme({ style: el.dataset.style });
      document.querySelectorAll('.style-chip').forEach((s) => s.classList.toggle('active', s.dataset.style === el.dataset.style));
      toast(`配色风格：${STYLES.find((s) => s.id === el.dataset.style)?.name}`);
      return true;
    }
    case 'reset-theme':
      for (const k of Object.keys(state.theme)) {
        if (!(k in DEFAULT_THEME)) delete state.theme[k];
      }
      setTheme(DEFAULT_THEME);
      document.documentElement.dataset.density = 'comfortable';
      runtime.render();
      toast('已恢复默认外观');
      return true;
    case 'language':
      showMenu(el.closest('md-text-button') || el, [
        { id: 'zh', label: '简体中文', selected: true },
        { id: 'zh-tw', label: '繁體中文' },
        { id: 'en', label: 'English' },
      ], (id) => toast(id === 'zh' ? '已经是简体中文' : '演示版只维护了简体中文文案'));
      return true;
    case 'export-backup': {
      const data = {
        app: 'L3Hub',
        kind: 'web-preview-backup',
        exportedAt: new Date().toISOString(),
        theme: state.theme,
        assistant: state.assistantId,
        conversation: state.conversationId,
      };
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'l3hub-preview-backup.json';
      a.click();
      URL.revokeObjectURL(a.href);
      toast('备份已导出');
      return true;
    }
    case 'clear-cache':
      toast('已清理 12.4 MB 演示缓存');
      return true;
    case 'open-github':
      window.open('https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf', '_blank', 'noopener');
      return true;
    case 'open-readme':
      openDialog({
        headline: 'demo/m3/README.md',
        iconName: 'article',
        content: `<div class="dialog-code">${esc(
          '# L3Hub · Material 3 Web Preview\n\n- 纯 M3：颜色、字体、形状、层级、动效\n- @material/web 2.5.0 + material-color-utilities 0.4.0\n- 无构建步骤，浏览器直接跑 ES module\n- 五个路由 / 三档布局 / 实时主题 / 流式演示'
        )}</div>`,
        actions: '<md-text-button data-action="close-dialog">关闭</md-text-button>',
      });
      return true;
    default:
      return false;
  }
}
