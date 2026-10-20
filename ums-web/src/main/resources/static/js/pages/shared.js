// Pieces used by more than one role: notices, the term stage stepper, grade badges.

import { api } from '../api.js';
import { h, card, badge, empty, fmtDate, load, table } from '../ui.js';

export async function noticesCard(limit = 5, title = 'Notices') {
  const items = await api.get(`/api/notices?limit=${limit}`);
  const body = items.length === 0
    ? empty('No notices right now.', 'bell')
    : h('div', null, items.map((n) => h('div', { class: 'notice' },
      h('div', { class: 'row spread' }, h('b', null, n.title), badge(n.audience.toLowerCase(), n.audience === 'ALL' ? 'ok' : 'info')),
      h('div', { class: 'small muted' }, `${n.author} · ${fmtDate(n.createdAt)}`),
      h('p', { style: 'margin-top:4px' }, n.body))));
  return card(title, body);
}

export function noticesPage() {
  const host = h('div');
  load(host, () => noticesCard(50, 'All notices'));
  return host;
}

const STAGES = ['REGISTRATION_OPEN', 'IN_PROGRESS', 'GRADING', 'RESULTS_PUBLISHED', 'CLOSED'];
const STAGE_LABEL = { REGISTRATION_OPEN: 'Registration', IN_PROGRESS: 'Teaching', GRADING: 'Grading', RESULTS_PUBLISHED: 'Results', CLOSED: 'Closed' };

export function stepper(state) {
  const at = STAGES.indexOf(state);
  return h('div', { class: 'stepper' }, STAGES.map((s, i) => h('div', { class: `step ${i < at ? 'done' : i === at ? 'now' : ''}` },
    h('span', { class: 'dot' }, i < at ? '✓' : String(i + 1)), STAGE_LABEL[s])));
}

export function stageLabel(state) { return STAGE_LABEL[state] || state; }

export function gradeBadge(grade) {
  const kind = ['O', 'A+', 'A'].includes(grade) ? 'ok' : ['B+', 'B'].includes(grade) ? 'info' : grade === 'F' ? 'bad' : 'warn';
  return badge(grade, kind);
}

export function atRiskTable(rows) {
  return table([
    { label: 'Student', cell: (r) => h('div', null, h('b', null, r.student), h('div', { class: 'small muted' }, r.rollNo)) },
    { label: 'Course', cell: (r) => r.courseCode },
    { label: 'Attendance', cell: (r) => `${Number(r.percent).toFixed(1)}% of ${r.held}` },
  ], rows, 'Everyone is above the attendance minimum.');
}
