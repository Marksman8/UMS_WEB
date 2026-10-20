// Admin area: dashboard, people, academic setup, terms & sections, approvals, notices, activity.

import { api, qs } from '../api.js';
import {
  h, card, stat, table, badge, bar, empty, num, dayName, busy, toast, confirmBox, modal, load, field, select, readForm,
  stateBadge, fmtDate, fmtTime, clear, errorToast,
} from '../ui.js';
import { stepper, stageLabel, atRiskTable } from './shared.js';
import { icon } from '../icons.js';

const page = () => document.getElementById('page');

function secretModal(title, lead, secret) {
  modal({
    title, body: h('div', { class: 'stack' }, h('p', null, lead),
      h('div', { class: 'callout' }, h('span', { class: 'mono', style: 'font-size:18px;user-select:all' }, secret)),
      h('p', { class: 'small muted' }, 'Shown once. Copy it now and share it securely; the user can change it later.')),
    actions: [{ label: 'Done' }],
  });
}

// ---------------------------------------------------------------------------------------------- dashboard
async function dashboard() {
  const [s, activity] = await Promise.all([api.get('/api/admin/summary'), api.get('/api/admin/activity?limit=8')]);
  const fill = s.seatsTotal === 0 ? 0 : Math.round((s.seatsFilled / s.seatsTotal) * 100);
  return h('div', { class: 'stack' },
    h('div', { class: 'grid cols-4' },
      stat('Students', String(s.usersByRole.STUDENT ?? 0), 'Active accounts'),
      stat('Faculty', String(s.usersByRole.FACULTY ?? 0), 'Active accounts'),
      stat('Awaiting approval', String(s.pendingApprovals), 'Submitted mark sheets', s.pendingApprovals > 0 ? 'warn' : ''),
      stat('Seats taken', `${fill}%`, `${s.seatsFilled} of ${s.seatsTotal} in ${s.openSections} open section(s)`)),
    s.terms.length > 0 ? card('Terms', h('div', { class: 'stack' }, s.terms.slice(0, 2).map((t) =>
      h('div', { class: 'row spread' }, h('b', null, t.name), stepper(t.state))))) : null,
    h('div', { class: 'grid cols-2' },
      card('Students needing attention', atRiskTable(s.atRisk), null, { flush: s.atRisk.length > 0 }),
      card('Latest activity', activityTable(activity), h('a', { class: 'btn sm ghost', href: '#/admin/activity' }, 'See all'), { flush: true })));
}

function activityTable(rows) {
  return table([
    { label: 'When', cell: (a) => fmtTime(a.at), class: 'nowrap small' },
    { label: 'Who', cell: (a) => a.actor },
    { label: 'Action', cell: (a) => badge(a.action.replaceAll('_', ' ').toLowerCase(), a.ok ? 'ok' : 'bad') },
    { label: 'Detail', cell: (a) => h('span', { class: 'small muted' }, a.detail || '') },
  ], rows, 'No activity yet.');
}

