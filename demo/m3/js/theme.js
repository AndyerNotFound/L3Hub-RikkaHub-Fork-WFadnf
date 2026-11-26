// M3 主题引擎：用官方 material-color-utilities 从种子色生成全部 color roles。
// 只使用 M3 的 48 个系统色角色，界面里不出现手写十六进制。
import { Hct } from '../vendor/@material/material-color-utilities/hct/hct.js';
import { argbFromHex, hexFromArgb } from '../vendor/@material/material-color-utilities/utils/string_utils.js';
import { MaterialDynamicColors } from '../vendor/@material/material-color-utilities/dynamiccolor/material_dynamic_colors.js';
import { SchemeTonalSpot } from '../vendor/@material/material-color-utilities/scheme/scheme_tonal_spot.js';
import { SchemeVibrant } from '../vendor/@material/material-color-utilities/scheme/scheme_vibrant.js';
import { SchemeExpressive } from '../vendor/@material/material-color-utilities/scheme/scheme_expressive.js';
import { SchemeNeutral } from '../vendor/@material/material-color-utilities/scheme/scheme_neutral.js';
import { SchemeMonochrome } from '../vendor/@material/material-color-utilities/scheme/scheme_monochrome.js';
import { SchemeFidelity } from '../vendor/@material/material-color-utilities/scheme/scheme_fidelity.js';
import { SchemeContent } from '../vendor/@material/material-color-utilities/scheme/scheme_content.js';
import { SchemeRainbow } from '../vendor/@material/material-color-utilities/scheme/scheme_rainbow.js';
import { SchemeFruitSalad } from '../vendor/@material/material-color-utilities/scheme/scheme_fruit_salad.js';

export const PALETTES = [
  { id: 'iris', name: '星紫', seed: '#4C5FD5', style: 'tonal' },
  { id: 'ocean', name: '海蓝', seed: '#00639B', style: 'vibrant' },
  { id: 'forest', name: '松绿', seed: '#2E6B4F', style: 'fidelity' },
  { id: 'sunset', name: '落日', seed: '#B4551F', style: 'expressive' },
  { id: 'sakura', name: '樱粉', seed: '#8E4955', style: 'tonal' },
  { id: 'graphite', name: '石墨', seed: '#5A5A5F', style: 'monochrome' },
];

export const STYLES = [
  { id: 'tonal', name: '色调', cls: SchemeTonalSpot },
  { id: 'vibrant', name: '鲜活', cls: SchemeVibrant },
  { id: 'expressive', name: '表现', cls: SchemeExpressive },
  { id: 'neutral', name: '中性', cls: SchemeNeutral },
  { id: 'fidelity', name: '还原', cls: SchemeFidelity },
  { id: 'content', name: '内容', cls: SchemeContent },
  { id: 'rainbow', name: '彩虹', cls: SchemeRainbow },
  { id: 'fruit', name: '果盘', cls: SchemeFruitSalad },
  { id: 'mono', name: '单色', cls: SchemeMonochrome },
];

const STYLE_BY_ID = Object.fromEntries(STYLES.map((s) => [s.id, s]));
const PALETTE_BY_ID = Object.fromEntries(PALETTES.map((p) => [p.id, p]));

// 与 @material/web 消费的 token 一一对应（48 项）。
const ROLES = [
  ['primary', 'primary'],
  ['onPrimary', 'on-primary'],
  ['primaryContainer', 'primary-container'],
  ['onPrimaryContainer', 'on-primary-container'],
  ['secondary', 'secondary'],
  ['onSecondary', 'on-secondary'],
  ['secondaryContainer', 'secondary-container'],
  ['onSecondaryContainer', 'on-secondary-container'],
  ['tertiary', 'tertiary'],
  ['onTertiary', 'on-tertiary'],
  ['tertiaryContainer', 'tertiary-container'],
  ['onTertiaryContainer', 'on-tertiary-container'],
  ['error', 'error'],
  ['onError', 'on-error'],
  ['errorContainer', 'error-container'],
  ['onErrorContainer', 'on-error-container'],
  ['background', 'background'],
  ['onBackground', 'on-background'],
  ['surface', 'surface'],
  ['onSurface', 'on-surface'],
  ['surfaceVariant', 'surface-variant'],
  ['onSurfaceVariant', 'on-surface-variant'],
  ['outline', 'outline'],
  ['outlineVariant', 'outline-variant'],
  ['shadow', 'shadow'],
  ['scrim', 'scrim'],
  ['inverseSurface', 'inverse-surface'],
  ['inverseOnSurface', 'inverse-on-surface'],
  ['inversePrimary', 'inverse-primary'],
  ['surfaceDim', 'surface-dim'],
  ['surfaceBright', 'surface-bright'],
  ['surfaceContainerLowest', 'surface-container-lowest'],
  ['surfaceContainerLow', 'surface-container-low'],
  ['surfaceContainer', 'surface-container'],
  ['surfaceContainerHigh', 'surface-container-high'],
  ['surfaceContainerHighest', 'surface-container-highest'],
  ['primaryFixed', 'primary-fixed'],
  ['primaryFixedDim', 'primary-fixed-dim'],
  ['onPrimaryFixed', 'on-primary-fixed'],
  ['onPrimaryFixedVariant', 'on-primary-fixed-variant'],
  ['secondaryFixed', 'secondary-fixed'],
  ['secondaryFixedDim', 'secondary-fixed-dim'],
  ['onSecondaryFixed', 'on-secondary-fixed'],
  ['onSecondaryFixedVariant', 'on-secondary-fixed-variant'],
  ['tertiaryFixed', 'tertiary-fixed'],
  ['tertiaryFixedDim', 'tertiary-fixed-dim'],
  ['onTertiaryFixed', 'on-tertiary-fixed'],
  ['onTertiaryFixedVariant', 'on-tertiary-fixed-variant'],
];

