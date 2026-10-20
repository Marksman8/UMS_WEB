// Student area: dashboard, registration, timetable, attendance, results, notices.

import { api } from '../api.js';
import {
  h, card, stat, table, badge, bar, empty, num, pct, dayName, busy, toast, confirmBox, load, clear, errorToast,
} from '../ui.js';
import { noticesCard, noticesPage, gradeBadge } from './shared.js';

async function dashboard() {
  const [s, courses, noticeCard] = await Promise.all([
    api.get('/api/student/summary'), api.get('/api/student/courses'), noticesCard(4),
  ]);
  const lowAttendance = s.attendancePercent !== null && Number(s.attendancePercent) < s.attendanceThreshold;
  return h('div', { class: 'stack' },
    h('div', { class: 'grid cols-4' },
      stat('CGPA', num(s.cgpa), s.lastSgpa ? `Last term SGPA ${num(s.lastSgpa)}` : 'No published results yet'),
      stat('Credits this term', `${s.currentCredits} / ${s.creditLimit}`, 'Registered credits'),
      stat('Attendance', pct(s.attendancePercent), s.coursesBelowThreshold > 0
        ? `${s.coursesBelowThreshold} course(s) below ${s.attendanceThreshold}%` : `Minimum is ${s.attendanceThreshold}%`, lowAttendance ? 'warn' : ''),
      stat('Open sections', String(s.openSections), s.openSections > 0 ? 'Registration is open' : 'Registration is closed')),
    h('div', { class: 'grid cols-2' },
      card('My courses', table([
        { label: 'Course', cell: (c) => h('div', null, h('b', null, c.courseCode), h('div', { class: 'small muted' }, c.title)) },
        { label: 'When', cell: (c) => `${dayName(c.dayOfWeek)} ${c.start}–${c.end}`, class: 'nowrap' },
        { label: 'Room', cell: (c) => c.room },
        { label: 'Faculty', cell: (c) => c.faculty },
      ], courses, 'You have not registered for any course yet. Open the Register page.'), null, { flush: courses.length > 0 }),
      noticeCard));
}

async function register() {
  const [s, offers, mine] = await Promise.all([
    api.get('/api/student/summary'), api.get('/api/student/sections/open'), api.get('/api/student/courses'),
  ]);
  const enrolmentBySection = new Map(mine.map((c) => [c.sectionId, c.enrolmentId]));
  const host = h('div', { class: 'stack' });
  const refresh = () => load(host.parentElement, register);

  const act = (btn, fn, okMessage) => busy(btn, async () => { await fn(); toast(okMessage); refresh(); });

  const seatBadge = (o) => (o.seatsLeft <= 0 ? badge('Full', 'bad') : badge(`${o.seatsLeft} of ${o.capacity} left`, o.seatsLeft <= 5 ? 'warn' : 'ok'));

  host.append(
    h('div', { class: 'callout' }, `You have ${s.currentCredits} of ${s.creditLimit} credits. Seats are given in the order requests arrive; a clash, a missing prerequisite or the credit limit will stop a request with a clear message.`),
    card('Sections open for registration', table([
      { label: 'Course', cell: (o) => h('div', null, h('b', null, `${o.courseCode} · ${o.title}`), h('div', { class: 'small muted' }, `${o.credits} credits · ${o.termName}`)) },
      { label: 'Section', cell: (o) => h('div', null, `${o.code} · ${o.faculty}`) },
      { label: 'Schedule', cell: (o) => h('div', null, `${dayName(o.dayOfWeek)} ${o.start}–${o.end}`, h('div', { class: 'small muted' }, o.room)), class: 'nowrap' },
      { label: 'Seats', cell: seatBadge },
      {
        label: '', cell: (o) => {
          const actions = h('div', { class: 'actions' });
          if (o.registered) {
            actions.append(badge('Registered', 'ok'), h('button', {
              class: 'btn sm danger ghost', onclick: async (e) => {
                if (await confirmBox(`Drop ${o.courseCode}?`, { confirmLabel: 'Drop course', danger: true })) {
                  act(e.currentTarget, () => api.del(`/api/student/registrations/${enrolmentBySection.get(o.sectionId)}`), `${o.courseCode} dropped`);
                }
              },
            }, 'Drop'));
          } else if (o.missingPrereqs) {
            actions.append(badge(`Needs ${o.missingPrereqs}`, 'bad'));
          } else {
            actions.append(h('button', {
              class: 'btn sm', disabled: o.seatsLeft <= 0,
              onclick: (e) => act(e.currentTarget, () => api.post('/api/student/registrations', { sectionId: o.sectionId }), `Registered for ${o.courseCode}`),
            }, 'Register'));
          }
          return actions;
        },
      },
    ], offers, 'Registration is not open for any section right now.'), null, { flush: offers.length > 0 }));
  return host;
}