// ---------------------------------------------------------------------------------------------- people
async function usersPage() {
  const [programmes, departments] = await Promise.all([api.get('/api/admin/programmes'), api.get('/api/admin/departments')]);
  const state = { q: '', role: '', rows: [], next: null };
  const results = h('div');
  const more = h('div', { class: 'row', style: 'justify-content:center;margin-top:12px' });

  const search = h('input', { type: 'text', placeholder: 'Search name, username or roll number', 'aria-label': 'Search users', style: 'min-width:260px' });
  const role = select('role', [{ value: '', label: 'All roles' }, { value: 'STUDENT', label: 'Students' }, { value: 'FACULTY', label: 'Faculty' }, { value: 'ADMIN', label: 'Admins' }, { value: 'DEV', label: 'Developers' }], '');

  const draw = () => {
    clear(results).append(card(null, table([
      { label: 'Person', cell: (u) => h('div', null, h('b', null, u.fullName), h('div', { class: 'small muted' }, `${u.username}${u.email ? ` · ${u.email}` : ''}`)) },
      { label: 'Role', cell: (u) => badge(u.role.toLowerCase(), u.role === 'STUDENT' ? 'info' : u.role === 'FACULTY' ? 'ok' : 'muted') },
      { label: 'Detail', cell: (u) => u.detail || '-', class: 'mono' },
      { label: 'Status', cell: (u) => (!u.active ? badge('disabled', 'bad') : u.locked ? badge('locked', 'warn') : badge('active', 'ok')) },
      {
        label: '', cell: (u) => h('div', { class: 'actions' },
          h('button', { class: 'btn sm ghost', title: 'Reset password', onclick: () => resetPassword(u) }, icon('key'), 'Reset'),
          h('button', { class: `btn sm ${u.active ? 'danger ' : ''}ghost`, onclick: () => toggleActive(u) }, u.active ? 'Disable' : 'Enable')),
      },
    ], state.rows, 'No one matches that search.'), null, { flush: state.rows.length > 0 }));
    clear(more);
    if (state.next) more.append(h('button', { class: 'btn ghost', onclick: (e) => busy(e.currentTarget, () => fetchPage(false)) }, 'Load more'));
  };

  const fetchPage = async (reset) => {
    if (reset) { state.rows = []; state.next = null; }
    const p = await api.get(`/api/admin/users${qs({ q: state.q, role: state.role, after: state.next, limit: 25 })}`);
    state.rows = state.rows.concat(p.items);
    state.next = p.next;
    draw();
  };

  const toggleActive = async (u) => {
    if (!(await confirmBox(`${u.active ? 'Disable' : 'Enable'} ${u.fullName}?`, { confirmLabel: u.active ? 'Disable' : 'Enable', danger: u.active }))) return;
    await busy(null, async () => { await api.post(`/api/admin/users/${u.id}/active`, { active: !u.active }); toast(`${u.fullName} ${u.active ? 'disabled' : 'enabled'}`); await fetchPage(true); });
  };
  const resetPassword = async (u) => {
    if (!(await confirmBox(`Generate a new password for ${u.fullName}? The old one stops working immediately.`, { confirmLabel: 'Reset' }))) return;
    await busy(null, async () => { const r = await api.post(`/api/admin/users/${u.id}/password`, {}); secretModal('New password', `New password for ${u.username}:`, r.password); await fetchPage(true); });
  };

  let timer;
  search.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => { state.q = search.value.trim(); busy(null, () => fetchPage(true)); }, 300); });
  role.addEventListener('change', () => { state.role = role.value; busy(null, () => fetchPage(true)); });

  await fetchPage(true);
  return h('div', { class: 'stack' },
    h('div', { class: 'row spread' }, h('div', { class: 'row' }, search, role),
      h('div', { class: 'row' },
        h('button', { class: 'btn ghost', onclick: () => importModal(() => fetchPage(true)) }, icon('upload'), 'Import CSV'),
        h('button', { class: 'btn', onclick: () => addUserModal(programmes, departments, () => fetchPage(true)) }, icon('plus'), 'Add person'))),
    results, more);
}

