const log = document.getElementById('log');
const inp = document.getElementById('inp');
const btn = document.getElementById('send');
const resetBtn = document.getElementById('reset');
const pidBox = document.getElementById('pid');
const modeSel = document.getElementById('mode');
const modeNow = document.getElementById('modeNow');
const memoryModeSel = document.getElementById('memoryMode');
const memoryModeNow = document.getElementById('memoryModeNow');

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

function syncContextModeAvailability(memoryMode) {
  const enabled = memoryMode === 'CONTEXT_MODE';
  modeSel.disabled = !enabled;
}

async function loadHistory() {
  try {
    const res = await fetch('/api/history', { method: 'GET' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
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
      let line = `Tokens: user=${s.userTokens}, history=${s.historyTokens}, output=${s.responseTokens}`;
      if (s.reasoningTokens != null) line += `, reasoning=${s.reasoningTokens}`;
      if (s.totalTokens != null) line += `, total=${s.totalTokens}`;
      addLine('sys', line);
    }

    // (Опционально) если сервер вернул ошибку не-200, подсветим это
    if (!res.ok) {
      addLine('sys', `HTTP ${res.status}`);
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

async function loadContextModes() {
  try {
    const res = await fetch('/api/context', { method: 'GET' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    const current = data.mode;
    const available = data.available || [];

    modeSel.innerHTML = '';
    for (const m of available) {
      const opt = document.createElement('option');
      opt.value = m;
      opt.textContent = m;
      if (m === current) opt.selected = true;
      modeSel.appendChild(opt);
    }

    modeNow.textContent = current || 'n/a';
  } catch (e) {
    modeNow.textContent = 'n/a';
    addLine('sys', 'Не удалось загрузить режимы контекста (/api/context): ' + e);
  }
}

async function setContextMode(mode) {
  try {
    const res = await fetch('/api/context', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode })
    });
    const data = await res.json();

    if (!res.ok || !data.ok) {
      addLine('sys', 'Не удалось сменить режим: ' + (data.message || `HTTP ${res.status}`));
      return;
    }

    modeNow.textContent = data.mode;
    addLine('sys', 'Режим контекста переключен на: ' + data.mode);
  } catch (e) {
    addLine('sys', 'Ошибка при смене режима: ' + e);
  }
}

async function loadAgentMemoryMode() {
  try {
    const res = await fetch('/api/memory-mode', { method: 'GET' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    const current = data.mode;
    const available = data.available || [];

    memoryModeSel.innerHTML = '';
    for (const m of available) {
      const opt = document.createElement('option');
      opt.value = m;
      opt.textContent = m;
      if (m === current) opt.selected = true;
      memoryModeSel.appendChild(opt);
    }

    memoryModeNow.textContent = current || 'n/a';
    syncContextModeAvailability(current);
  } catch (e) {
    memoryModeNow.textContent = 'n/a';
    addLine('sys', 'Не удалось загрузить режим памяти (/api/memory-mode): ' + e);
  }
}

async function setAgentMemoryMode(mode) {
  try {
    const res = await fetch('/api/memory-mode', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode })
    });

    const data = await res.json();

    if (!res.ok || !data.ok) {
      addLine('sys', 'Не удалось сменить режим памяти: ' + (data.message || `HTTP ${res.status}`));
      return;
    }

    memoryModeNow.textContent = data.mode;
    syncContextModeAvailability(data.mode);
    addLine('sys', 'Режим памяти переключен на: ' + data.mode);
  } catch (e) {
    addLine('sys', 'Ошибка при смене режима памяти: ' + e);
  }
}

btn.addEventListener('click', send);
resetBtn.addEventListener('click', resetHistory);
inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });
modeSel.addEventListener('change', async () => {
  const m = modeSel.value;
  await setContextMode(m);
});
memoryModeSel.addEventListener('change', async () => {
  const m = memoryModeSel.value;
  await setAgentMemoryMode(m);
});

refreshPid();
loadHistory();
loadAgentMemoryMode();
loadContextModes();
inp.focus();