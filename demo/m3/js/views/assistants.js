// 助手页：卡片网格 + 过滤 + 详情 / 新建。
import { icon } from '../icons.js';
import { assistants } from '../data.js';
import { state, currentAssistant, selectAssistant, newConversation, runtime, toast } from '../core.js';
import { avatar, esc, openDialog } from '../ui.js';

let activeTag = '全部';

function tags() {
  return ['全部', ...new Set(assistants.flatMap((a) => a.tags))];
}

function cardsHtml() {
  const list = assistants.filter((a) => activeTag === '全部' || a.tags.includes(activeTag));
  return list
    .map((a) => {
      const active = a.id === state.assistantId;
      return `<button class="assistant-card ${active ? 'active' : ''}" data-action="assistant-detail" data-id="${esc(a.id)}">
        <div class="ac-head">
          ${avatar(a, 'lg')}
          <span class="ac-tag">${active ? '使用中' : esc(a.tags[0] || '助手')}</span>
        </div>
        <div>
          <div class="ac-name">${esc(a.name)}</div>
          <p class="slogan">${esc(a.slogan)}</p>
        </div>
        <div class="meta-line"><span class="dot"></span><span class="ml-name">${esc(a.model)}</span><span>·</span><span>${esc(
        a.provider
      )}</span></div>
        <div class="chip-row">${a.tools.map((t) => `<span class="chip">${esc(t)}</span>`).join('')}</div>
        <div class="ac-foot"><span>${a.conversations} 次对话</span><span>记忆 ${a.memories} 条</span></div>
      </button>`;
    })
    .join('');
}

export function renderAssistants() {
  return `<div class="page"><div class="page-inner">
    <header class="page-head">
      <div>
        <span class="eyebrow">${icon('smart_toy', { size: 13 })} Assistants</span>
        <h1 class="page-title">助手</h1>
        <p class="page-sub">人设、模型与工具打包成可切换的工作伙伴。记忆跟着助手走，不跟着会话走。</p>
      </div>
      <div class="head-actions">
        <button class="icon-btn" data-action="toggle-theme" aria-label="切换主题">${icon('contrast', { size: 21 })}</button>
        <md-outlined-button data-action="import-card">${icon('upload', { size: 18, slot: 'icon' })}导入角色卡</md-outlined-button>
        <md-filled-button data-action="new-assistant">${icon('add', { size: 18, slot: 'icon' })}新建助手</md-filled-button>
      </div>
    </header>

    <div class="filter-row">
      <md-chip-set aria-label="按标签筛选">
        ${tags()
          .map(
            (t) => `<md-filter-chip label="${esc(t)}" data-action="assistant-tag" data-assistant-tag="${esc(t)}" ${
              activeTag === t ? 'selected' : ''
            }></md-filter-chip>`
          )
          .join('')}
      </md-chip-set>
      <span class="muted t-body-m">${assistants.length} 位助手 · 记忆共 ${assistants.reduce(
    (n, a) => n + a.memories,
    0
  )} 条</span>
      <span class="spacer"></span>
      <span class="chip">${icon('cloud_done', { size: 12 })}已同步</span>
    </div>

    <div class="assistant-grid" id="assistant-grid">${cardsHtml()}
      <button class="assistant-card add" data-action="new-assistant">
        <span class="add-ico">${icon('add', { size: 24 })}</span>
        <strong>新建助手</strong>
        <span class="t-body-m muted">从空白开始，或导入一张角色卡</span>
      </button>
    </div>

    <div class="section-head"><h2>共享能力</h2><span class="muted t-body-m">所有助手都能用</span></div>
    <div class="capability-grid">
      <div class="capability">
        <span class="cap-ico">${icon('psychology', { size: 20 })}</span>
        <div><h3>文件式记忆</h3><p>每条记忆都是工作区里的一个文件，可读、可改、可版本化。</p></div>
      </div>
      <div class="capability">
        <span class="cap-ico">${icon('extension', { size: 20 })}</span>
        <div><h3>MCP 与技能</h3><p>工具按需挂载；技能以 Markdown 描述，不写死在提示词里。</p></div>
      </div>
      <div class="capability">
        <span class="cap-ico">${icon('account_tree', { size: 20 })}</span>
        <div><h3>子代理</h3><p>可以并行派活，结果以回传卡片回到主对话，不污染上下文。</p></div>
      </div>
      <div class="capability">
        <span class="cap-ico">${icon('terminal', { size: 20 })}</span>
        <div><h3>工作区</h3><p>proot Linux、终端与文件树共享一个目录，权限按助手单独设置。</p></div>
      </div>
    </div>
  </div></div>`;
}