function addUserModal(programmes, departments, onDone) {
  const role = select('role', [{ value: 'STUDENT', label: 'Student' }, { value: 'FACULTY', label: 'Faculty' }, { value: 'ADMIN', label: 'Administrator' }], 'STUDENT');
  const studentFields = h('div', { class: 'form-grid wide' },
    field('Programme', select('programmeId', programmes.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` })))),
    field('Admission year', h('input', { type: 'number', name: 'admissionYear', min: 2000, max: 2100, value: new Date().getFullYear() })),
    field('Semester', h('input', { type: 'number', name: 'semester', min: 1, max: 12, value: 1 })),
    field('Roll number', h('input', { type: 'text', name: 'rollNo', maxlength: 20 }), 'Leave empty to generate'));
  const facultyFields = h('div', { class: 'form-grid wide', style: 'display:none' },
    field('Department', select('deptId', departments.map((d) => ({ value: d.id, label: `${d.code} · ${d.name}` })))),
    field('Designation', h('input', { type: 'text', name: 'designation', maxlength: 60, placeholder: 'Assistant Professor' })));
  const form = h('form', { class: 'form-grid', autocomplete: 'off' },
    field('Role', role),
    field('Full name', h('input', { type: 'text', name: 'fullName', required: true, maxlength: 120 })),
    field('Username', h('input', { type: 'text', name: 'username', required: true, pattern: '[A-Za-z0-9._-]{3,40}', title: '3-40 letters, digits, dot, dash or underscore' })),
    field('Email', h('input', { type: 'email', name: 'email', maxlength: 160 })),
    field('Password', h('input', { type: 'password', name: 'password', autocomplete: 'new-password' }), 'Leave empty to generate a secure one', true),
    studentFields, facultyFields);
  const sync = () => {
    studentFields.style.display = role.value === 'STUDENT' ? '' : 'none';
    facultyFields.style.display = role.value === 'FACULTY' ? '' : 'none';
    studentFields.querySelectorAll('input,select').forEach((i) => { i.disabled = role.value !== 'STUDENT'; });
    facultyFields.querySelectorAll('input,select').forEach((i) => { i.disabled = role.value !== 'FACULTY'; });
  };
  role.addEventListener('change', sync);
  sync();
  modal({
    title: 'Add a person', body: form,
    actions: [
      { label: 'Cancel', kind: 'ghost' },
      {
        label: 'Create account', run: async () => {
          if (!form.reportValidity()) return true;
          const v = readForm(form);
          const payload = {
            username: v.username, password: v.password, fullName: v.fullName, email: v.email, role: v.role,
            programmeId: v.programmeId ? Number(v.programmeId) : undefined, admissionYear: v.admissionYear ? Number(v.admissionYear) : undefined,
            semester: v.semester ? Number(v.semester) : undefined, rollNo: v.rollNo, deptId: v.deptId ? Number(v.deptId) : undefined, designation: v.designation,
          };
          const created = await api.post('/api/admin/users', payload);
          toast(`${created.username} created`);
          onDone();
          if (created.generatedPassword) setTimeout(() => secretModal('Account created', `Password for ${created.username}${created.rollNo ? ` (roll ${created.rollNo})` : ''}:`, created.generatedPassword), 50);
          return false;
        },
      },
    ],
  });
}

function importModal(onDone) {
  const area = h('textarea', { rows: 8, class: 'mono', placeholder: 'STUDENT,anu.k,Anu Krishnan,anu@uni.edu,MCA\nFACULTY,rao.s,Dr. S. Rao,rao@uni.edu,CSA' });
  const out = h('div');
  modal({
    title: 'Import people from CSV', wide: true,
    body: h('div', { class: 'stack' },
      h('p', { class: 'muted small' }, 'One person per line: role, username, full name, email, programme code (students) or department code (faculty). Passwords are generated and listed below once. Up to 500 lines.'),
      area, out),
    actions: [
      { label: 'Close', kind: 'ghost' },
      {
        label: 'Import', run: async () => {
          const r = await api.postText('/api/admin/users/import', area.value);
          clear(out).append(h('div', { class: 'callout' }, `${r.created} created, ${r.failed} failed.`), table([
            { label: 'Line', cell: (l) => l.line, num: true },
            { label: 'Username', cell: (l) => l.username },
            { label: 'Result', cell: (l) => badge(l.ok ? 'created' : 'failed', l.ok ? 'ok' : 'bad') },
            { label: 'Password / reason', cell: (l) => (l.ok ? h('span', { class: 'mono' }, l.password || '') : l.message) },
          ], r.lines));
          onDone();
          return true;
        },
      },
    ],
  });
}

// ---------------------------------------------------------------------------------------------- academic
async function academicPage() {
  const host = h('div');
  let tab = 'courses';
  const tabs = h('div', { class: 'tabs', role: 'tablist' });
  const draw = async () => {
    clear(tabs).append(...[['departments', 'Departments'], ['programmes', 'Programmes'], ['courses', 'Courses']].map(([k, label]) =>
      h('button', { class: `tab${tab === k ? ' active' : ''}`, role: 'tab', onclick: () => { tab = k; draw(); } }, label)));
    await load(host, tab === 'departments' ? departmentsTab : tab === 'programmes' ? programmesTab : coursesTab);
  };
  await draw();
  return h('div', null, tabs, host);
}

async function departmentsTab() {
  const rows = await api.get('/api/admin/departments');
  return card('Departments', table([
    { label: 'Code', cell: (d) => h('b', null, d.code) }, { label: 'Name', cell: (d) => d.name },
  ], rows), h('button', {
    class: 'btn sm', onclick: () => simpleForm('Add department', [
      field('Code', h('input', { type: 'text', name: 'code', required: true, maxlength: 10 })),
      field('Name', h('input', { type: 'text', name: 'name', required: true, maxlength: 120 })),
    ], (v) => api.post('/api/admin/departments', v), () => load(page(), academicPage)),
  }, icon('plus'), 'Add'), { flush: true });
}

async function programmesTab() {
  const [rows, depts] = await Promise.all([api.get('/api/admin/programmes'), api.get('/api/admin/departments')]);
  return card('Programmes', table([
    { label: 'Code', cell: (p) => h('b', null, p.code) }, { label: 'Name', cell: (p) => p.name },
    { label: 'Department', cell: (p) => p.deptCode }, { label: 'Semesters', cell: (p) => p.semesters, num: true },
  ], rows), h('button', {
    class: 'btn sm', onclick: () => simpleForm('Add programme', [
      field('Department', select('deptId', depts.map((d) => ({ value: d.id, label: `${d.code} · ${d.name}` })))),
      field('Code', h('input', { type: 'text', name: 'code', required: true, maxlength: 10 })),
      field('Name', h('input', { type: 'text', name: 'name', required: true, maxlength: 120 })),
      field('Semesters', h('input', { type: 'number', name: 'semesters', min: 1, max: 12, value: 4, required: true })),
    ], (v) => api.post('/api/admin/programmes', { deptId: Number(v.deptId), code: v.code, name: v.name, semesters: Number(v.semesters) }), () => load(page(), academicPage)),
  }, icon('plus'), 'Add'), { flush: true });
}

async function coursesTab() {
  const [rows, depts] = await Promise.all([api.get('/api/admin/courses'), api.get('/api/admin/departments')]);
  return card('Course catalogue', table([
    { label: 'Code', cell: (c) => h('b', null, c.code) }, { label: 'Title', cell: (c) => c.title },
    { label: 'Credits', cell: (c) => c.credits, num: true }, { label: 'Dept', cell: (c) => c.deptCode },
    { label: 'Prerequisites', cell: (c) => c.prereqs || h('span', { class: 'faint' }, 'none') },
  ], rows), h('button', {
    class: 'btn sm', onclick: () => {
      const prereq = h('select', { name: 'prereqIds', multiple: true, size: 5 }, rows.map((c) => h('option', { value: c.id }, `${c.code} · ${c.title}`)));
      simpleForm('Add course', [
        field('Code', h('input', { type: 'text', name: 'code', required: true, maxlength: 12 })),
        field('Title', h('input', { type: 'text', name: 'title', required: true, maxlength: 160 })),
        field('Credits', h('input', { type: 'number', name: 'credits', min: 1, max: 6, value: 3, required: true })),
        field('Department', select('deptId', depts.map((d) => ({ value: d.id, label: `${d.code} · ${d.name}` })))),
        field('Description', h('textarea', { name: 'description', maxlength: 400, rows: 2 }), null, true),
        field('Prerequisites', prereq, 'Hold Ctrl/Cmd to pick several', true),
      ], (v) => api.post('/api/admin/courses', {
        code: v.code, title: v.title, credits: Number(v.credits), deptId: Number(v.deptId), description: v.description,
        prereqIds: [...prereq.selectedOptions].map((o) => Number(o.value)),
      }), () => load(page(), academicPage));
    },
  }, icon('plus'), 'Add'), { flush: true });
}

/** Opens a modal form; `submit` receives the form values; `done` runs after success. */
function simpleForm(title, fields, submit, done) {
  const form = h('form', { class: 'form-grid', autocomplete: 'off' }, fields);
  modal({
    title, body: form,
    actions: [
      { label: 'Cancel', kind: 'ghost' },
      {
        label: 'Save', run: async () => {
          if (!form.reportValidity()) return true;
          await submit(readForm(form));
          toast('Saved');
          done?.();
          return false;
        },
      },
    ],
  });
}

// ---------------------------------------------------------------------------------------------- terms & sections
async function termsPage(ctx) {
  const [terms, courses, faculty] = await Promise.all([api.get('/api/admin/terms'), api.get('/api/admin/courses'), api.get('/api/admin/faculty-options')]);
  const selected = terms.find((t) => t.id === Number(ctx.query.get('term'))) || terms[0];
  const sections = selected ? await api.get(`/api/admin/sections?termId=${selected.id}`) : [];
  const refresh = () => load(page(), () => termsPage(ctx));

  const advance = async (t) => {
    const warn = t.nextState === 'RESULTS_PUBLISHED'
      ? ' This publishes every approved mark sheet, computes grades and SGPA, and cannot be undone.'
      : t.nextState === 'IN_PROGRESS' ? ' Students can no longer register or drop courses.' : '';
    if (!(await confirmBox(`Move ${t.name} to "${stageLabel(t.nextState)}"?${warn}`, { confirmLabel: `Move to ${stageLabel(t.nextState)}` }))) return;
    await busy(null, async () => { await api.post(`/api/admin/terms/${t.id}/advance`); toast(`${t.name} is now ${stageLabel(t.nextState)}`); refresh(); });
  };

  const newTerm = () => simpleForm('New term', [
    field('Name', h('input', { type: 'text', name: 'name', required: true, maxlength: 40, placeholder: 'Spring 2027' })),
    field('Starts', h('input', { type: 'date', name: 'startsOn', required: true })),
    field('Ends', h('input', { type: 'date', name: 'endsOn', required: true })),
  ], (v) => api.post('/api/admin/terms', v), refresh);

  const addSection = () => simpleForm('Add a section', [
    field('Course', select('courseId', courses.map((c) => ({ value: c.id, label: `${c.code} · ${c.title}` })))),
    field('Faculty', select('facultyId', faculty.map((f) => ({ value: f.id, label: `${f.name} (${f.deptCode})` })))),
    field('Section code', h('input', { type: 'text', name: 'code', maxlength: 10, placeholder: 'A' })),
    field('Capacity', h('input', { type: 'number', name: 'capacity', min: 1, max: 500, value: 40, required: true })),
    field('Day', select('dayOfWeek', [1, 2, 3, 4, 5, 6].map((d) => ({ value: d, label: dayName(d) })))),
    field('Room', h('input', { type: 'text', name: 'room', maxlength: 30, placeholder: 'LH-101' })),
    field('Starts', h('input', { type: 'time', name: 'start', required: true, value: '09:00' })),
    field('Ends', h('input', { type: 'time', name: 'end', required: true, value: '10:00' })),
  ], (v) => api.post('/api/admin/sections', {
    courseId: Number(v.courseId), termId: selected.id, facultyId: Number(v.facultyId), code: v.code, capacity: Number(v.capacity),
    dayOfWeek: Number(v.dayOfWeek), start: v.start, end: v.end, room: v.room,
  }), refresh);

  return h('div', { class: 'stack' },
    h('div', { class: 'row spread' }, h('p', { class: 'muted' }, 'A term moves forward one stage at a time.'),
      h('button', { class: 'btn', onclick: newTerm }, icon('plus'), 'New term')),
    h('div', { class: 'grid cols-2' }, terms.slice(0, 4).map((t) => h('section', { class: `card${selected && t.id === selected.id ? '' : ''}` },
      h('div', { class: 'card-h' }, h('div', null, h('h3', null, t.name), h('div', { class: 'small muted' }, `${fmtDate(t.startsOn)} – ${fmtDate(t.endsOn)}`)),
        h('div', { class: 'row' }, h('a', { class: 'btn sm ghost', href: `#/admin/terms?term=${t.id}` }, 'Sections'),
          t.nextState ? h('button', { class: 'btn sm', onclick: () => advance(t) }, `Move to ${stageLabel(t.nextState)}`) : badge('Finished', 'muted'))),
      h('div', { class: 'card-b' }, stepper(t.state))))),
    selected ? card(`Sections · ${selected.name}`, table([
      { label: 'Course', cell: (s) => h('div', null, h('b', null, `${s.courseCode} ${s.code}`), h('div', { class: 'small muted' }, s.courseTitle)) },
      { label: 'Faculty', cell: (s) => s.facultyName },
      { label: 'When', cell: (s) => `${dayName(s.dayOfWeek)} ${s.start}–${s.end} · ${s.room}`, class: 'nowrap' },
      { label: 'Seats', cell: (s) => h('div', { class: 'row' }, h('div', { style: 'width:80px' }, bar((s.enrolled / s.capacity) * 100)), `${s.enrolled}/${s.capacity}`) },
      { label: 'Marks', cell: (s) => stateBadge(s.sheetState) },
    ], sections, 'No sections in this term yet.'), h('button', {
      class: 'btn sm', disabled: selected.state !== 'REGISTRATION_OPEN', title: selected.state !== 'REGISTRATION_OPEN' ? 'Sections can only be added while registration is open' : '', onclick: addSection,
    }, icon('plus'), 'Add section'), { flush: sections.length > 0 }) : empty('Create a term to begin.', 'calendar'));
}