const cache = new Map();
const colors = new MaterialDynamicColors();
const media = window.matchMedia('(prefers-color-scheme: dark)');

export const DEFAULT_THEME = {
  mode: 'system', // system | light | dark
  palette: 'iris',
  style: 'tonal',
  seed: '#4C5FD5',
  contrast: 0,
  radius: 24,
  blur: true,
  motion: true,
};

export function paletteById(id) {
  return PALETTE_BY_ID[id] || null;
}

export function styleById(id) {
  return STYLE_BY_ID[id] || STYLE_BY_ID.tonal;
}

export function isDark(mode = DEFAULT_THEME.mode) {
  if (mode === 'dark') return true;
  if (mode === 'light') return false;
  return media.matches;
}

export function watchSystemTheme(cb) {
  const handler = () => cb(isDark('system'));
  media.addEventListener('change', handler);
  return () => media.removeEventListener('change', handler);
}

/** 生成一份完整的 M3 roles（argb → hex）。 */
export function buildRoles(seed, styleId, dark, contrast = 0) {
  const key = `${seed}|${styleId}|${dark}|${contrast}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const style = styleById(styleId);
  const scheme = new style.cls(Hct.fromInt(argbFromHex(seed)), dark, contrast);
  const out = {};
  for (const [fn, css] of ROLES) {
    out[css] = hexFromArgb(colors[fn]().getArgb(scheme));
  }
  out['surface-tint'] = out.primary;
  out['on-surface-tint'] = out['on-primary'];
  if (cache.size > 60) cache.clear();
  cache.set(key, out);
  return out;
}

function hexToRgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function onColor(hex) {
  const [r, g, b] = hexToRgb(hex).map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.45 ? '#000000' : '#FFFFFF';
}

/** 应用主题：写入 CSS 变量并同步 <html> 上的开关。 */
export function applyTheme(theme, { animate = false } = {}) {
  const dark = isDark(theme.mode);
  const roles = buildRoles(theme.seed, theme.style, dark, theme.contrast);
  const root = document.documentElement;
  const run = () => {
    for (const [role, value] of Object.entries(roles)) {
      root.style.setProperty(`--md-sys-color-${role}`, value);
    }
    root.dataset.theme = dark ? 'dark' : 'light';
    root.style.colorScheme = dark ? 'dark' : 'light';
    root.style.setProperty('--l3-radius', `${theme.radius}px`);
    root.style.setProperty('--l3-radius-sm', `${Math.max(8, Math.round(theme.radius * 0.6))}px`);
    root.style.setProperty('--l3-radius-lg', `${Math.round(theme.radius * 1.25)}px`);
    root.dataset.blur = theme.blur ? 'on' : 'off';
    root.dataset.motion = theme.motion ? 'on' : 'off';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', roles.surface);
    document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', dark ? 'dark light' : 'light dark');
  };
  if (animate && document.startViewTransition && theme.motion && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    document.startViewTransition(run);
  } else {
    run();
  }
  return { dark, roles };
}

/** 供调色板小方块使用：直接取某个角色在当前种子下的颜色。 */
export function rolePreview(seed, styleId, role, dark = isDark()) {
  return buildRoles(seed, styleId, dark, 0)[role] || '#000000';
}

export { onColor, hexToRgb };

export function loadTheme() {
  try {
    const raw = localStorage.getItem('l3hub.m3.theme');
    if (!raw) return { ...DEFAULT_THEME };
    const saved = JSON.parse(raw);
    return { ...DEFAULT_THEME, ...saved };
  } catch {
    return { ...DEFAULT_THEME };
  }
}

export function saveTheme(theme) {
  try {
    localStorage.setItem('l3hub.m3.theme', JSON.stringify(theme));
  } catch {
    /* 隐私模式下忽略 */
  }
}