function openAssistantDetail(id) {
  const a = assistants.find((x) => x.id === id);
  if (!a) return;
  const active = a.id === state.assistantId;
  openDialog({
    headline: a.name,
    iconName: 'smart_toy',
    content: `<div class="dialog-assistant">${avatar(a, 'lg')}<div><h3>${esc(a.name)}</h3><p>${esc(a.provider)} · ${esc(
      a.model
    )}</p></div><span class="spacer"></span>${active ? '<span class="ac-tag">使用中</span>' : ''}</div>
      <p class="dialog-lead">${esc(a.slogan)}</p>
      <div class="dialog-chips">${a.tools.map((t) => `<span class="chip">${esc(t)}</span>`).join('')}</div>
      <div class="dialog-section"><h4>系统提示</h4><div class="dialog-code">${esc(a.prompt)}</div></div>
      <div class="dialog-section"><h4>工作方式</h4>
        <div class="side-row"><span class="k">记忆</span><span class="v">${a.memories} 条 · 跟随助手</span></div>
        <div class="side-row"><span class="k">工具审批</span><span class="v">Shell 需确认，读取自动</span></div>
        <div class="side-row"><span class="k">子代理</span><span class="v">最多 3 个并行</span></div>
      </div>`,
    actions: `<md-text-button data-action="close-dialog">关闭</md-text-button>${
      active
        ? `<md-outlined-button data-action="close-dialog">已经选中</md-outlined-button>`
        : `<md-filled-button data-action="use-assistant" data-id="${esc(a.id)}">切换到此助手</md-filled-button>`
    }`,
  });
}

function openNewAssistant() {
  openDialog({
    headline: '新建助手',
    iconName: 'smart_toy',
    content: `<p class="dialog-lead">先起个名字和一句口号。模型、工具与记忆可以之后再调。</p>
      <div style="display:flex;flex-direction:column;gap:12px">
        <md-outlined-text-field id="na-name" label="名字" placeholder="例如：Neko"></md-outlined-text-field>
        <md-outlined-text-field id="na-slogan" label="口号" placeholder="今天想聊点什么？"></md-outlined-text-field>
        <md-outlined-text-field id="na-model" label="模型" value="Claude Sonnet 4.5"></md-outlined-text-field>
      </div>
      <div class="dialog-section"><h4>默认工具</h4><div class="dialog-chips"><span class="chip">记忆</span><span class="chip">工作区</span><span class="chip">搜索</span></div></div>`,
    actions: `<md-text-button data-action="close-dialog">取消</md-text-button><md-filled-button data-action="create-assistant">创建</md-filled-button>`,
  });
}

export function assistantsAction(action, el) {
  switch (action) {
    case 'assistant-detail':
      openAssistantDetail(el.dataset.id);
      return true;
    case 'assistant-tag': {
      activeTag = el.dataset.assistantTag;
      document.querySelectorAll('[data-assistant-tag]').forEach((c) => {
        c.selected = c.dataset.assistantTag === activeTag;
      });
      const grid = document.getElementById('assistant-grid');
      if (grid) grid.innerHTML = `${cardsHtml()}<button class="assistant-card add" data-action="new-assistant"><span class="add-ico">${icon(
        'add',
        { size: 24 }
      )}</span><strong>新建助手</strong><span class="t-body-m muted">从空白开始，或导入一张角色卡</span></button>`;
      return true;
    }
    case 'use-assistant': {
      selectAssistant(el.dataset.id);
      newConversation();
      document.querySelector('md-dialog')?.close?.();
      if (location.hash === '#/chat') runtime.render();
      else location.hash = '#/chat';
      toast(`已切换到 ${currentAssistant().name}，新开了一个对话`);
      return true;
    }
    case 'new-assistant':
      openNewAssistant();
      return true;
    case 'create-assistant': {
      const name = document.getElementById('na-name')?.value?.trim() || '新助手';
      const slogan = document.getElementById('na-slogan')?.value?.trim() || '今天想聊点什么？';
      const model = document.getElementById('na-model')?.value?.trim() || 'Claude Sonnet 4.5';
      const a = {
        id: `a-${Date.now().toString(36)}`,
        name,
        emoji: '✨',
        slogan,
        model,
        provider: '自定义',
        tags: ['日常'],
        tools: ['记忆', '工作区', '搜索'],
        conversations: 0,
        memories: 0,
        prompt: '你是新创建的助手。先读上下文，再回答。',
      };
      assistants.push(a);
      selectAssistant(a.id);
      document.querySelector('md-dialog')?.close?.();
      runtime.render();
      toast(`已创建 ${name}`);
      return true;
    }
    case 'import-card':
      toast('SillyTavern 角色卡导入还没接到 Web 演示');
      return true;
    default:
      return false;
  }
}
