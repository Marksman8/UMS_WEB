// Tiny UI toolkit: DOM builder (text is always inserted as text, never as HTML), toasts, modals, tables.

import { icon } from './icons.js';

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const [key, value] of Object.entries(attrs)) {
      if (value === undefined || value === null || value === false) continue;
      if (key === 'class') el.className = value;
      else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
      else if (key === 'value') el.value = value;
      else if (key === 'checked' || key === 'disabled' || key === 'selected' || key === 'readOnly') el[key] = Boolean(value);
      else el.setAttribute(key, value === true ? '' : value);
    }
  }
  append(el, children);
  return el;
}

export function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c === undefined || c === null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

export function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); return el; }

// ----- formatting ------------------------------------------------------------------------------
export const DAYS = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const dayName = (d) => DAYS[d] || '?';
export const num = (v, digits = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(digits));
export const pct = (v) => (v === null || v === undefined ? '-' : `${Number(v).toFixed(1)}%`);
export function fmtDate(v) {
  if (!v) return '-';
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v);
  return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}
export function fmtTime(v) {
  if (!v) return '-';
  return new Date(v).toLocaleString(undefined, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
export function bytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1048576).toFixed(1)} MB`;
}
export const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();

// ----- small components -------------------------------------------------------------------------
export const badge = (text, kind = 'muted') => h('span', { class: `badge ${kind}` }, text);

const STATE_KIND = {
  REGISTRATION_OPEN: 'info', IN_PROGRESS: 'ok', GRADING: 'warn', RESULTS_PUBLISHED: 'ok', CLOSED: 'muted',
  DRAFT: 'muted', SUBMITTED: 'warn', APPROVED: 'info', PUBLISHED: 'ok',
};
export const stateBadge = (state) => badge((state || '-').replaceAll('_', ' ').toLowerCase(), STATE_KIND[state] || 'muted');

export const spinner = () => h('div', { class: 'spinner', role: 'status', 'aria-label': 'Loading' });

export function empty(text, iconName = 'inbox') {
  return h('div', { class: 'empty' }, icon(iconName), h('div', null, text));
}

export function card(title, body, actions, { flush = false } = {}) {
  return h('section', { class: 'card' },
    title || actions ? h('div', { class: 'card-h' }, h('h2', null, title), actions ? h('div', { class: 'row' }, actions) : null) : null,
    h('div', { class: `card-b${flush ? ' flush' : ''}` }, body));
}

export function stat(label, value, hint, tone = '') {
  return h('div', { class: `card stat ${tone}` },
    h('div', { class: 'label' }, label), h('div', { class: 'value' }, value ?? '-'), hint ? h('div', { class: 'hint' }, hint) : null);
}

export function bar(percent, tone) {
  const p = Math.max(0, Math.min(100, Number(percent) || 0));
  return h('div', { class: `bar ${tone || ''}`, role: 'progressbar', 'aria-valuenow': p, 'aria-valuemin': 0, 'aria-valuemax': 100 },
    h('i', { style: `width:${p}%` }));
}

/** columns: [{ label, cell: (row) => Node|string, num?: bool, class?: string }] */
export function table(columns, rows, emptyText = 'Nothing here yet.') {
  if (!rows || rows.length === 0) return empty(emptyText);
  return h('div', { class: 'table-wrap' }, h('table', { class: 't' },
    h('thead', null, h('tr', null, columns.map((c) => h('th', { class: c.num ? 'num' : '' }, c.label)))),
    h('tbody', null, rows.map((r) => h('tr', null, columns.map((c) =>
      h('td', { class: `${c.num ? 'num' : ''} ${c.class || ''}` }, c.cell(r))))))));
}

// ----- forms --------------------------------------------------------------------------------------
export function field(label, input, hint, wide = false) {
  const id = input.id || `f${Math.random().toString(36).slice(2, 8)}`;
  input.id = id;
  return h('div', { class: `field${wide ? ' wide' : ''}` }, h('label', { for: id }, label), input, hint ? h('div', { class: 'hint' }, hint) : null);
}

export function select(name, options, value) {
  return h('select', { name }, options.map((o) => h('option', { value: o.value, selected: String(o.value) === String(value) }, o.label)));
}

/** Reads a form into an object; empty strings become undefined, number/date inputs keep their string. */
export function readForm(form) {
  const out = {};
  new FormData(form).forEach((v, k) => { out[k] = typeof v === 'string' && v.trim() === '' ? undefined : v; });
  return out;
}

// ----- feedback -----------------------------------------------------------------------------------
export function toast(message, kind = '') {
  const t = h('div', { class: `toast ${kind}`, role: kind === 'error' ? 'alert' : 'status' }, message);
  document.getElementById('toasts').append(t);
  setTimeout(() => t.remove(), kind === 'error' ? 7000 : 3800);
}

export function errorToast(e) { toast(e?.message || 'Something went wrong.', 'error'); }

/** Runs an async action on a button: disables it while pending, shows errors as toasts. */
export async function busy(button, action) {
  if (button) button.disabled = true;
  try { return await action(); } catch (e) { errorToast(e); return undefined; } finally { if (button) button.disabled = false; }
}

export function modal({ title, body, actions = [], wide = false }) {
  const previouslyFocused = document.activeElement;
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); previouslyFocused?.focus?.(); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const footer = actions.map((a) => h('button', {
    class: `btn ${a.kind || ''}`, type: 'button',
    onclick: async (ev) => {
      const btn = ev.currentTarget;
      btn.disabled = true;
      try { const keepOpen = await a.run?.(close); if (!keepOpen && a.closes !== false) close(); }
      catch (e) { errorToast(e); }
      finally { btn.disabled = false; }
    },
  }, a.label));
  const overlay = h('div', { class: 'overlay', onmousedown: (e) => { if (e.target === overlay) close(); } },
    h('div', { class: `modal${wide ? ' wide' : ''}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
      h('div', { class: 'modal-h' }, h('h2', null, title)),
      h('div', { class: 'modal-b' }, body),
      footer.length ? h('div', { class: 'modal-f' }, footer) : null));
  document.body.append(overlay);
  document.addEventListener('keydown', onKey);
  overlay.querySelector('input,select,textarea,button')?.focus();
  return close;
}

export function confirmBox(message, { confirmLabel = 'Confirm', danger = false } = {}) {
  return new Promise((resolve) => {
    modal({
      title: 'Please confirm', body: h('p', null, message),
      actions: [
        { label: 'Cancel', kind: 'ghost', run: () => resolve(false) },
        { label: confirmLabel, kind: danger ? 'danger' : '', run: () => resolve(true) },
      ],
    });
  });
}

/** Shows a spinner in `target`, awaits `loader`, then swaps in the result (or an error message). */
export async function load(target, loader) {
  clear(target).append(spinner());
  try {
    const node = await loader();
    clear(target).append(node);
  } catch (e) {
    clear(target).append(empty(e.message || 'Could not load this page.', 'x'));
  }
}
