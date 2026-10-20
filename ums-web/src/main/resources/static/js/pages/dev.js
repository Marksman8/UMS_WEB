// Developer area: overview, SQL console (with schema browser, history, CSV export, Ask-AI), live feed, schema.

import { api } from '../api.js';
import {
  h, card, stat, table, badge, empty, bytes, busy, toast, load, clear, errorToast, fmtTime,
} from '../ui.js';
import { icon } from '../icons.js';

const HISTORY_KEY = 'ums.sql.history';
const HISTORY_MAX = 20;

const readHistory = () => {
  try { const v = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); return Array.isArray(v) ? v.slice(0, HISTORY_MAX) : []; } catch { return []; }
};
const writeHistory = (list) => { try { localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, HISTORY_MAX))); } catch { /* storage blocked: history is a convenience only */ } };

// ---------------------------------------------------------------------------------------------- overview
async function overview() {
  const [health, stats, schema] = await Promise.all([api.get('/api/dev/health'), api.get('/api/dev/stats'), api.get('/api/dev/schema')]);
  const up = (s) => `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
  return h('div', { class: 'stack' },
    h('div', { class: 'grid cols-4' },
      stat('Database', health.databaseUp ? 'Up' : 'Down', `${health.databaseLatencyMs} ms round trip`, health.databaseUp ? '' : 'warn'),
      stat('Schema version', health.migrationVersion || '-', `Flyway · schema ${stats.schema}`),
      stat('Connections', `${stats.connections} / ${stats.maxConnections}`, `Pool ${health.poolActive} busy · ${health.poolIdle} idle`),
      stat('Uptime', up(health.uptimeSeconds), `Heap ${health.heapUsedMb} / ${health.heapMaxMb} MB`)),
    h('div', { class: 'grid cols-2' },
      card('Database', h('div', { class: 'stack' },
        h('div', null, h('b', null, 'Server '), h('span', { class: 'mono small' }, stats.version)),
        h('div', null, h('b', null, 'Size '), bytes(stats.sizeBytes)),
        h('div', null, h('b', null, 'Runtime '), h('span', { class: 'mono small' }, health.java)),
        h('p', { class: 'small muted' }, 'Open pgAdmin and connect to the same database to see these tables directly: Servers → ums_web → Schemas → ums → Tables.'))),
      card('Tables', table([
        { label: 'Table', cell: (t) => h('b', null, t.name) },
        { label: 'Kind', cell: (t) => badge(t.kind.toLowerCase(), t.kind === 'VIEW' ? 'info' : 'muted') },
        { label: 'Rows', cell: (t) => `${t.rows}${t.rowsCapped ? '+' : ''}`, num: true },
      ], schema), h('a', { class: 'btn sm ghost', href: '#/dev/console' }, icon('terminal'), 'Open console'), { flush: true })));
}

// ---------------------------------------------------------------------------------------------- console
function resultTable(res) {
  const cell = (v) => (v === null || v === undefined ? h('span', { class: 'null' }, 'NULL') : typeof v === 'object' ? JSON.stringify(v) : String(v));
  const numeric = (type) => /int|numeric|decimal|float|double|real/i.test(type);
  return h('div', { class: 'result-wrap' }, h('table', { class: 't' },
    h('thead', null, h('tr', null, res.columns.map((c) => h('th', { class: numeric(c.type) ? 'num' : '', title: c.type }, c.name)))),
    h('tbody', null, res.rows.map((r) => h('tr', null, r.map((v, i) => h('td', { class: numeric(res.columns[i].type) ? 'num' : '' }, cell(v))))))));
}

function toCsv(res) {
  const esc = (v) => {
    if (v === null || v === undefined) return '';
    let s = typeof v === 'object' ? JSON.stringify(v) : String(v);
    if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`; // neutralise spreadsheet formula injection
    return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
  };
  return [res.columns.map((c) => esc(c.name)).join(','), ...res.rows.map((r) => r.map(esc).join(','))].join('\r\n');
}

