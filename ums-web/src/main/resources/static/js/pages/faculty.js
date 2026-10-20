// Faculty area: dashboard, my sections, attendance, marks, notices.

import { api } from '../api.js';
import {
  h, card, stat, table, badge, bar, empty, num, dayName, busy, toast, confirmBox, modal, load, field, stateBadge, fmtDate,
} from '../ui.js';
import { noticesCard, noticesPage, atRiskTable } from './shared.js';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

function sectionPicker(sections, selectedId, onPick) {
  const sel = h('select', { 'aria-label': 'Section', onchange: (e) => onPick(e.target.value) },
    sections.map((s) => h('option', { value: s.id, selected: s.id === selectedId },
      `${s.courseCode} ${s.code} · ${s.title} (${s.termName})`)));
  return h('div', { class: 'row' }, h('span', { class: 'muted' }, 'Section'), h('div', { style: 'min-width:340px' }, sel));
}

function pickSection(sections, query, preferState) {
  const asked = Number(query.get('section'));
  return sections.find((s) => s.id === asked)
    || sections.find((s) => s.termState === preferState)
    || sections[0];
}

async function dashboard() {
  const [sum, sections, notices] = await Promise.all([api.get('/api/faculty/summary'), api.get('/api/faculty/sections'), noticesCard(4)]);
  return h('div', { class: 'stack' },
    h('div', { class: 'grid cols-4' },
      stat('Sections', String(sum.sections), 'In active terms'),
      stat('Students', String(sum.students), 'Across your sections'),
      stat('Draft sheets', String(sum.draftSheets), 'Marks still to submit', sum.draftSheets > 0 ? 'warn' : ''),
      stat('Awaiting approval', String(sum.submittedSheets), 'Submitted to admin')),
    h('div', { class: 'grid cols-2' },
      card('My classes', table([
        { label: 'Course', cell: (s) => h('div', null, h('b', null, s.courseCode), h('div', { class: 'small muted' }, s.title)) },
        { label: 'When', cell: (s) => `${dayName(s.dayOfWeek)} ${s.start}–${s.end}`, class: 'nowrap' },
        { label: 'Students', cell: (s) => `${s.enrolled}/${s.capacity}`, num: true },
        { label: 'Term', cell: (s) => stateBadge(s.termState) },
      ], sections, 'You have no sections yet.'), null, { flush: sections.length > 0 }),
      card('Students needing attention', atRiskTable(sum.atRisk), null, { flush: sum.atRisk.length > 0 })),
    notices);
}

async function sectionsPage(ctx) {
  const sections = await api.get('/api/faculty/sections');
  if (sections.length === 0) return card('My sections', empty('No sections are assigned to you in an active term.', 'book'));
  return h('div', { class: 'grid cols-3' }, sections.map((s) => h('section', { class: 'card' },
    h('div', { class: 'card-h' }, h('div', null, h('h3', null, `${s.courseCode} · ${s.title}`),
      h('div', { class: 'small muted' }, `Section ${s.code} · ${s.credits} credits · ${s.termName}`)), stateBadge(s.termState)),
    h('div', { class: 'card-b stack' },
      h('div', { class: 'small' }, `${dayName(s.dayOfWeek)} ${s.start}–${s.end} · ${s.room}`),
      h('div', null, h('div', { class: 'row spread small' }, h('span', null, 'Enrolled'), h('b', null, `${s.enrolled} / ${s.capacity}`)), bar((s.enrolled / s.capacity) * 100)),
      h('div', { class: 'row small' }, 'Mark sheet', stateBadge(s.sheetState), h('span', { class: 'muted' }, `${s.sessionsHeld} class(es) held`)),
      h('div', { class: 'row' },
        h('button', { class: 'btn sm ghost', onclick: () => showRoster(s) }, 'Roster'),
        h('a', { class: 'btn sm ghost', href: `#/faculty/attendance?section=${s.id}` }, 'Attendance'),
        h('a', { class: 'btn sm', href: `#/faculty/marks?section=${s.id}` }, 'Marks'))))));
}

async function showRoster(s) {
  const rows = await api.get(`/api/faculty/sections/${s.id}/roster`);
  modal({
    title: `Roster · ${s.courseCode} ${s.code}`, wide: true,
    body: table([
      { label: 'Roll no', cell: (r) => r.rollNo, class: 'mono' },
      { label: 'Student', cell: (r) => r.name },
      { label: 'Email', cell: (r) => r.email || '-' },
    ], rows, 'Nobody is enrolled yet.'),
    actions: [{ label: 'Close', kind: 'ghost' }],
  });
}