// ---------------------------------------------------------------------------------------------- approvals
async function approvalsPage() {
  const rows = await api.get('/api/admin/approvals');
  const refresh = () => load(page(), approvalsPage);
  if (rows.length === 0) return card('Mark sheet approvals', empty('All caught up — no mark sheets are waiting.', 'check'));
  return card('Mark sheets waiting for approval', table([
    { label: 'Course', cell: (r) => h('div', null, h('b', null, `${r.courseCode} ${r.section}`), h('div', { class: 'small muted' }, `${r.title} · ${r.term}`)) },
    { label: 'Faculty', cell: (r) => r.faculty },
    { label: 'Students', cell: (r) => r.enrolled, num: true },
    { label: 'Average', cell: (r) => num(r.average, 1), num: true },
    { label: 'Failing', cell: (r) => (r.failing > 0 ? badge(String(r.failing), 'warn') : badge('0', 'ok')), num: true },
    { label: 'Submitted', cell: (r) => fmtTime(r.submittedAt), class: 'small nowrap' },
    {
      label: '', cell: (r) => h('div', { class: 'actions' },
        h('button', { class: 'btn sm ghost', onclick: async (e) => { if (await confirmBox(`Send ${r.courseCode} back to ${r.faculty} for correction?`, { confirmLabel: 'Send back' })) busy(e.currentTarget, async () => { await api.post(`/api/admin/approvals/${r.sheetId}/return`); toast('Sent back to faculty'); refresh(); }); } }, 'Send back'),
        h('button', { class: 'btn sm', onclick: (e) => busy(e.currentTarget, async () => { await api.post(`/api/admin/approvals/${r.sheetId}/approve`); toast(`${r.courseCode} approved`); refresh(); }) }, 'Approve')),
    },
  ], rows), null, { flush: true });
}