function download(name, text) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const a = h('a', { href: url, download: name });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function consolePage(ctx) {
  const schema = await api.get('/api/dev/schema');
  let history = readHistory();
  let last = null;

  const editor = h('textarea', { class: 'editor', spellcheck: 'false', 'aria-label': 'SQL editor', maxlength: 5000, placeholder: 'SELECT * FROM student LIMIT 20;' });
  editor.value = ctx.query.get('sql') || 'SELECT s.roll_no, u.full_name, p.code AS programme\nFROM student s\nJOIN app_user_public u ON u.id = s.user_id\nJOIN programme p ON p.id = s.programme_id\nORDER BY s.roll_no\nLIMIT 20;';
  const output = h('div');
  const status = h('span', { class: 'small muted' }, 'Read-only · 200 rows max · 5 s limit · Ctrl+Enter to run');
  const historyHost = h('div');
  const askBox = h('input', { style: 'flex:1', type: 'text', maxlength: 500, placeholder: 'Ask in plain English, e.g. "students below 75% attendance"', 'aria-label': 'Ask AI for a query' });
  const askNote = h('div', { class: 'small muted', style: 'margin-top:6px' });

  const drawHistory = () => {
    clear(historyHost);
    if (history.length === 0) return;
    historyHost.append(card('Recent queries', h('div', null, history.map((q) => h('div', {
      class: 'notice', style: 'cursor:pointer', title: 'Click to load', onclick: () => { editor.value = q; editor.focus(); },
    }, h('span', { class: 'mono small' }, q.length > 140 ? `${q.slice(0, 140)}…` : q)))), h('button', {
      class: 'btn sm ghost', onclick: () => { history = []; writeHistory(history); drawHistory(); },
    }, 'Clear'), { flush: false }));
  };

  const run = async (btn) => {
    const sql = editor.value.trim();
    if (!sql) { toast('Type a query first', 'error'); return; }
    await busy(btn, async () => {
      clear(output).append(h('div', { class: 'row', style: 'padding:16px' }, h('div', { class: 'spinner' }), 'Running…'));
      try {
        const res = await api.post('/api/dev/query', { sql });
        last = res;
        history = [sql, ...history.filter((q) => q !== sql)].slice(0, HISTORY_MAX);
        writeHistory(history); drawHistory();
        clear(output).append(h('section', { class: 'card' },
          h('div', { class: 'result-meta' },
            h('b', null, `${res.rowCount} row${res.rowCount === 1 ? '' : 's'}`),
            res.truncated ? badge('truncated at the row limit', 'warn') : null,
            h('span', null, `${res.millis} ms`), h('span', { style: 'flex:1' }),
            res.rowCount > 0 ? h('button', { class: 'btn sm ghost', onclick: () => download('query-result.csv', toCsv(last)) }, icon('download'), 'CSV') : null),
          res.rowCount === 0 ? empty('The query ran and returned no rows.') : resultTable(res)));
      } catch (e) {
        clear(output).append(h('div', { class: 'callout bad', role: 'alert' }, h('b', null, 'The query did not run. '), e.message));
      }
    });
  };

  const runBtn = h('button', { class: 'btn', onclick: (e) => run(e.currentTarget) }, icon('play'), 'Run');
  editor.addEventListener('keydown', (e) => { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); run(runBtn); } });

  const ask = (btn) => {
    const question = askBox.value.trim();
    if (!question) return;
    busy(btn, async () => {
      const started = Date.now();
      clear(askNote).append('Asking the AI model… a local model can take up to a minute the first time.');
      const tick = setInterval(() => { askNote.textContent = `Asking the AI model… ${Math.round((Date.now() - started) / 1000)} s`; }, 1000);
      let s;
      try { s = await api.post('/api/dev/ask', { question }); } finally { clearInterval(tick); }
      editor.value = s.sql;
      clear(askNote).append(`${s.explanation} `, badge(s.source === 'templates' ? 'built-in templates' : s.source === 'ollama' ? 'local model' : 'Claude', s.source === 'templates' ? 'muted' : 'ok'), s.note ? ` ${s.note}` : '');
      editor.focus();
    });
  };
  askBox.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); ask(askBtn); } });
  const askBtn = h('button', { class: 'btn ghost', onclick: (e) => ask(e.currentTarget) }, icon('sparkles'), 'Suggest');

  const browser = h('div', { class: 'schema-list' }, schema.map((t) => h('details', null,
    h('summary', null, h('span', null, t.name), h('span', { class: 'faint small' }, `${t.rows}${t.rowsCapped ? '+' : ''}`)),
    h('ul', null,
      h('li', { onclick: () => { editor.value = `SELECT * FROM ${t.name} LIMIT 50;`; run(runBtn); }, style: 'color:var(--green-deep);font-weight:600' }, 'Preview rows →'),
      t.columns.map((c) => h('li', { title: c.nullable ? 'nullable' : 'not null', onclick: () => { editor.setRangeText(c.name, editor.selectionStart, editor.selectionEnd, 'end'); editor.focus(); } },
        h('span', null, c.primaryKey ? '🔑 ' : '', c.name), h('span', { class: 'faint' }, c.type)))))));

  drawHistory();
  if (ctx.query.get('sql')) run(runBtn);
  return h('div', { class: 'sql-layout' },
    card('Tables', browser, null, { flush: true }),
    h('div', { class: 'stack' },
      card('SQL console', h('div', { class: 'stack' },
        h('div', { class: 'row' }, askBox, askBtn), askNote, editor,
        h('div', { class: 'row spread' }, status, runBtn)),
      null),
      output, historyHost));
}