async function attendancePage(ctx) {
  const sections = await api.get('/api/faculty/sections');
  if (sections.length === 0) return card('Attendance', empty('No sections to take attendance for.', 'clock'));
  const section = pickSection(sections, ctx.query, 'IN_PROGRESS');
  const sessions = await api.get(`/api/faculty/sections/${section.id}/sessions`);
  const open = section.termState === 'IN_PROGRESS';
  const date = h('input', { type: 'date', name: 'heldOn', value: todayIso(), max: todayIso() });
  const topic = h('input', { type: 'text', name: 'topic', maxlength: 160, placeholder: 'e.g. Binary trees' });
  const refresh = () => load(document.getElementById('page'), () => attendancePage(ctx));

  return h('div', { class: 'stack' },
    sectionPicker(sections, section.id, (id) => ctx.navigate(`#/faculty/attendance?section=${id}`)),
    open ? null : h('div', { class: 'callout warn' }, `Attendance can be taken while the term is in progress. ${section.termName} is currently "${section.termState.replaceAll('_', ' ').toLowerCase()}".`),
    open ? card('Start a class session', h('form', {
      class: 'form-grid', onsubmit: (e) => {
        e.preventDefault();
        busy(e.submitter, async () => {
          const created = await api.post(`/api/faculty/sections/${section.id}/sessions`, { heldOn: date.value || null, topic: topic.value || null });
          await sheetModal(created.id, `${section.courseCode} · ${fmtDate(created.heldOn)}`, refresh);
        });
      },
    }, field('Date', date), field('Topic (optional)', topic), h('div', { class: 'field', style: 'justify-content:flex-end' }, h('button', { class: 'btn', type: 'submit' }, 'Take attendance')))) : null,
    card('Sessions held', table([
      { label: 'Date', cell: (s) => fmtDate(s.heldOn), class: 'nowrap' },
      { label: 'Topic', cell: (s) => s.topic || h('span', { class: 'faint' }, '-') },
      { label: 'Present', cell: (s) => (s.total === 0 ? h('span', { class: 'faint' }, 'not marked') : h('div', { class: 'row' }, h('div', { style: 'width:110px' }, bar((s.present / s.total) * 100)), `${s.present}/${s.total}`)) },
      { label: '', cell: (s) => h('div', { class: 'actions' }, h('button', { class: 'btn sm ghost', onclick: () => sheetModal(s.id, `${section.courseCode} · ${fmtDate(s.heldOn)}`, refresh) }, open ? 'Open / edit' : 'View')) },
    ], sessions, 'No class has been recorded yet.'), null, { flush: sessions.length > 0 }));
}

async function sheetModal(sessionId, title, onSaved) {
  const rows = await api.get(`/api/faculty/sessions/${sessionId}/attendance`);
  const boxes = rows.map((r) => h('input', { type: 'checkbox', checked: r.present !== false, 'aria-label': `${r.name} present` }));
  const setAll = (v) => boxes.forEach((b) => { b.checked = v; });
  modal({
    title: `Attendance · ${title}`, wide: true,
    body: h('div', { class: 'stack' },
      h('div', { class: 'row' }, h('button', { class: 'btn sm ghost', type: 'button', onclick: () => setAll(true) }, 'All present'),
        h('button', { class: 'btn sm ghost', type: 'button', onclick: () => setAll(false) }, 'All absent'),
        h('span', { class: 'muted small' }, 'Tick = present')),
      table([
        { label: 'Roll no', cell: (r) => r.rollNo, class: 'mono' },
        { label: 'Student', cell: (r) => r.name },
        { label: 'Present', cell: (r) => boxes[rows.indexOf(r)] },
      ], rows, 'Nobody is enrolled in this section.')),
    actions: [
      { label: 'Cancel', kind: 'ghost' },
      {
        label: 'Save attendance', run: async () => {
          await api.put(`/api/faculty/sessions/${sessionId}/attendance`, rows.map((r, i) => ({ enrolmentId: r.enrolmentId, present: boxes[i].checked })));
          toast('Attendance saved');
          onSaved?.();
        },
      },
    ],
  });
}