// ---------------------------------------------------------------------------------------------- notices
async function noticesAdmin() {
  const items = await api.get('/api/notices?limit=50');
  const refresh = () => load(page(), noticesAdmin);
  const form = h('form', { class: 'form-grid', onsubmit: (e) => {
    e.preventDefault();
    busy(e.submitter, async () => { await api.post('/api/notices', readForm(form)); toast('Notice posted'); refresh(); });
  } },
  field('Title', h('input', { type: 'text', name: 'title', required: true, maxlength: 160 })),
  field('Audience', select('audience', [{ value: 'ALL', label: 'Everyone' }, { value: 'STUDENT', label: 'Students' }, { value: 'FACULTY', label: 'Faculty' }])),
  field('Message', h('textarea', { name: 'body', required: true, maxlength: 2000, rows: 3 }), null, true),
  h('div', { class: 'wide' }, h('button', { class: 'btn', type: 'submit' }, icon('send'), 'Post notice')));
  return h('div', { class: 'stack' }, card('Post a notice', form),
    card('Posted notices', items.length === 0 ? empty('No notices yet.', 'bell') : h('div', null, items.map((n) => h('div', { class: 'notice' },
      h('div', { class: 'row spread' }, h('div', { class: 'row' }, h('b', null, n.title), badge(n.audience.toLowerCase(), n.audience === 'ALL' ? 'ok' : 'info')),
        h('button', { class: 'icon-btn', title: 'Delete', 'aria-label': `Delete ${n.title}`, onclick: async () => { if (await confirmBox(`Delete "${n.title}"?`, { confirmLabel: 'Delete', danger: true })) busy(null, async () => { await api.del(`/api/notices/${n.id}`); toast('Deleted'); refresh(); }); } }, icon('trash'))),
      h('div', { class: 'small muted' }, `${n.author} · ${fmtDate(n.createdAt)}`), h('p', { style: 'margin-top:4px' }, n.body))))));
}