// ---------------------------------------------------------------------------------------------- live feed
const FEED_MAX = 200;

async function livePage() {
  let rows = await api.get('/api/dev/activity/recent?limit=50'); // newest first
  let lastId = rows.length ? rows[0].id : 0;
  let timer = null;
  let paused = false;
  const host = h('div');
  const dot = h('span', { class: 'live-dot' });
  const pauseBtn = h('button', { class: 'btn sm ghost', onclick: () => { paused = !paused; dot.className = paused ? 'live-dot off' : 'live-dot'; pauseBtn.textContent = paused ? 'Resume' : 'Pause'; } }, 'Pause');

  const draw = () => clear(host).append(table([
    { label: 'When', cell: (a) => fmtTime(a.at), class: 'nowrap small' },
    { label: 'Who', cell: (a) => h('div', null, a.actor, a.role ? h('span', { class: 'small muted' }, ` · ${a.role.toLowerCase()}`) : null) },
    { label: 'Action', cell: (a) => badge(a.action.replaceAll('_', ' ').toLowerCase(), a.ok ? 'ok' : 'bad') },
    { label: 'Target', cell: (a) => h('span', { class: 'small muted' }, [a.entity, a.entityId, a.detail].filter(Boolean).join(' · ')) },
  ], rows, 'Waiting for activity. Sign in as someone else in another window and watch this fill.'));

  const tick = async () => {
    if (!document.getElementById('live-feed-root')) { clearInterval(timer); return; } // page left: stop polling
    if (paused) return;
    try {
      const fresh = await api.get(`/api/dev/activity?after=${lastId}&limit=100`); // oldest first
      if (fresh.length > 0) {
        lastId = fresh[fresh.length - 1].id;
        rows = fresh.slice().reverse().concat(rows).slice(0, FEED_MAX);
        draw();
      }
    } catch (e) { if (e.status === 401 || e.status === 403) clearInterval(timer); }
  };
  timer = setInterval(tick, 1500);
  draw();
  return h('div', { id: 'live-feed-root' }, card('Live activity', host, h('div', { class: 'row' }, dot, h('span', { class: 'small muted' }, 'updates every 1.5 s'), pauseBtn), { flush: true }));
}

// ---------------------------------------------------------------------------------------------- schema
async function schemaPage() {
  const tables = await api.get('/api/dev/schema');
  return h('div', { class: 'stack' }, tables.map((t) => card(
    h('span', null, t.name, ' ', badge(t.kind.toLowerCase(), t.kind === 'VIEW' ? 'info' : 'muted')),
    table([
      { label: 'Column', cell: (c) => h('span', null, c.primaryKey ? '🔑 ' : '', h('b', null, c.name)), class: 'mono' },
      { label: 'Type', cell: (c) => c.type, class: 'mono small' },
      { label: 'Null', cell: (c) => (c.nullable ? 'yes' : 'no'), class: 'small' },
    ], t.columns),
    h('div', { class: 'row' }, h('span', { class: 'small muted' }, `${t.rows}${t.rowsCapped ? '+' : ''} rows`),
      h('a', { class: 'btn sm ghost', href: `#/dev/console?sql=${encodeURIComponent(`SELECT * FROM ${t.name} LIMIT 50;`)}` }, 'Browse')),
    { flush: true })));
}

export const devPages = [
  { path: 'overview', label: 'Overview', icon: 'home', title: 'System overview', render: overview },
  { path: 'console', label: 'SQL console', icon: 'terminal', title: 'SQL console', render: consolePage },
  { path: 'live', label: 'Live feed', icon: 'activity', title: 'Live activity', render: livePage },
  { path: 'schema', label: 'Schema', icon: 'database', title: 'Database schema', render: schemaPage },
];