async function marksPage(ctx) {
  const sections = await api.get('/api/faculty/sections');
  if (sections.length === 0) return card('Marks', empty('No sections to enter marks for.', 'file'));
  const section = pickSection(sections, ctx.query, 'IN_PROGRESS');
  const grid = await api.get(`/api/faculty/sections/${section.id}/marks`);
  const refresh = () => load(document.getElementById('page'), () => marksPage(ctx));

  const inputs = new Map(); // `${enrolmentId}:${componentId}` -> input
  const totals = new Map(); // enrolmentId -> td content node
  const original = (row, c) => row.marks[c.id];

  const recompute = (row) => {
    let sum = 0;
    grid.components.forEach((c) => {
      const v = inputs.get(`${row.enrolmentId}:${c.id}`).value;
      if (v !== '') sum += (Number(v) / c.maxMarks) * c.weight;
    });
    totals.get(row.enrolmentId).textContent = sum.toFixed(2);
  };

  const body = table([
    { label: 'Roll no', cell: (r) => r.rollNo, class: 'mono' },
    { label: 'Student', cell: (r) => r.name },
    ...grid.components.map((c) => ({
      label: `${c.name} (/${Number(c.maxMarks)})`, num: true,
      cell: (r) => {
        const input = h('input', {
          type: 'number', min: 0, max: c.maxMarks, step: 0.5, disabled: !grid.editable,
          value: original(r, c) ?? '', 'aria-label': `${c.name} for ${r.name}`, oninput: () => recompute(r),
        });
        inputs.set(`${r.enrolmentId}:${c.id}`, input);
        return input;
      },
    })),
    {
      label: 'Total /100', num: true,
      cell: (r) => { const b = h('b', null, num(r.total)); totals.set(r.enrolmentId, b); return b; },
    },
  ], grid.rows, 'Nobody is enrolled in this section.');

  const collectChanges = () => {
    const changes = [];
    grid.rows.forEach((r) => grid.components.forEach((c) => {
      const raw = inputs.get(`${r.enrolmentId}:${c.id}`).value;
      const before = original(r, c);
      if (raw === '' && before !== undefined) changes.push({ enrolmentId: r.enrolmentId, componentId: c.id, value: null });
      else if (raw !== '' && Number(raw) !== Number(before)) changes.push({ enrolmentId: r.enrolmentId, componentId: c.id, value: Number(raw) });
    }));
    return changes;
  };
  const save = async () => {
    const changes = collectChanges();
    if (changes.length === 0) return 0;
    await api.put(`/api/faculty/sections/${section.id}/marks`, changes);
    return changes.length;
  };

  const why = grid.editable ? null
    : section.termState !== 'IN_PROGRESS' && section.termState !== 'GRADING'
      ? `Marks open once the term is in progress (now: ${section.termState.replaceAll('_', ' ').toLowerCase()}).`
      : `This mark sheet is ${grid.sheetState.toLowerCase()} and can no longer be edited.`;

  return h('div', { class: 'stack' },
    sectionPicker(sections, section.id, (id) => ctx.navigate(`#/faculty/marks?section=${id}`)),
    h('div', { class: 'row spread' },
      h('div', { class: 'row' }, h('h2', null, `${grid.courseCode} · ${grid.title}`), stateBadge(grid.sheetState)),
      grid.editable ? h('div', { class: 'row' },
        h('button', { class: 'btn ghost', onclick: (e) => busy(e.currentTarget, async () => { const n = await save(); toast(n ? `${n} change(s) saved` : 'Nothing to save'); refresh(); }) }, 'Save draft'),
        h('button', {
          class: 'btn', onclick: async (e) => {
            if (!(await confirmBox('Submit these marks to the administrator for approval? You will not be able to edit them unless they are returned.', { confirmLabel: 'Submit marks' }))) return;
            busy(e.currentTarget, async () => { await save(); await api.post(`/api/faculty/sections/${section.id}/submit`); toast('Marks submitted for approval'); refresh(); });
          },
        }, 'Submit for approval')) : null),
    why ? h('div', { class: 'callout warn' }, why) : h('div', { class: 'callout' }, 'Every student needs every mark before you can submit. The total is weighted out of 100.'),
    card(null, body, null, { flush: true }));
}

export const facultyPages = [
  { path: 'dashboard', label: 'Dashboard', icon: 'home', title: 'Faculty dashboard', render: dashboard },
  { path: 'sections', label: 'My sections', icon: 'book', title: 'My sections', render: sectionsPage },
  { path: 'attendance', label: 'Attendance', icon: 'checkSquare', title: 'Attendance', render: attendancePage },
  { path: 'marks', label: 'Marks', icon: 'file', title: 'Marks entry', render: marksPage },
  { path: 'notices', label: 'Notices', icon: 'bell', title: 'Notices', render: async () => noticesPage() },
];