async function timetable() {
  const courses = await api.get('/api/student/courses');
  if (courses.length === 0) return card('Weekly timetable', empty('Register for courses to see your timetable.', 'calendar'));
  const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
  const firstHour = Math.min(8, Math.floor(Math.min(...courses.map((c) => toMin(c.start))) / 60));
  const lastHour = Math.max(17, Math.ceil(Math.max(...courses.map((c) => toMin(c.end))) / 60));
  const grid = h('div', { class: 'tt' }, h('div', { class: 'head' }, ''));
  for (let d = 1; d <= 6; d++) grid.append(h('div', { class: 'head' }, dayName(d)));
  for (let hour = firstHour; hour < lastHour; hour++) {
    grid.append(h('div', { class: 'time' }, `${String(hour).padStart(2, '0')}:00`));
    for (let d = 1; d <= 6; d++) {
      const cell = h('div', { class: 'cell' });
      courses.filter((c) => c.dayOfWeek === d && Math.floor(toMin(c.start) / 60) === hour).forEach((c) => {
        const top = ((toMin(c.start) % 60) / 60) * 56;
        const height = ((toMin(c.end) - toMin(c.start)) / 60) * 56 - 4;
        cell.append(h('div', { class: 'slot', style: `top:${top}px;height:${height}px`, title: `${c.title} · ${c.faculty}` },
          h('b', null, c.courseCode), h('span', null, `${c.start}–${c.end} · ${c.room}`)));
      });
      grid.append(cell);
    }
  }
  return card(`Weekly timetable · ${courses[0].termName}`, grid);
}

async function attendance() {
  const [s, lines] = await Promise.all([api.get('/api/student/summary'), api.get('/api/student/attendance')]);
  const below = lines.filter((l) => l.belowThreshold);
  return h('div', { class: 'stack' },
    below.length > 0
      ? h('div', { class: 'callout warn' }, `Attendance is below ${s.attendanceThreshold}% in ${below.map((b) => b.code).join(', ')}. You may be barred from the end-term exam.`)
      : null,
    card('Attendance by course', table([
      { label: 'Course', cell: (l) => h('div', null, h('b', null, l.code), h('div', { class: 'small muted' }, `${l.title} · ${l.term}`)) },
      { label: 'Classes', cell: (l) => `${l.present} / ${l.held}`, num: true },
      { label: 'Attendance', cell: (l) => h('div', { class: 'row' }, h('div', { style: 'width:140px' }, bar(l.percent, l.belowThreshold ? 'bad' : '')), pct(l.percent)) },
      { label: 'Status', cell: (l) => (l.belowThreshold ? badge('Below minimum', 'bad') : badge('OK', 'ok')) },
    ], lines, 'No attendance has been recorded yet.'), null, { flush: lines.length > 0 }));
}

async function results() {
  const t = await api.get('/api/student/transcript');
  if (t.terms.length === 0) return card('Results', empty('Results appear here once a term is published.', 'award'));
  return h('div', { class: 'stack' },
    h('div', { class: 'grid cols-4' }, stat('CGPA', num(t.cgpa), 'All attempts count'), stat('Credits graded', String(t.credits), `${t.terms.length} term(s)`)),
    t.terms.slice().reverse().map((term) => card(`${term.term}`, table([
      { label: 'Course', cell: (c) => h('div', null, h('b', null, c.code), h('div', { class: 'small muted' }, c.title)) },
      { label: 'Credits', cell: (c) => c.credits, num: true },
      { label: 'Total /100', cell: (c) => num(c.total, 1), num: true },
      { label: 'Grade', cell: (c) => gradeBadge(c.grade) },
      { label: 'Points', cell: (c) => c.points, num: true },
    ], term.courses), badge(`SGPA ${num(term.sgpa)}`, 'ok'), { flush: true })));
}

export const studentPages = [
  { path: 'dashboard', label: 'Dashboard', icon: 'home', title: 'My dashboard', render: dashboard },
  { path: 'register', label: 'Register', icon: 'checkSquare', title: 'Course registration', render: register },
  { path: 'timetable', label: 'Timetable', icon: 'calendar', title: 'Timetable', render: timetable },
  { path: 'attendance', label: 'Attendance', icon: 'clock', title: 'Attendance', render: attendance },
  { path: 'results', label: 'Results', icon: 'award', title: 'Results & transcript', render: results },
  { path: 'notices', label: 'Notices', icon: 'bell', title: 'Notices', render: async () => noticesPage() },
];
