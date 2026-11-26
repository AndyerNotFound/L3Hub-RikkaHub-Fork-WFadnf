// 模型与服务页：提供商、模型清单、七天用量。
import { icon } from '../icons.js';
import { modelList, providerList, usage } from '../data.js';
import { runtime, toast } from '../core.js';
import { esc, openDialog, sparkline, stackedBars } from '../ui.js';

let activeProvider = 'all';
let activeCap = '全部';
let selectedModel = 'claude-sonnet-4.5';

const caps = ['全部', '视觉', '工具', '推理'];

function providerName(id) {
  return providerList.find((p) => p.id === id)?.name || id;
}

function filteredModels() {
  return modelList.filter((m) => {
    if (activeProvider !== 'all' && m.provider !== activeProvider) return false;
    if (activeCap !== '全部' && !m.caps.includes(activeCap)) return false;
    return true;
  });
}

function providersHtml() {
  return `<div class="card providers">
    <div class="card-head"><span class="card-title">提供商</span><span class="chip">${providerList.length}</span></div>
    <button class="provider-item ${activeProvider === 'all' ? 'active' : ''}" data-action="provider" data-provider="all">
      <span class="provider-letter">${icon('grid_view', { size: 18 })}</span>
      <span class="provider-text"><span class="provider-name">全部提供商</span><span class="provider-host">${modelList.length} 个模型</span></span>
      <span class="spacer"></span>${icon('chevron_right', { size: 18 })}
    </button>
    ${providerList
      .map(
        (p) => `<button class="provider-item ${activeProvider === p.id ? 'active' : ''}" data-action="provider" data-provider="${esc(
          p.id
        )}">
          <span class="provider-letter">${esc(p.letter)}</span>
          <span class="provider-text">
            <span class="provider-name">${esc(p.name)}<span class="dot ${p.status === 'slow' ? 'warn' : 'ok'}"></span></span>
            <span class="provider-host">${esc(p.host)}</span>
            <span class="provider-quota"><i style="width:${Math.round(p.quota * 100)}%"></i></span>
          </span>
          <span class="spacer"></span>
          <span class="model-cell" style="text-align:right">${p.models}<br><span style="font-size:9px">模型</span></span>
        </button>`
      )
      .join('')}
    <md-outlined-button class="providers-add" data-action="add-provider">${icon('add', { size: 17, slot: 'icon' })}添加提供商</md-outlined-button>
  </div>`;
}

function modelRow(m) {
  const p = providerList.find((x) => x.id === m.provider);
  return `<button class="model-row ${selectedModel === m.id ? 'selected' : ''}" data-action="model-select" data-model="${esc(m.id)}">
    <span style="min-width:0">
      <span class="model-name">${esc(m.name)}${m.key ? `<span class="chip primary">默认</span>` : ''}</span>
      <span class="model-id">${esc(m.id)} · ${esc(p?.name || '')}</span>
    </span>
    <span class="model-caps">${m.caps.map((c) => `<span class="chip">${esc(c)}</span>`).join('')}</span>
    <span class="model-cell ctx"><b>${esc(m.context)}</b>上下文</span>
    <span class="model-cell price"><b>${esc(m.price)}</b><span class="model-lat ${m.latency > 80 ? 'slow' : ''}"><i></i>${
      m.latency
    }ms</span></span>
  </button>`;
}

