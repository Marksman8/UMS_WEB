// Floating AI assistant: a small chat drawer backed by /api/ai/chat. Replies are inserted as text only.

import { api } from './api.js';
import { h, clear, errorToast } from './ui.js';
import { icon } from './icons.js';

const SUGGESTIONS = {
  STUDENT: ['What is my CGPA?', 'Which courses am I below attendance in?', 'How many credits can I register?', 'What is my timetable?'],
  FACULTY: ['Which sections do I teach?', 'Which students are at risk in my courses?', 'Which mark sheets are still draft?'],
  ADMIN: ['How many students are enrolled?', 'What is waiting for approval?', 'Which sections are full?'],
  DEV: ['How is the database doing?', 'How many tables are there?', 'What happened recently?'],
};
const KEEP_TURNS = 12;

export function mountAssistant(user) {
  document.getElementById('ai-root')?.remove();
  const root = h('div', { id: 'ai-root' });
  const history = [];
  let mode = null;

  const log = h('div', { class: 'ai-log', 'aria-live': 'polite' });
  const chips = h('div', { class: 'ai-suggest' });
  const input = h('input', { type: 'text', maxlength: 1000, placeholder: 'Ask about your data…', 'aria-label': 'Message' });
  const sub = h('span', null, 'Ask about your data');
  const send = h('button', { class: 'btn', type: 'submit', 'aria-label': 'Send' }, icon('send'));

  const add = (who, text, source) => {
    log.append(h('div', { class: `msg ${who}` }, text, source ? h('span', { class: 'src' }, source) : null));
    log.scrollTop = log.scrollHeight;
  };

  const ask = async (text) => {
    const message = text.trim();
    if (!message) return;
    clear(chips);
    add('user', message);
    input.value = '';
    send.disabled = true; input.disabled = true;
    const pending = h('div', { class: 'msg bot' }, h('div', { class: 'spinner', style: 'width:16px;height:16px' }));
    log.append(pending); log.scrollTop = log.scrollHeight;
    try {
      const res = await api.post('/api/ai/chat', { message, history: history.slice(-KEEP_TURNS) });
      pending.remove();
      add('bot', res.reply, res.source === 'ollama' ? 'local model' : res.source === 'claude' ? 'Claude' : 'built-in assistant');
      history.push({ role: 'user', text: message }, { role: 'assistant', text: res.reply });
      if (history.length > KEEP_TURNS * 2) history.splice(0, history.length - KEEP_TURNS * 2);
    } catch (e) {
      pending.remove();
      errorToast(e);
    } finally {
      send.disabled = false; input.disabled = false; input.focus();
    }
  };

  const panel = h('div', { class: 'ai-panel', role: 'dialog', 'aria-label': 'Assistant', hidden: true },
    h('div', { class: 'ai-head' }, icon('sparkles'), h('div', null, h('b', null, 'Assistant'), sub),
      h('button', { class: 'min', 'aria-label': 'Minimize assistant', title: 'Minimize (Esc)', onclick: () => toggle(false) }, 'Minimize')),
    log, chips,
    h('form', { class: 'ai-form', onsubmit: (e) => { e.preventDefault(); ask(input.value); } }, input, send));
  panel.hidden = true;

  const fab = h('button', { class: 'btn ai-fab', onclick: () => toggle(true) }, icon('sparkles'), 'Ask');

  function toggle(open) {
    panel.hidden = !open;
    fab.hidden = open;
    if (open) {
      if (log.childElementCount === 0) {
        add('bot', `Hello ${user.fullName.split(' ')[0]}. I can answer questions about the data you are allowed to see.`);
        (SUGGESTIONS[user.role] || []).forEach((s) => chips.append(h('button', { class: 'chip', onclick: () => ask(s) }, s)));
      }
      input.focus();
    }
  }

  panel.addEventListener('keydown', (e) => { if (e.key === 'Escape') toggle(false); });
  root.append(fab, panel);
  document.body.append(root);

  api.get('/api/ai/status').then((s) => {
    mode = s.mode;
    clear(sub).append(s.mode === 'local' ? 'Ask about your data' : `${s.mode === 'ollama' ? 'Local AI' : 'Claude'} · ${s.model}`);
  }).catch(() => clear(sub).append('Unavailable'));
}

export function unmountAssistant() { document.getElementById('ai-root')?.remove(); }
