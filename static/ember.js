(() => {
  const page = document.body.dataset.page;

  // persist mode
  const modeSel = document.getElementById('modeSel');
  if (modeSel) {
    const saved = localStorage.getItem('ember_mode');
    if (saved) modeSel.value = saved;
    modeSel.addEventListener('change', () => localStorage.setItem('ember_mode', modeSel.value));
  }

  // Inventory push button (calls mounted app endpoint)
  const invPushBtn = document.getElementById('invPushBtn');
  const pushModal = document.getElementById('pushModal');
  const pushClose = document.getElementById('pushClose');
  const pushOut = document.getElementById('pushOutput');

  function showPushModal(text) {
    if (!pushModal || !pushOut) return;
    pushOut.textContent = text || '';
    pushModal.setAttribute('aria-hidden', 'false');
    pushModal.classList.add('open');
  }
  function hidePushModal() {
    if (!pushModal) return;
    pushModal.setAttribute('aria-hidden', 'true');
    pushModal.classList.remove('open');
  }
  if (pushClose) pushClose.addEventListener('click', hidePushModal);
  if (pushModal) pushModal.addEventListener('click', (e) => {
    if (e.target === pushModal) hidePushModal();
  });

  async function fetchJsonOrText(url, opts) {
    const resp = await fetch(url, opts);
    const text = await resp.text();

    let data = null;
    try {
      data = JSON.parse(text);
    } catch (_) {
      // not JSON
    }

    return { resp, text, data };
  }

  function trimBody(s, max = 4000) {
    const t = String(s || '');
    if (t.length <= max) return t;
    return t.slice(0, max) + "\n\n…(truncated)…\n";
  }

  if (invPushBtn) {
    invPushBtn.addEventListener('click', async () => {
      showPushModal('Running /inventory/push…\n');
      try {
        const { resp, text, data } = await fetchJsonOrText('/inventory/push', { method: 'POST' });

        // If we got JSON, use it. If not, show raw response body.
        if (data && typeof data === 'object') {
          if (!data.ok) {
            showPushModal(
              `FAILED (HTTP ${resp.status}, code ${data.code ?? '??'})\n\n` +
              `${data.error || data.output || ''}`
            );
            return;
          }
          showPushModal(`OK (HTTP ${resp.status}, code ${data.code})\n\n${data.output || ''}`);
          return;
        }

        // Non-JSON response (HTML error page, plain text, etc.)
        const ct = resp.headers.get('content-type') || '(no content-type)';
        showPushModal(
          `NON-JSON RESPONSE (HTTP ${resp.status})\n` +
          `content-type: ${ct}\n\n` +
          trimBody(text)
        );
      } catch (err) {
        showPushModal(`Error: ${err}`);
      }
    });
  }

  // Chat page logic
  if (page !== 'chat') return;

  const chat = document.getElementById('chat');
  const form = document.getElementById('composer');
  const msg = document.getElementById('msg');

  const history = [];

  function addBubble(role, text) {
    const row = document.createElement('div');
    row.className = 'row ' + role;
    const bubble = document.createElement('div');
    bubble.className = 'bubble ' + role;
    bubble.textContent = text;
    row.appendChild(bubble);
    chat.appendChild(row);
    chat.scrollTop = chat.scrollHeight;
  }

  addBubble('assistant', 'Ember online.');

  async function send(text) {
    const mode = (modeSel && modeSel.value) ? modeSel.value : (localStorage.getItem('ember_mode') || 'medium');

    addBubble('user', text);
    addBubble('assistant', '…');

    const placeholder = chat.querySelector('.row.assistant:last-child .bubble.assistant');

    const payload = { query: text, history: history.slice(-12), mode };

    try {
      const resp = await fetch('/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await resp.json();
      const out = (data.response || '').trim();
      placeholder.textContent = out || '(empty)';
      history.push({ user: text, assistant: out });
    } catch (e) {
      placeholder.textContent = 'Error: ' + e;
    }
  }

  // Enter to send, Shift+Enter newline
  msg.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const text = (msg.value || '').trim();
    if (!text) return;
    msg.value = '';
    send(text);
  });
})();
