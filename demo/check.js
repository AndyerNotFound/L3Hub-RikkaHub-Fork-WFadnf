
(function(){
'use strict';

const $  = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => Array.prototype.slice.call(el.querySelectorAll(s));
const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const wait = ms => new Promise(r => setTimeout(r, ms));

/* ================= 数据 ================= */

const state = {
  view: 'list',
  theme: 'light',
  hue: 248,
  filter: 'all',
  query: '',
  model: {label:'Claude Sonnet 4.5', provider:'Anthropic', note:'200K 上下文 · 3 个密钥', hue:248, badge:'主力'},
  thread: null,
  streaming: false,
  runId: 0
};

const ASSISTANTS = [
  {name:'默认助手', model:'Claude Sonnet 4.5', hue:248, glyph:'默'},
  {name:'代码评审', model:'GPT-5.1', hue:168, glyph:'码'},
  {name:'翻译', model:'Gemini 3 Pro', hue:292, glyph:'译'}
];

const MODELS = [
  {provider:'Anthropic', items:[
    {label:'Claude Sonnet 4.5', provider:'Anthropic', note:'200K 上下文 · 3 个密钥', hue:248, badge:'主力'},
    {label:'Claude Haiku 4.5', provider:'Anthropic', note:'200K 上下文 · 快速', hue:248, badge:'轻量'}
  ]},
  {provider:'OpenAI', items:[
    {label:'GPT-5.1', provider:'OpenAI', note:'400K 上下文 · 2 个密钥', hue:168, badge:'代理'},
    {label:'GPT-5.1 mini', provider:'OpenAI', note:'400K 上下文 · 低成本', hue:168}
  ]},
  {provider:'DeepSeek', items:[
    {label:'DeepSeek V3.2', provider:'DeepSeek', note:'128K 上下文 · 按量计费', hue:292}
  ]},
  {provider:'Google', items:[
    {label:'Gemini 3 Pro', provider:'Google', note:'1M 上下文 · 多模态', hue:22}
  ]}
];

const seed = {
  t2: [
    {role:'user', text:'界面改造要分几批做？'},
    {role:'ai', blocks:[
      {type:'text', md:'一次做完。分批上线会让两套组件在一段时间里并存，交互与会话里的手势都得写两份适配。'},
      {type:'text', md:'这一批的范围按顺序是：\n\n- 设置页换成 Salt 分组列表；\n- 抽屉全量换成 Salt Sheet；\n- 主题接入 SaltUI 的取色与字体；\n- 收尾清掉旧的 Miuix 桥接层。'},
      {type:'meta', text:'0.9s 首字 · 1,860 tokens · ¥0.031'}
    ]}
  ],
  t3: [
    {role:'user', text:'微信不在前台且 PSS 超过 300MB 就杀，会不会误伤？'},
    {role:'ai', blocks:[
      {type:'text', md:'会，所以判定条件要三项同时成立：**不在前台**、**屏幕已息**、**内存超阈值**。'},
      {type:'text', md:'另外微信持有 `USE_EXACT_ALARM`，受限分桶落不下去，读回仍是 20；实际生效的是 appops 的后台 ignore。待机分组那条路留在日志里观察即可。'},
      {type:'meta', text:'1.2s 首字 · 940 tokens · ¥0.014'}
    ]}
  ],
  t4: [
    {role:'user', text:'这一版写进发布说明的改动有哪些？'},
    {role:'ai', blocks:[
      {type:'text', md:'按面向用户的口径，值得写进去的有四项：\n\n- 设置首页改用 Salt 分组列表，层级更清楚；\n- 抽屉换成贴边 Sheet，支持下滑关闭；\n- 流式预览按落帧合并，长回复更稳；\n- 页面转场动画修复，返回不再闪一下。'},
      {type:'meta', text:'0.8s 首字 · 720 tokens · ¥0.011'}
    ]}
  ],
  t5: [
    {role:'user', text:'为什么编译不能在 Termux 里跑？'},
    {role:'ai', blocks:[
      {type:'text', md:'内存与散热都不够。Gradle 与 Kotlin 守护进程常驻之后，手机会一直处于高负载，充电都压不住温度。'},
      {type:'text', md:'现在的做法是 `compguard` 常驻，发现编译进程就查杀；确需编译时用 `compguard allow 10m` 临时放行。'},
      {type:'meta', text:'1.1s 首字 · 1,120 tokens · ¥0.018'}
    ]}
  ]
};

const threads = [
  {id:'t1', title:'协议网关的分帧合并', snippet:'逐 chunk 写 logd 会把日志刷满，改成按帧合并后再落盘。', time:'14:22', glyph:'协', hue:248, pinned:true, tool:true, model:'Claude Sonnet 4.5', provider:'Anthropic'},
  {id:'t2', title:'Miuix 迁移的范围', snippet:'界面改造要分几批？一次做完。', time:'11:05', glyph:'迁', hue:292, pinned:true, tool:false, model:'Claude Sonnet 4.5', provider:'Anthropic'},
  {id:'t3', title:'compguard 的内存阈值', snippet:'不在前台、屏幕已息、内存超限，三项同时成立才杀。', time:'昨天', glyph:'守', hue:168, pinned:false, tool:true, model:'DeepSeek V3.2', provider:'DeepSeek'},
  {id:'t4', title:'发布说明 L.3.3.0.8', snippet:'设置首页、抽屉、流式落帧、转场动画。', time:'昨天', glyph:'发', hue:22, pinned:false, tool:false, model:'GPT-5.1', provider:'OpenAI'},
  {id:'t5', title:'Termux 里的 Gradle', snippet:'内存与散热都不够，编译交给别处。', time:'11/21', glyph:'编', hue:200, pinned:false, tool:true, model:'Gemini 3 Pro', provider:'Google'}
];

const SEED_T1 = [
  {role:'user', text:'流式预览为什么不能每个 chunk 都更新一次界面？现在的做法是什么？'},
  {role:'ai', blocks:[
    {type:'thinking', sec:6, text:'这条问题要落到具体代码上：先找 onChunk 的调用点，再确认现在刷新发生在哪一层。日志只作为佐证，不能当结论。'},
    {type:'tool', title:'workspace · grep', arg:'"onChunk" · app/src/main', state:'done', stateText:'3 处命中',
     out:'docs/references/chat-generation-pipeline.md:112  合并写入\nChatVM.kt:418                    onChunk(...)\nStreamAccumulator.kt:88          flushOnFrame()'},
    {type:'text', md:'相关代码集中在 `ChatVM` 与 `StreamAccumulator` 两处。逐 chunk 直接刷新界面的问题在于：每个报文都会触发一次重组与纹理上传，网络抖动时 UI 线程被高频小任务挤满，滚动与输入都会掉帧。'},
    {type:'text', md:'现在的做法是**落帧合并**：\n\n- 缓冲区接收 chunk，只做字符串拼接；\n- 同一帧内的多次追加合并为一次状态提交；\n- 逐 chunk 报文不再写 logd，只记录合并后的结果。'},
    {type:'code', lang:'kotlin', code:'private fun onChunk(chunk: String) {\n    buffer.append(chunk)\n    if (!framePosted) {\n        framePosted = true\n        postOnFrame { framePosted = false; flush() }\n    }\n}'},
    {type:'text', md:'需要逐帧数据时打开 trace 通道即可，不必依赖日志。\n\n> 本节内容取自项目内的生成链路文档，仅作演示。'},
    {type:'meta', text:'1.8s 首字 · 1,204 tokens · ¥0.021'}
  ]}
];

function historyOf(t){
  if(t.messages) return t.messages;
  if(seed[t.id]) return (t.messages = seed[t.id]);
  return (t.messages = SEED_T1);
}

/* ================= Markdown ================= */

function highlight(code){
  const re = /(\/\/[^\n]*)|("(?:[^"\\]|\\.)*")|\b(private|fun|val|var|if|return|class|object|suspend|import|override|inline|when|else|true|false|null)\b/g;
  let out = '', last = 0, m;
  while((m = re.exec(code))){
    out += esc(code.slice(last, m.index));
    if(m[1]) out += '<span class="c">' + esc(m[1]) + '</span>';
    else if(m[2]) out += '<span class="s">' + esc(m[2]) + '</span>';
    else out += '<span class="k">' + esc(m[3]) + '</span>';
    last = re.lastIndex;
  }
  return out + esc(code.slice(last));
}

function inline(s){
  let t = esc(s);
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  return t;
}

function codeBlock(lang, code){
  return '<div class="code"><div class="code-head"><span>' + esc(lang || 'text') +
    '</span><button data-copy aria-label="复制"><svg class="ico sm"><use href="#i-copy"/></svg></button></div>' +
    '<pre><code>' + highlight(code) + '</code></pre></div>';
}

function md(src){
  const lines = String(src).replace(/\r/g, '').split('\n');
  let out = '', para = [], list = null;
  const flushPara = () => { if(para.length){ out += '<p>' + inline(para.join(' ')) + '</p>'; para = []; } };
  const flushList = () => {
    if(list){ out += '<ul>' + list.map(x => '<li>' + inline(x) + '</li>').join('') + '</ul>'; list = null; }
  };
  for(let i = 0; i < lines.length; i++){
    const line = lines[i];
    if(/^```/.test(line)){
      flushPara(); flushList();
      const lang = line.slice(3).trim(), code = [];
      i++;
      while(i < lines.length && !/^```/.test(lines[i])) code.push(lines[i++]);
      out += codeBlock(lang, code.join('\n'));
      continue;
    }
    if(!line.trim()){ flushPara(); flushList(); continue; }
    if(/^###\s+/.test(line)){ flushPara(); flushList(); out += '<h3>' + inline(line.replace(/^###\s+/, '')) + '</h3>'; continue; }
    if(/^[-*]\s+/.test(line)){ flushPara(); (list = list || []).push(line.replace(/^[-*]\s+/, '')); continue; }
    if(/^>\s?/.test(line)){ flushPara(); flushList(); out += '<blockquote>' + inline(line.replace(/^>\s?/, '')) + '</blockquote>'; continue; }
    flushList(); para.push(line.trim());
  }
  flushPara(); flushList();
  return out;
}

/* ================= 渲染 ================= */

const msgs = $('#msgs');

function aiHead(){
  return '<div class="ai-head"><div class="ai-mark">L3</div><div class="ai-name">' +
    esc(state.model.label) + '<em>' + esc(state.model.provider) + '</em></div></div>';
}

function thinkEl(sec, text){
  const d = document.createElement('details');
  d.className = 'think';
  d.innerHTML = '<summary><span class="mini-spark"><svg class="ico"><use href="#i-spark"/></svg></span>' +
    '思考 ' + sec + ' 秒<svg class="ico sm" style="margin-left:auto"><use href="#i-chev"/></svg></summary>' +
    '<div class="think-body">' + esc(text) + '</div>';
  return d;
}

function toolEl(b){
  const d = document.createElement('div');
  d.className = 'tool ' + (b.state === 'done' ? 'done' : 'run');
  d.innerHTML = '<button class="tool-head"><div class="tool-ico"><svg class="ico sm"><use href="#i-folder"/></svg></div>' +
    '<div class="tool-txt"><b>' + esc(b.title) + '</b><span>' + esc(b.arg) + '</span></div>' +
    '<div class="tool-state"><i class="dot"></i>' + esc(b.stateText || (b.state === 'done' ? '完成' : '运行中')) + '</div>' +
    '<svg class="ico sm chev"><use href="#i-chev"/></svg></button>' +
    '<div class="tool-out">' + esc(b.out || '') + '</div>';
  d.querySelector('.tool-head').addEventListener('click', () => d.classList.toggle('open'));
  return d;
}

function textEl(markdown){
  const d = document.createElement('div');
  d.className = 'md-live';
  d.innerHTML = md(markdown);
  return d;
}

function metaEl(text){
  const d = document.createElement('div');
  d.className = 'meta';
  d.innerHTML = text.split(' · ').map((x, i) =>
    (i ? '<i class="dot"></i>' : '') + '<span>' + esc(x) + '</span>'
  ).join('');
  return d;
}

function renderBlock(b){
  if(b.type === 'thinking') return thinkEl(b.sec || 4, b.text);
  if(b.type === 'tool') return toolEl(b);
  if(b.type === 'code'){
    const d = document.createElement('div');
    d.innerHTML = codeBlock(b.lang, b.code);
    return d.firstChild;
  }
  if(b.type === 'meta') return metaEl(b.text);
  return textEl(b.md || '');
}

function renderMessage(m){
  if(m.role === 'user'){
    const d = document.createElement('div');
    d.className = 'msg user';
    const b = document.createElement('div');
    b.className = 'bubble-user';
    b.textContent = m.text;
    d.appendChild(b);
    return d;
  }
  const wrap = document.createElement('div');
  wrap.className = 'msg ai';
  wrap.innerHTML = aiHead();
  const body = document.createElement('div');
  body.className = 'bubble-ai ai-blocks';
  (m.blocks || []).forEach(b => body.appendChild(renderBlock(b)));
  wrap.appendChild(body);
  return wrap;
}

function scrollBottom(){
  if(!msgs) return;
  msgs.scrollTop = msgs.scrollHeight;
}

/* ================= 会话列表 ================= */

const listEl = $('#threadList');

function renderThreads(){
  const q = state.query.trim().toLowerCase();
  const rows = threads.filter(t => {
    if(state.filter === 'pinned' && !t.pinned) return false;
    if(state.filter === 'tool' && !t.tool) return false;
    if(!q) return true;
    return (t.title + t.snippet + t.model).toLowerCase().indexOf(q) >= 0;
  });
  if(!rows.length){
    listEl.innerHTML = '<li class="empty">没有匹配的会话</li>';
    return;
  }
  listEl.innerHTML = rows.map(t => {
    const tags = [];
    if(t.pinned) tags.push('<span class="tag on">置顶</span>');
    if(t.tool) tags.push('<span class="tag">工具调用</span>');
    tags.push('<span class="tag">' + esc(t.model) + '</span>');
    return '<li><button class="thread" data-thread="' + t.id + '">' +
      '<div class="avatar" style="--ah:' + t.hue + '">' + esc(t.glyph) + '</div>' +
      '<div class="thread-body"><div class="thread-top">' +
        '<span class="thread-title">' + esc(t.title) + '</span>' +
        '<span class="thread-time">' + esc(t.time) + '</span></div>' +
      '<div class="thread-snippet">' + esc(t.snippet) + '</div>' +
      '<div class="thread-tags">' + tags.join('') + '</div>' +
      '</div></button></li>';
  }).join('');
}

listEl.addEventListener('click', e => {
  const btn = e.target.closest('[data-thread]');
  if(btn) openChat(btn.dataset.thread);
});

$('#searchInput').addEventListener('input', e => {
  state.query = e.target.value;
  renderThreads();
});

$$('.chip[data-filter]').forEach(c => c.addEventListener('click', () => {
  $$('.chip[data-filter]').forEach(x => x.classList.toggle('is-on', x === c));
  state.filter = c.dataset.filter;
  renderThreads();
}));

/* ================= 视图切换 ================= */

function go(name, dir){
  const from = $('.view.is-active');
  const to = $('.view[data-view="' + name + '"]');
  if(!to || from === to) return;
  dir = dir || 'forward';
  to.hidden = false;
  ['enter-right','enter-left','exit-left','exit-right'].forEach(c => {
    to.classList.remove(c); from.classList.remove(c);
  });
  void to.offsetWidth;
  to.classList.add(dir === 'forward' ? 'enter-right' : 'enter-left');
  from.classList.add(dir === 'forward' ? 'exit-left' : 'exit-right');
  state.view = name;
  $$('.nav-item').forEach(n => n.classList.toggle('on', n.dataset.nav === name));
  setTimeout(() => {
    from.hidden = true;
    from.classList.remove('exit-left','exit-right');
    to.classList.remove('enter-right','enter-left');
  }, 360);
}

function openChat(id){
  const t = threads.find(x => x.id === id);
  if(!t) return;
  state.thread = t;
  $('#chatTitle').textContent = t.title;
  $('#chatSub').textContent = t.model + ' · ' + t.provider;
  msgs.innerHTML = '';
  historyOf(t).forEach(m => msgs.appendChild(renderMessage(m)));
  closeDrawer();
  go('chat');
  requestAnimationFrame(scrollBottom);
}

function newChat(){
  const t = {id:'t' + Date.now(), title:'新的会话', snippet:'尚未开始', time:'刚刚', glyph:'新',
    hue: state.hue, pinned:false, tool:false, model: state.model.label, provider: state.model.provider,
    messages:[{role:'ai', blocks:[{type:'text', md:'新的会话已就绪。当前模型为 **' + state.model.label +
      '**，工作区权限为读写。需要我做什么？'}]}]};
  threads.unshift(t);
  renderThreads();
  openChat(t.id);
}

$('#newChat').addEventListener('click', newChat);
$$('[data-back]').forEach(b => b.addEventListener('click', () => {
  if(state.streaming) stop();
  go('list', 'back');
}));

/* ================= 抽屉 ================= */

const drawer = $('#drawer'), drawerScrim = $('#drawerScrim');

function openDrawer(){
  drawer.classList.add('show');
  drawerScrim.classList.add('show');
  drawer.setAttribute('aria-hidden', 'false');
}
function closeDrawer(){
  drawer.classList.remove('show');
  drawerScrim.classList.remove('show');
  drawer.setAttribute('aria-hidden', 'true');
}

$$('[data-drawer]').forEach(b => b.addEventListener('click', openDrawer));
drawerScrim.addEventListener('click', closeDrawer);

$$('.nav-item').forEach(n => n.addEventListener('click', () => {
  closeDrawer();
  go(n.dataset.nav);
}));

$('#assistList').innerHTML = ASSISTANTS.map(a =>
  '<button class="assist-row" data-assist="' + esc(a.name) + '">' +
  '<span class="avatar" style="--ah:' + a.hue + '">' + esc(a.glyph) + '</span>' +
  '<span><b>' + esc(a.name) + '</b><span>' + esc(a.model) + '</span></span></button>'
).join('');

$('#assistList').addEventListener('click', e => {
  const row = e.target.closest('[data-assist]');
  if(!row) return;
  const a = ASSISTANTS.find(x => x.name === row.dataset.assist);
  const m = MODELS.flatMap(g => g.items).find(x => x.label === a.model);
  if(m) selectModel(m, true);
  closeDrawer();
  toast('已切换助手：' + a.name);
});

/* ================= 模型 Sheet ================= */

const sheet = $('#sheet'), sheetScrim = $('#sheetScrim');

$('#modelList').innerHTML = MODELS.map(g =>
  '<div class="provider">' + esc(g.provider) + '</div>' +
  g.items.map(m =>
    '<button class="model-row" data-model="' + esc(m.label) + '">' +
    '<span class="avatar" style="--ah:' + m.hue + '">' + esc(m.label.slice(0, 2)) + '</span>' +
    '<span class="mt"><b>' + esc(m.label) + '</b><span>' + esc(m.note) + '</span></span>' +
    (m.badge ? '<span class="badge">' + esc(m.badge) + '</span>' : '') +
    '<svg class="ico sm"><use href="#i-check"/></svg></button>'
  ).join('')
).join('');

function markModel(){
  $$('.model-row').forEach(r => r.classList.toggle('on', r.dataset.model === state.model.label));
}

function selectModel(m, silent){
  state.model = m;
  $('#modelChip').querySelector('span').textContent = m.label;
  if(state.thread){
    state.thread.model = m.label;
    state.thread.provider = m.provider;
  }
  $('#chatSub').textContent = m.label + ' · ' + m.provider;
  $('#input').placeholder = '发送消息给 ' + m.label + '…';
  markModel();
  renderThreads();
  if(!silent) toast('已切换到 ' + m.label);
}

function openSheet(){
  markModel();
  sheet.classList.add('show');
  sheetScrim.classList.add('show');
  sheet.setAttribute('aria-hidden', 'false');
}
function closeSheet(){
  sheet.classList.remove('show');
  sheetScrim.classList.remove('show');
  sheet.setAttribute('aria-hidden', 'true');
}

$('#modelChip').addEventListener('click', openSheet);
$('#sheetClose').addEventListener('click', closeSheet);
sheetScrim.addEventListener('click', closeSheet);
$('#modelList').addEventListener('click', e => {
  const row = e.target.closest('[data-model]');
  if(!row) return;
  const m = MODELS.flatMap(g => g.items).find(x => x.label === row.dataset.model);
  if(m){ selectModel(m); closeSheet(); }
});
$$('[data-sheet]').forEach(b => b.addEventListener('click', openSheet));

document.addEventListener('keydown', e => {
  if(e.key === 'Escape'){ closeSheet(); closeDrawer(); }
});

/* ================= 发送与流式演示 ================= */

const input = $('#input'), sendBtn = $('#send');

function autosize(){
  input.style.height = 'auto';
  input.style.height = Math.min(input.scrollHeight, 112) + 'px';
}

function setSendState(){
  if(state.streaming){
    sendBtn.classList.add('stop');
    sendBtn.innerHTML = '<svg class="ico"><use href="#i-stop"/></svg>';
    sendBtn.setAttribute('aria-label', '停止');
    $('#sendHint').textContent = '生成中';
  }else{
    sendBtn.classList.remove('stop');
    sendBtn.innerHTML = '<svg class="ico"><use href="#i-send"/></svg>';
    sendBtn.setAttribute('aria-label', '发送');
    $('#sendHint').textContent = 'Enter 发送';
  }
}

function stop(){
  state.runId++;
  state.streaming = false;
  setSendState();
  $$('.caret').forEach(c => c.remove());
  toast('已停止生成');
}

async function typeInto(box, text, runId, delay){
  const el = document.createElement('div');
  el.className = 'md-live';
  const caret = document.createElement('span');
  caret.className = 'caret';
  box.appendChild(el);
  box.appendChild(caret);
  const step = 2;
  for(let i = step; i < text.length + step; i += step){
    if(runId !== state.runId) break;
    el.innerHTML = md(text.slice(0, i));
    scrollBottom();
    await wait(delay || 14);
  }
  if(runId === state.runId) el.innerHTML = md(text);
  caret.remove();
  return el;
}

async function send(){
  if(state.streaming){ stop(); return; }
  const text = input.value.trim();
  if(!text) return;
  input.value = '';
  autosize();

  const t = state.thread;
  if(!t) return;
  const history = historyOf(t);
  history.push({role:'user', text});
  msgs.appendChild(renderMessage({role:'user', text}));
  scrollBottom();

  if(t.title === '新的会话' || !t.title){
    t.title = text.length > 16 ? text.slice(0, 16) + '…' : text;
    $('#chatTitle').textContent = t.title;
  }
  t.snippet = text;
  t.time = '刚刚';
  renderThreads();

  const runId = ++state.runId;
  state.streaming = true;
  setSendState();

  const wrap = document.createElement('div');
  wrap.className = 'msg ai';
  wrap.innerHTML = aiHead();
  const body = document.createElement('div');
  body.className = 'bubble-ai ai-blocks';
  wrap.appendChild(body);
  msgs.appendChild(wrap);
  scrollBottom();

  /* 思考 */
  const sec = 4;
  const think = thinkEl(sec, '');
  body.appendChild(think);
  const thinkBody = think.querySelector('.think-body');
  await typeInto(thinkBody, '先判断这条消息需要哪一层能力：直接回答、检索工作区，还是调用工具。演示里走一遍完整链路——检索、读文件、给出结论，最后标注用量。', runId, 12);
  if(runId !== state.runId) return;
  think.open = false;

  /* 工具 */
  const tool = toolEl({title:'workspace · grep', arg:'"onChunk" · app/src/main', state:'run', stateText:'运行中'});
  body.appendChild(tool);
  scrollBottom();
  await wait(900);
  if(runId !== state.runId) return;
  tool.className = 'tool done open';
  tool.querySelector('.tool-state').innerHTML = '<i class="dot"></i>3 处命中';
  tool.querySelector('.tool-out').textContent =
    'docs/references/chat-generation-pipeline.md:112  合并写入\n' +
    'ChatVM.kt:418                    onChunk(...)\n' +
    'StreamAccumulator.kt:88          flushOnFrame()';
  scrollBottom();
  await wait(320);
  if(runId !== state.runId) return;

  /* 正文 */
  const text1 = '工作区里相关的代码集中在 `ChatVM` 与 `StreamAccumulator` 两处。**逐 chunk 直接刷新界面**的问题在于：每个报文都会触发一次重组与纹理上传，网络抖动时 UI 线程被高频小任务挤满，滚动与输入都会掉帧。';
  await typeInto(body, text1, runId, 14);
  if(runId !== state.runId) return;

  const text2 = '现在的做法是**落帧合并**：\n\n- 缓冲区接收 chunk，只做字符串拼接；\n- 同一帧内的多次追加合并为一次状态提交；\n- 逐 chunk 报文不再写 logd，只记录合并后的结果。';
  await typeInto(body, text2, runId, 12);
  if(runId !== state.runId) return;

  await wait(240);
  if(runId !== state.runId) return;
  const code = document.createElement('div');
  code.style.animation = 'msgIn .3s cubic-bezier(.2,.9,.25,1) both';
  code.innerHTML = codeBlock('kotlin', 'private fun onChunk(chunk: String) {\n    buffer.append(chunk)\n    if (!framePosted) {\n        framePosted = true\n        postOnFrame { framePosted = false; flush() }\n    }\n}');
  body.appendChild(code);
  scrollBottom();
  await wait(420);
  if(runId !== state.runId) return;

  const text3 = '需要逐帧数据时打开 trace 通道即可，不必依赖日志。\n\n> 本段回复由演示脚本生成，并非真实模型输出。';
  await typeInto(body, text3, runId, 12);
  if(runId !== state.runId) return;

  const meta = metaEl('1.8s 首字 · 1,204 tokens · ¥0.021');
  meta.style.opacity = '0';
  meta.style.transition = 'opacity .3s';
  body.appendChild(meta);
  requestAnimationFrame(() => { meta.style.opacity = '1'; });
  scrollBottom();

  history.push({role:'ai', blocks:[
    {type:'thinking', sec, text:'先判断这条消息需要哪一层能力：直接回答、检索工作区，还是调用工具。演示里走一遍完整链路——检索、读文件、给出结论，最后标注用量。'},
    {type:'tool', title:'workspace · grep', arg:'"onChunk" · app/src/main', state:'done', stateText:'3 处命中',
     out:'docs/references/chat-generation-pipeline.md:112  合并写入\nChatVM.kt:418                    onChunk(...)\nStreamAccumulator.kt:88          flushOnFrame()'},
    {type:'text', md:text1},
    {type:'text', md:text2},
    {type:'code', lang:'kotlin', code:'private fun onChunk(chunk: String) {\n    buffer.append(chunk)\n    if (!framePosted) {\n        framePosted = true\n        postOnFrame { framePosted = false; flush() }\n    }\n}'},
    {type:'text', md:text3},
    {type:'meta', text:'1.8s 首字 · 1,204 tokens · ¥0.021'}
  ]});
  t.tool = true;

  state.streaming = false;
  setSendState();
}

sendBtn.addEventListener('click', send);
input.addEventListener('input', autosize);
input.addEventListener('keydown', e => {
  if(e.key === 'Enter' && !e.shiftKey && !e.isComposing){
    e.preventDefault();
    send();
  }
});

/* ================= 复制 ================= */

document.addEventListener('click', e => {
  const btn = e.target.closest('[data-copy]');
  if(!btn) return;
  const box = btn.closest('.code');
  const code = box ? box.querySelector('pre').textContent : '';
  const done = () => {
    btn.innerHTML = '<svg class="ico sm"><use href="#i-check"/></svg>';
    toast('代码已复制');
    setTimeout(() => { btn.innerHTML = '<svg class="ico sm"><use href="#i-copy"/></svg>'; }, 1600);
  };
  if(navigator.clipboard && navigator.clipboard.writeText){
    navigator.clipboard.writeText(code).then(done, done);
  }else{
    done();
  }
});

/* ================= 用量图表 ================= */

const SEGS = [
  {label:'Anthropic', v:46, color:'hsl(248 58% 56%)', tokens:'570k'},
  {label:'DeepSeek',  v:28, color:'hsl(292 50% 58%)', tokens:'347k'},
  {label:'OpenRouter',v:18, color:'hsl(168 46% 46%)', tokens:'223k'},
  {label:'Google',    v:8,  color:'hsl(30 82% 55%)',  tokens:'99k'}
];

function buildDonut(){
  const svg = $('#donut');
  const R = 54, C = 2 * Math.PI * R;
  let acc = 0, html = '<circle cx="66" cy="66" r="54" stroke="var(--surface-3)"/>';
  SEGS.forEach(s => {
    const len = Math.max(C * s.v / 100 - 4, 2);
    html += '<circle cx="66" cy="66" r="54" stroke="' + s.color + '" stroke-dasharray="' + len + ' ' + (C - len) + '" stroke-dashoffset="' + (-acc) + '"/>';
    acc += C * s.v / 100;
  });
  svg.innerHTML = html;
  $('#legend').innerHTML = SEGS.map(s =>
    '<div class="legend-row"><i style="--c:' + s.color + '"></i>' + s.label +
    ' <b>' + s.v + '%</b><span>' + s.tokens + '</span></div>'
  ).join('');
}

function buildBars(){
  const vals = [42, 58, 36, 64, 88, 52, 71, 95, 63, 81, 110, 74, 92, 58];
  const max = Math.max.apply(null, vals);
  $('#bars').innerHTML = vals.map((v, i) =>
    '<div class="bar" style="height:' + Math.round(v / max * 100) + '%;animation-delay:' + (i * 22) + 'ms" title="' +
    v + 'k tokens"></div>'
  ).join('');
}

/* ================= 主题 / 取色 / 模糊 ================= */

const mq = window.matchMedia('(prefers-color-scheme: dark)');
const root = document.documentElement;

function applyTheme(){
  const dark = state.theme === 'dark' || (state.theme === 'system' && mq.matches);
  root.dataset.theme = dark ? 'dark' : 'light';
  $$('#themeSeg button').forEach(b => b.classList.toggle('on', b.dataset.themeOpt === state.theme));
  $('#themeToggleLabel').textContent = dark ? '切换浅色' : '切换深色';
  $('#themeToggle').querySelector('use').setAttribute('href', dark ? '#i-sun' : '#i-moon');
}

function applyHue(h){
  state.hue = h;
  root.style.setProperty('--h', h);
  root.style.setProperty('--h2', (h + 44) % 360);
  $$('#swatches .swatch').forEach(s => s.classList.toggle('on', Number(s.dataset.hue) === h));
  renderThreads();
}

$$('#themeSeg button').forEach(b => b.addEventListener('click', () => {
  state.theme = b.dataset.themeOpt;
  applyTheme();
}));

$('#themeToggle').addEventListener('click', () => {
  const dark = root.dataset.theme === 'dark';
  state.theme = dark ? 'light' : 'dark';
  applyTheme();
});

$$('#swatches .swatch').forEach(s => s.addEventListener('click', () => {
  applyHue(Number(s.dataset.hue));
  toast('取色已更新');
}));

mq.addEventListener('change', () => { if(state.theme === 'system') applyTheme(); });

const blurRange = $('#blurRange');
function applyBlur(v){
  root.style.setProperty('--blur', v + 'px');
  $('#blurVal').textContent = v + ' px';
  blurRange.style.setProperty('--p', (v / 34 * 100) + '%');
}
blurRange.addEventListener('input', e => applyBlur(Number(e.target.value)));

/* ================= 杂项 ================= */

let toastTimer = null;
function toast(text){
  const el = $('#toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 1800);
}

$$('[data-toast]').forEach(b => b.addEventListener('click', () => toast(b.dataset.toast)));

$$('.switch').forEach(s => s.addEventListener('click', () => {
  const on = s.getAttribute('aria-checked') === 'true';
  s.setAttribute('aria-checked', String(!on));
  toast(s.dataset.toggle + (on ? '已关闭' : '已开启'));
}));

function tickClock(){
  const d = new Date();
  const str = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  $$('[data-clock]').forEach(el => { el.textContent = str; });
}
tickClock();
setInterval(tickClock, 20000);

$('#resetDemo').addEventListener('click', () => location.reload());

/* ================= 初始化 ================= */

renderThreads();
buildDonut();
buildBars();
markModel();
selectModel(state.model, true);
applyTheme();
applyHue(state.hue);
applyBlur(Number(blurRange.value));
setSendState();
autosize();

document.querySelector('textarea').setAttribute('placeholder', '发送消息给 ' + state.model.label + '…');
})();