// ---------------------------------------------------------------------------------------------- activity
async function activityPage() {
  let rows = await api.get('/api/admin/activity?limit=50');
  const host = h('div');
  const more = h('div', { class: 'row', style: 'justify-content:center;margin-top:12px' });
  const draw = () => {
    clear(host).append(card('Audit trail', table([
      { label: 'When', cell: (a) => fmtTime(a.at), class: 'nowrap small' },
      { label: 'Who', cell: (a) => h('div', null, a.actor, a.role ? h('span', { class: 'small muted' }, ` · ${a.role.toLowerCase()}`) : null) },
      { label: 'Action', cell: (a) => badge(a.action.replaceAll('_', ' ').toLowerCase(), a.ok ? 'ok' : 'bad') },
      { label: 'Detail', cell: (a) => h('span', { class: 'small muted' }, [a.entity, a.entityId, a.detail].filter(Boolean).join(' · ')) },
    ], rows), null, { flush: true }));
    clear(more).append(rows.length >= 50 ? h('button', { class: 'btn ghost', onclick: (e) => busy(e.currentTarget, async () => {
      const older = await api.get(`/api/admin/activity?limit=50&before=${rows[rows.length - 1].id}`);
      rows = rows.concat(older);
      draw();
    }) }, 'Load older') : null);
  };
  draw();
  return h('div', null, host, more);
}

export const adminPages = [
  { path: 'dashboard', label: 'Dashboard', icon: 'home', title: 'Administration', render: dashboard },
  { section: 'People' },
  { path: 'users', label: 'People', icon: 'users', title: 'People & accounts', render: usersPage },
  { section: 'Academics' },
  { path: 'academic', label: 'Catalogue', icon: 'layers', title: 'Departments, programmes & courses', render: academicPage },
  { path: 'terms', label: 'Terms & sections', icon: 'calendar', title: 'Terms & sections', render: termsPage },
  { path: 'approvals', label: 'Approvals', icon: 'checkSquare', title: 'Mark sheet approvals', render: approvalsPage },
  { section: 'Communication' },
  { path: 'notices', label: 'Notices', icon: 'bell', title: 'Notices', render: noticesAdmin },
  { path: 'activity', label: 'Activity', icon: 'activity', title: 'Activity log', render: activityPage },
];
