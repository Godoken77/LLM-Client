package org.example.ui

fun htmlPage(): String = """
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Persistent LLM Agent Chat</title>
  <style>
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; margin: 0; background: #0b0f17; color: #e6e6e6; }
    .wrap { max-width: 900px; margin: 0 auto; padding: 18px; }
    .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 16px; box-shadow: 0 8px 30px rgba(0,0,0,.25); }
    h1 { font-size: 18px; margin: 0 0 12px; opacity: .9; }
    #log { height: 60vh; overflow: auto; padding: 12px; background: #0b1220; border-radius: 12px; border: 1px solid #1f2937; white-space: pre-wrap; }
    .msg { margin: 10px 0; line-height: 1.35; }
    .me { color: #93c5fd; }
    .bot { color: #a7f3d0; }
    .sys { color: #fbbf24; opacity: .9; }
    .row { display: flex; gap: 10px; margin-top: 12px; }
    input { flex: 1; padding: 12px 12px; border-radius: 12px; border: 1px solid #374151; background: #0b1220; color: #e6e6e6; outline: none; }
    button { padding: 12px 14px; border-radius: 12px; border: 1px solid #374151; background: #111827; color: #e6e6e6; cursor: pointer; }
    button:disabled { opacity: .6; cursor: not-allowed; }
    .hint { margin-top: 10px; font-size: 12px; opacity: .75; display: flex; gap: 10px; align-items: center; }
    code { background: rgba(255,255,255,.06); padding: 2px 6px; border-radius: 8px; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>Web Agent Chat (с сохранением контекста в JSON)</h1>
      <div id="log"></div>

      <div class="row">
        <input id="inp" placeholder="Напишите сообщение..." autocomplete="off"/>
        <button id="send">Отправить</button>
        <button id="reset">Сбросить историю</button>
      </div>

      <div class="hint">
        Server PID: <code id="pid">...</code> • История лежит в <code>./data/&lt;SID&gt;.json</code>
      </div>
    </div>
  </div>

<script>
const log = document.getElementById('log');
const inp = document.getElementById('inp');
const btn = document.getElementById('send');
const resetBtn = document.getElementById('reset');
const pidBox = document.getElementById('pid');

function addLine(cls, text) {
  const div = document.createElement('div');
  div.className = 'msg ' + cls;
  div.textContent = text;
  log.appendChild(div);
  log.scrollTop = log.scrollHeight;
}

function clsByRole(role) {
  if (role === 'user') return 'me';
  if (role === 'assistant') return 'bot';
  return 'sys';
}

function prefixByRole(role) {
  if (role === 'user') return 'Вы: ';
  if (role === 'assistant') return 'Агент: ';
  return '';
}

// NEW: загрузка истории с сервера
async function loadHistory() {
  try {
    const res = await fetch('/api/history', { method: 'GET' });
    if (!res.ok) throw new Error(`HTTP ${'$'}{res.status}`);
    const data = await res.json();

    log.innerHTML = '';

    const items = data.items ?? [];
    if (items.length === 0) {
      addLine('sys', 'История пуста. Начните диалог.');
      return;
    }

    for (const it of items) {
      const role = it.role ?? 'sys';
      const text = it.text ?? '';
      addLine(clsByRole(role), prefixByRole(role) + text);
    }
  } catch (e) {
    // не критично: просто покажем подсказку
    log.innerHTML = '';
    addLine('sys', 'Не удалось загрузить историю (/api/history): ' + e);
  }
}

async function refreshPid(){
  try { pidBox.textContent = await fetch('/api/pid').then(r => r.text()); }
  catch { pidBox.textContent = 'n/a'; }
}

async function send() {
  const text = inp.value.trim();
  if (!text) return;

  addLine('me', 'Вы: ' + text);
  inp.value = '';
  btn.disabled = true;

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text })
    });

    const data = await res.json();
    addLine('bot', 'Агент: ' + (data.reply ?? '(пусто)'));

    // ✅ NEW: вывод токенов/статистики, если бэк их возвращает
    if (data.stats) {
      const s = data.stats;
      let line = `Tokens: user=${'$'}{s.userTokens}, history=${'$'}{s.historyTokens}, output=${'$'}{s.responseTokens}`;
      if (s.reasoningTokens != null) line += `, reasoning=${'$'}{s.reasoningTokens}`;
      if (s.totalTokens != null) line += `, total=${'$'}{s.totalTokens}`;
      addLine('sys', line);
    }

    // (Опционально) если сервер вернул ошибку не-200, подсветим это
    if (!res.ok) {
      addLine('sys', `HTTP ${'$'}{res.status}`);
    }
  } catch (e) {
    addLine('bot', 'Агент: Ошибка сети: ' + e);
  } finally {
    btn.disabled = false;
    inp.focus();
  }
}

async function resetHistory() {
  try {
    await fetch('/api/reset', { method: 'POST' });
    await loadHistory(); // NEW: перерисовать после сброса
  } catch (e) {
    addLine('sys', 'Не удалось сбросить историю: ' + e);
  }
}

btn.addEventListener('click', send);
resetBtn.addEventListener('click', resetHistory);
inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });

refreshPid();
loadHistory(); // NEW: при старте страницы подтянуть прошлые сообщения
inp.focus();
</script>
</body>
</html>
""".trimIndent()