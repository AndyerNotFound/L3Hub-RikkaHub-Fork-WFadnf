// 设置页：外观实时生效，其余是能点的演示开关。
import { icon, mark } from '../icons.js';
import { DEFAULT_THEME, PALETTES, STYLES, paletteById, rolePreview, isDark } from '../theme.js';
import { state, setTheme, runtime, toast, bus } from '../core.js';
import { esc, openDialog, showMenu } from '../ui.js';

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

function appearanceSection() {
  const t = state.theme;
  return `<section class="settings-section" id="sec-appearance">
    <div class="settings-section-head"><h2>外观</h2><p>改这里会立刻写进 48 个 color role，Material Web 组件一起变。</p></div>
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

function generalSection() {
  return `<section class="settings-section" id="sec-general">
    <div class="settings-section-head"><h2>通用</h2><p>只保留会在这一版演示里生效的开关。</p></div>
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

function dataSection() {
  return `<section class="settings-section" id="sec-data">
    <div class="settings-section-head"><h2>数据</h2><p>演示的“数据”只有主题偏好和会话数组，导出的是一个真的 JSON。</p></div>
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

function aboutSection() {
  return `<section class="settings-section" id="sec-about">
    <div class="settings-section-head"><h2>关于</h2><p>这是 L3Hub 的 Web 前端演示，不是 Android 包。</p></div>
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

export function renderSettings() {
  return `<div class="page"><div class="page-inner">
    <header class="page-head">
      <div>
        <span class="eyebrow">${icon('settings', { size: 13 })} Settings</span>
        <h1 class="page-title">设置</h1>
        <p class="page-sub">主题是这一页的主角。换一颗种子色，整页的层级、气泡、按钮都会重新长一遍。</p>
      </div>
      <div class="head-actions">
        <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 21 })}</button>
        <md-filled-tonal-button data-action="reset-theme">${icon('undo', { size: 17, slot: 'icon' })}恢复默认</md-filled-tonal-button>
      </div>
    </header>
    <div class="settings-grid">
      <nav class="settings-nav" aria-label="设置分区">
        <button class="settings-nav-item active" data-section="sec-appearance">${icon('palette', { size: 16 })}外观</button>
        <button class="settings-nav-item" data-section="sec-general">${icon('tune', { size: 16 })}通用</button>
        <button class="settings-nav-item" data-section="sec-data">${icon('database', { size: 16 })}数据</button>
        <button class="settings-nav-item" data-section="sec-about">${icon('article', { size: 16 })}关于</button>
      </nav>
      <div class="settings-main">
        ${appearanceSection()}
        ${generalSection()}
        ${dataSection()}
        ${aboutSection()}
      </div>
    </div>
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
