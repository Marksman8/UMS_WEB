// Application shell: boot, sign-in screen, sidebar navigation, hash router.
// Route format: #/<role>/<page>?<query>. Each page module exports [{path,label,icon,title,render(ctx)} | {section}].

import { api } from './api.js';
import { h, clear, load, busy, initials, toast, errorToast } from './ui.js';
import { icon } from './icons.js';
import { studentPages } from './pages/student.js';
import { facultyPages } from './pages/faculty.js';
import { adminPages } from './pages/admin.js';
import { devPages } from './pages/dev.js';
import { mountAssistant, unmountAssistant } from './ai.js';

const ROLE_PAGES = { STUDENT: studentPages, FACULTY: facultyPages, ADMIN: adminPages, DEV: devPages };
const ROLE_LABEL = { STUDENT: 'Student', FACULTY: 'Faculty', ADMIN: 'Administrator', DEV: 'Developer' };
const DEMO_ACCOUNTS = [
  ['stu.anu', 'Student'], ['fac.rao', 'Faculty'], ['admin', 'Admin'], ['dev', 'Developer'],
];

const app = document.getElementById('app');
let user = null;

// ---------------------------------------------------------------------------------------------- login
function showLogin(message) {
  user = null;
  unmountAssistant();
  const username = h('input', { type: 'text', name: 'username', autocomplete: 'username', required: true, maxlength: 40, autofocus: true });
  const password = h('input', { type: 'password', name: 'password', autocomplete: 'current-password', required: true, maxlength: 100 });
  const error = h('div', { class: 'callout bad', role: 'alert', hidden: true });
  if (message) { error.textContent = message; error.hidden = false; }

  const form = h('form', {
    onsubmit: (e) => {
      e.preventDefault();
      busy(e.submitter, async () => {
        error.hidden = true;
        try {
          await api.post('/api/auth/login', { username: username.value.trim(), password: password.value });
          await boot();
        } catch (err) {
          error.textContent = err.message; error.hidden = false; password.value = ''; password.focus();
        }
      });
    },
  },
  h('div', { class: 'field' }, h('label', { for: 'u' }, 'Username'), (username.id = 'u', username)),
  h('div', { class: 'field', style: 'margin-top:12px' }, h('label', { for: 'p' }, 'Password'), (password.id = 'p', password)),
  h('div', { style: 'margin-top:14px' }, error),
  h('button', { class: 'btn', type: 'submit', style: 'width:100%;margin-top:14px;justify-content:center' }, 'Sign in'));

  clear(app).append(h('div', { class: 'login' },
    h('div', { class: 'login-brand' },
      h('div', { class: 'crest' }, 'U'),
      h('div', null, h('h1', null, 'One calm place for the whole university.'),
        h('p', { style: 'margin-top:14px' }, 'Registration, attendance, marks and results — with seats that never overbook and grades that always add up.')),
      h('div', { class: 'small', style: 'color:#cfe3cb' }, 'UMS · University Management System')),
    h('div', { class: 'login-panel' }, h('div', { class: 'login-card' },
      h('h2', null, 'Welcome back'), h('p', { class: 'muted' }, 'Sign in to continue.'),
      form,
      h('div', { class: 'chips', 'aria-label': 'Demo accounts' }, DEMO_ACCOUNTS.map(([u, label]) => h('button', {
        class: 'chip', type: 'button', onclick: () => { username.value = u; password.value = 'DemoPass@2026'; password.focus(); },
      }, label))),
      h('div', { class: 'demo-note' }, 'Demo data is loaded. Pick a role above to fill the form (password DemoPass@2026). Remove the demo data and passwords before real use.')))));
}

// ---------------------------------------------------------------------------------------------- shell
function parseRoute() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [pathPart, query = ''] = raw.split('?');
  const [role, page] = pathPart.split('/');
  return { role, page, query: new URLSearchParams(query) };
}

function showShell() {
  const pages = ROLE_PAGES[user.role];
  const firstPage = pages.find((p) => p.path);

  const nav = h('nav', { class: 'nav', 'aria-label': 'Main' }, pages.map((p) => (p.section
    ? h('div', { class: 'section' }, p.section)
    : h('a', { href: `#/${user.role.toLowerCase()}/${p.path}`, 'data-page': p.path }, icon(p.icon), p.label))));

  const title = h('h1', null, '');
  const sub = h('div', { class: 'sub' }, '');
  const pageHost = h('div', { id: 'page' });

  clear(app).append(h('div', { class: 'shell' },
    h('aside', { class: 'sidebar' },
      h('div', { class: 'brand' }, h('div', { class: 'crest' }, 'U'), h('div', null, h('b', null, 'UMS'), h('span', null, ROLE_LABEL[user.role]))),
      nav,
      h('div', { class: 'me' }, h('div', { class: 'avatar' }, initials(user.fullName)),
        h('div', { class: 'who' }, h('b', null, user.fullName), h('span', null, user.username)),
        h('button', { class: 'signout', title: 'Sign out', 'aria-label': 'Sign out', onclick: signOut }, icon('logout'), 'Sign out'))),
    h('main', { class: 'main' },
      h('div', { class: 'topbar' }, h('div', null, title, sub)),
      h('div', { class: 'content' }, pageHost))));

  let token = 0;
  const route = () => {
    const r = parseRoute();
    if (r.role !== user.role.toLowerCase()) { location.hash = `#/${user.role.toLowerCase()}/${firstPage.path}`; return; }
    const page = pages.find((p) => p.path === r.page) || firstPage;
    nav.querySelectorAll('a').forEach((a) => a.classList.toggle('active', a.dataset.page === page.path));
    title.textContent = page.title;
    document.title = `${page.title} · UMS`;
    const mine = ++token;
    const ctx = { user, query: r.query, navigate: (hash) => { location.hash = hash; } };
    load(pageHost, async () => {
      const node = await page.render(ctx);
      if (mine !== token) throw new Error('Superseded'); // a newer navigation won; its result is shown instead
      return node;
    }).then(() => { if (mine === token) window.scrollTo({ top: 0 }); });
  };

  window.onhashchange = route;
  route();
  mountAssistant(user);
}

async function signOut() {
  try { await api.post('/api/auth/logout'); } catch { /* the session may already be gone */ }
  window.onhashchange = null;
  location.hash = '';
  showLogin();
}

// ---------------------------------------------------------------------------------------------- boot
async function boot() {
  try {
    user = await api.get('/api/auth/me');
    showShell();
  } catch (e) {
    if (e.status === 401 || e.status === 403) showLogin();
    else showLogin(e.message);
  }
}

window.addEventListener('ums:signed-out', () => {
  if (user) { window.onhashchange = null; showLogin('Your session ended. Please sign in again.'); }
});

boot();