export function renderModels() {
  const list = filteredModels();
  const totalsIn = usage.days.reduce((n, d) => n + d.input, 0);
  const totalsOut = usage.days.reduce((n, d) => n + d.output, 0);
  return `<div class="page"><div class="page-inner">
    <header class="page-head">
      <div>
        <span class="eyebrow">${icon('hub', { size: 13 })} Providers</span>
        <h1 class="page-title">模型与服务</h1>
        <p class="page-sub">一个对话可以随时换模型；配额、延迟和价格放在同一张桌上比较，不用猜。</p>
      </div>
      <div class="head-actions">
        <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 21 })}</button>
        <md-outlined-button data-action="open-settings">${icon('settings', { size: 18, slot: 'icon' })}服务设置</md-outlined-button>
        <md-filled-button data-action="add-provider">${icon('add', { size: 18, slot: 'icon' })}添加提供商</md-filled-button>
      </div>
    </header>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">本月请求</div>
        <div class="stat-value">${usage.requests.toLocaleString()}</div>
        <div class="stat-foot"><span class="stat-delta">+18% 对比上月</span>${sparkline([18, 24, 21, 30, 28, 36, 42], { w: 96, h: 30 })}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Tokens</div>
        <div class="stat-value">${usage.tokens}</div>
        <div class="stat-foot"><span class="stat-delta">缓存命中 62%</span>${sparkline([30, 34, 28, 46, 38, 52, 58], { w: 96, h: 30 })}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">花费</div>
        <div class="stat-value">${usage.cost}</div>
        <div class="stat-foot"><span class="stat-delta">预算内 · 余 68%</span>${sparkline([12, 18, 14, 22, 20, 26, 24], { w: 96, h: 30 })}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">成功率</div>
        <div class="stat-value">${usage.success}</div>
        <div class="stat-foot"><span class="stat-delta">9 次重试</span>${sparkline([96, 98, 99, 97, 99.4, 99.1, 99.2], { w: 96, h: 30 })}</div>
      </div>
    </div>

    <div class="models-layout">
      ${providersHtml()}
      <div class="models-main">
        <div class="card">
          <div class="model-toolbar">
            <h2>${activeProvider === 'all' ? '全部模型' : providerName(activeProvider)}</h2>
            <span class="chip">${list.length} 个</span>
            <span class="spacer"></span>
            <md-text-button data-action="test-model">${icon('bolt', { size: 16, slot: 'icon' })}测试选中模型</md-text-button>
          </div>
          <div class="filter-row" style="padding:0 2px 8px;border:0">
            ${caps
              .map(
                (c) =>
                  `<md-filter-chip label="${c}" data-action="model-cap" data-model-cap="${esc(c)}" ${
                    activeCap === c ? 'selected' : ''
                  }></md-filter-chip>`
              )
              .join('')}
          </div>
          <div class="model-list" id="model-list">${
            list.length ? list.map(modelRow).join('') : '<div class="conv-empty">这个组合下没有模型</div>'
          }</div>
        </div>

        <div class="card usage-card">
          <div class="usage-head">
            <div><h2>过去七天</h2><p>输入 ${totalsIn.toLocaleString()}K · 输出 ${totalsOut.toLocaleString()}K tokens</p></div>
            <div class="legend"><span><i class="l-in"></i>输入</span><span><i class="l-out"></i>输出</span><span>${icon(
              'schedule',
              { size: 12 }
            )} 按 UTC+8</span></div>
          </div>
          ${stackedBars(usage.days)}
        </div>

        <div class="card">
          <div class="card-head"><span class="card-title">花得最多的模型</span><span class="spacer"></span><span class="muted card-sub">本月</span></div>
          ${[
            ['Claude Sonnet 4.5', 0.46, '¥19.60'],
            ['GPT-5.2', 0.27, '¥11.50'],
            ['Gemini 2.5 Pro', 0.16, '¥6.80'],
            ['其它', 0.11, '¥4.70'],
          ]
            .map(
              ([name, pct, cost]) => `<div class="side-row"><span class="k" style="flex:1">${esc(name)}</span>
                <span class="meter" style="width:110px"><i class="m1" style="width:${Math.round(pct * 100)}%"></i></span>
                <span class="v" style="width:64px">${esc(cost)}</span></div>`
            )
            .join('')}
        </div>
      </div>
    </div>
  </div></div>`;
}

function openProviderForm() {
  openDialog({
    headline: '添加提供商',
    iconName: 'hub',
    content: `<p class="dialog-lead">兼容 OpenAI / Anthropic / Google 的服务都可以接入。Key 只保存在本地数据里。</p>
      <div style="display:flex;flex-direction:column;gap:12px">
        <md-outlined-text-field id="pv-name" label="名称" placeholder="例如：Moonshot"></md-outlined-text-field>
        <md-outlined-text-field id="pv-host" label="Base URL" placeholder="https://api.example.com/v1"></md-outlined-text-field>
        <md-outlined-text-field id="pv-key" label="API Key" type="password" placeholder="sk-…"></md-outlined-text-field>
      </div>`,
    actions: `<md-text-button data-action="close-dialog">取消</md-text-button><md-filled-button data-action="create-provider">添加并测试</md-filled-button>`,
  });
}

export function modelsAction(action, el) {
  switch (action) {
    case 'provider': {
      activeProvider = el.dataset.provider;
      document.querySelectorAll('[data-provider]').forEach((p) => p.classList.toggle('active', p.dataset.provider === activeProvider));
      const list = filteredModels();
      const box = document.getElementById('model-list');
      if (box) box.innerHTML = list.length ? list.map(modelRow).join('') : '<div class="conv-empty">这个组合下没有模型</div>';
      const title = document.querySelector('.model-toolbar h2');
      if (title) title.textContent = activeProvider === 'all' ? '全部模型' : providerName(activeProvider);
      return true;
    }
    case 'model-select': {
      selectedModel = el.dataset.model;
      document.querySelectorAll('.model-row').forEach((r) => r.classList.toggle('selected', r.dataset.model === selectedModel));
      return true;
    }
    case 'model-cap': {
      activeCap = el.dataset.modelCap;
      document.querySelectorAll('[data-model-cap]').forEach((c) => {
        c.selected = c.dataset.modelCap === activeCap;
      });
      const list = filteredModels();
      const box = document.getElementById('model-list');
      if (box) box.innerHTML = list.length ? list.map(modelRow).join('') : '<div class="conv-empty">这个组合下没有模型</div>';
      return true;
    }
    case 'add-provider':
      openProviderForm();
      return true;
    case 'create-provider': {
      const name = document.getElementById('pv-name')?.value?.trim() || '新提供商';
      providerList.push({
        id: `p-${Date.now().toString(36)}`,
        name,
        host: document.getElementById('pv-host')?.value?.trim() || 'api.example.com',
        status: 'ok',
        quota: 0.2,
        reset: '刚接入',
        models: 0,
        letter: name.slice(0, 1).toUpperCase(),
      });
      document.querySelector('md-dialog')?.close?.();
      runtime.render();
      toast(`已添加 ${name}，正在测试连接…`);
      return true;
    }
    case 'test-model': {
      const m = modelList.find((x) => x.id === selectedModel);
      toast(m ? `${m.name} · 连接正常 · ${m.latency}ms` : '先选一个模型');
      return true;
    }
    case 'open-settings':
      location.hash = '#/settings';
      return true;
    default:
      return false;
  }
}
