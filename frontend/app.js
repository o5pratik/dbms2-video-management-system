const $ = (id) => document.getElementById(id);
const state = { csrfToken: null, username: null, page: 'dashboard', editingId: null, pendingDelete: null, videos: [] };
const names = { dashboard: 'Dashboard', add: 'Add Video', records: 'Video Records', reports: 'Reports' };
const number = (value) => Number(value || 0).toLocaleString();
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const dateText = (value) => value ? String(value).slice(0,10) : '—';
let toastTimer;

function toast(message, error = false) {
  const el = $('toast'); el.textContent = message; el.className = 'toast' + (error ? ' error' : '');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.add('hidden'), 4200);
}
function showAuthView(view) {
  $('signInView').classList.toggle('hidden', view !== 'login');
  $('registerView').classList.toggle('hidden', view !== 'register');
  $('loginError').textContent = ''; $('registerError').textContent = '';
}
function showLogin() {
  state.csrfToken = null; state.username = null;
  $('appShell').classList.add('hidden'); $('loginScreen').classList.remove('hidden');
  showAuthView('login');
  $('sidebar').classList.remove('open');
}
function showApp() {
  $('loginScreen').classList.add('hidden'); $('appShell').classList.remove('hidden');
  $('currentUsername').textContent = state.username || '';
  $('userAvatar').textContent = (state.username || 'A').charAt(0).toUpperCase();
  loadStatus(); navigate('dashboard');
}
async function api(path, options = {}) {
  const headers = { 'Accept': 'application/json', ...(options.body ? {'Content-Type':'application/json'} : {}), ...options.headers };
  if (state.csrfToken && options.method && options.method !== 'GET') headers['X-CSRF-Token'] = state.csrfToken;
  let response;
  try { response = await fetch(path, {credentials:'same-origin', ...options, headers}); }
  catch { throw new Error('Server unavailable. Check that the application is running.'); }
  let data = null;
  try { data = await response.json(); } catch {}
  if (!response.ok) {
    if (response.status === 401 && path !== '/api/auth/login' && path !== '/api/auth/register') showLogin();
    throw new Error(data?.error || `Request failed (${response.status}).`);
  }
  return data;
}
function setBusy(button, busy, text) {
  if (!button) return; if (busy) { button.dataset.oldText = button.innerHTML; button.disabled = true; button.textContent = text; }
  else { button.disabled = false; if (button.dataset.oldText) button.innerHTML = button.dataset.oldText; }
}
async function loadStatus() {
  try {
    const data = await api('/api/database/status');
    $('sideMode').textContent = data.mode;
    $('sideStatus').textContent = data.reachable ? 'Connected' : 'Disconnected';
    $('sideStatusDot').className = 'status-dot ' + (data.reachable ? 'good' : 'bad');
    if (!data.reachable) toast(data.message, true);
  } catch { $('sideStatus').textContent = 'Unavailable'; $('sideStatusDot').className = 'status-dot bad'; }
}
function navigate(page) {
  if (!names[page]) return;
  state.page = page;
  document.querySelectorAll('.page').forEach(el => el.classList.toggle('hidden', el.id !== `page-${page}`));
  document.querySelectorAll('.nav-link').forEach(el => el.classList.toggle('active', el.dataset.page === page));
  $('breadcrumbName').textContent = names[page]; $('sidebar').classList.remove('open');
  if (page === 'dashboard') return loadDashboard();
  if (page === 'records') return loadVideos();
  if (page === 'reports') return loadReports();
  if (page === 'add') return loadCategories();
}
async function loadDashboard() {
  $('recentList').textContent = 'Loading videos…';
  try {
    const data = await api('/api/dashboard/stats');
    $('statVideos').textContent = number(data.totalVideos);
    $('statCategories').textContent = number(data.totalCategories);
    $('statViews').textContent = number(data.totalViews);
    $('recentList').innerHTML = data.recentVideos.length ? data.recentVideos.map(v => `<div class="recent-item"><div class="recent-thumb">▶</div><div><strong>${escapeHtml(v.title)}</strong><small>${escapeHtml(v.categoryName)} · ${dateText(v.uploadDate)}</small></div><span class="recent-views">◉ ${number(v.views)}</span></div>`).join('') : '<div class="empty-cell">No videos yet. Add your first record.</div>';
  } catch (e) { $('recentList').textContent = e.message; }
}
async function loadCategories() {
  try {
    const categories = await api('/api/categories');
    const current = $('videoCategory').value;
    $('videoCategory').innerHTML = '<option value="">Select a category</option>' + categories.map(c => `<option value="${Number(c.categoryId)}">${escapeHtml(c.categoryName)}</option>`).join('');
    $('videoCategory').value = current;
  } catch (e) { $('formError').textContent = e.message; }
}
function videoRow(v) {
  return `<tr><td>#${v.videoId}</td><td><strong>${escapeHtml(v.title)}</strong></td><td><span class="category-badge">${escapeHtml(v.categoryName)}</span></td><td>${v.duration} min</td><td>${escapeHtml(v.creatorName)}</td><td>${dateText(v.uploadDate)}</td><td>${number(v.views)}</td><td><div class="action-buttons"><button class="table-action" data-action="view" data-id="${v.videoId}">View</button><button class="table-action" data-action="edit" data-id="${v.videoId}">Edit</button><button class="table-action delete" data-action="delete" data-id="${v.videoId}">Delete</button></div></td></tr>`;
}
async function loadVideos() {
  $('videoRows').innerHTML = '<tr><td colspan="8" class="empty-cell">Loading videos…</td></tr>';
  const q = $('searchInput').value.trim(); const field = $('searchField').value;
  try {
    const videos = await api(q ? `/api/videos/search?field=${encodeURIComponent(field)}&q=${encodeURIComponent(q)}` : '/api/videos');
    state.videos = videos;
    $('resultCount').textContent = `${videos.length} result${videos.length === 1 ? '' : 's'}`;
    $('videoRows').innerHTML = videos.length ? videos.map(videoRow).join('') : '<tr><td colspan="8" class="empty-cell">No matching videos found. Try another search.</td></tr>';
  } catch (e) { $('videoRows').innerHTML = `<tr><td colspan="8" class="empty-cell">${escapeHtml(e.message)}</td></tr>`; }
}
function resetVideoForm() {
  const now = new Date();
  $('videoForm').reset(); $('videoViews').value = '0';
  $('videoDate').value = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0,10);
  $('videoIdDisplay').value = ''; $('formError').textContent = ''; state.editingId = null;
  $('formTitle').textContent = 'Add a new video'; $('saveVideoBtn').innerHTML = 'Save video <span>→</span>';
  $('cancelEditBtn').classList.add('hidden');
}
function readVideoForm() {
  const form = $('videoForm');
  if (!form.reportValidity()) throw new Error('Please complete all required fields with valid values.');
  const duration = Number($('videoDuration').value), views = Number($('videoViews').value);
  if (!Number.isInteger(duration) || duration < 1 || !Number.isSafeInteger(views) || views < 0) throw new Error('Duration must be a positive whole number and views cannot be negative.');
  return {title:$('videoTitle').value.trim(),categoryId:Number($('videoCategory').value),duration,creatorName:$('videoCreator').value.trim(),uploadDate:$('videoDate').value,views,description:$('videoDescription').value.trim()};
}
async function saveVideo(event) {
  event.preventDefault(); $('formError').textContent = '';
  let payload; try { payload = readVideoForm(); } catch (e) { $('formError').textContent = e.message; return; }
  if (!payload.title || !payload.creatorName) { $('formError').textContent = 'Title and creator name are required.'; return; }
  const id = state.editingId, button = $('saveVideoBtn'); setBusy(button, true, 'Saving…');
  try {
    const saved = await api(id ? `/api/videos/${id}` : '/api/videos', {method:id?'PUT':'POST', body:JSON.stringify(payload)});
    toast(id ? `Video #${saved.videoId} updated successfully.` : `Video #${saved.videoId} saved successfully.`);
    resetVideoForm(); $('searchInput').value = ''; navigate('records');
  } catch (e) { $('formError').textContent = e.message; }
  finally { setBusy(button, false); }
}
function showView(v) {
  $('modalTitle').textContent = v.title;
  $('modalBody').innerHTML = `<div class="detail-grid"><div><span>Video ID</span><strong>#${v.videoId}</strong></div><div><span>Category</span><strong>${escapeHtml(v.categoryName)}</strong></div><div><span>Duration</span><strong>${v.duration} minutes</strong></div><div><span>Creator</span><strong>${escapeHtml(v.creatorName)}</strong></div><div><span>Upload date</span><strong>${dateText(v.uploadDate)}</strong></div><div><span>Views</span><strong>${number(v.views)}</strong></div></div><div class="detail-description">${escapeHtml(v.description || 'No description provided.')}</div>`;
  $('viewModal').classList.remove('hidden');
}
async function editVideo(v) {
  resetVideoForm(); await navigate('add');
  state.editingId = v.videoId; $('formTitle').textContent = 'Edit video'; $('saveVideoBtn').innerHTML = 'Update video <span>→</span>';
  $('cancelEditBtn').classList.remove('hidden'); $('videoIdDisplay').value = `#${v.videoId}`;
  $('videoTitle').value = v.title; $('videoCategory').value = String(v.categoryId); $('videoDuration').value = v.duration;
  $('videoCreator').value = v.creatorName; $('videoDate').value = dateText(v.uploadDate);
  $('videoViews').value = v.views; $('videoDescription').value = v.description || '';
  window.scrollTo({top:0,behavior:'smooth'});
}
function askDelete(v) {
  state.pendingDelete = v; $('deleteDescription').textContent = `“${v.title}” will be permanently removed from the database.`;
  $('deleteModal').classList.remove('hidden');
}
async function confirmDelete() {
  const v = state.pendingDelete; if (!v) return;
  const button = $('confirmDeleteBtn'); setBusy(button, true, 'Deleting…');
  try { await api(`/api/videos/${v.videoId}`, {method:'DELETE'}); $('deleteModal').classList.add('hidden'); state.pendingDelete = null; toast('Video deleted successfully.'); await loadVideos(); }
  catch (e) { toast(e.message, true); }
  finally { setBusy(button, false); }
}
function simpleRows(items, columns, emptyText) {
  return items.length ? items.map(item => `<tr>${columns.map(c => `<td>${c(item)}</td>`).join('')}</tr>`).join('') : `<tr><td colspan="${columns.length}" class="empty-cell">${escapeHtml(emptyText)}</td></tr>`;
}
async function loadReports() {
  for (const id of ['categoryReportRows','popularReportRows','groupedReportRows','dateReportRows','allReportRows']) $(id).innerHTML = '<tr><td class="empty-cell">Loading…</td></tr>';
  const params = new URLSearchParams(); if ($('reportFrom').value) params.set('from',$('reportFrom').value); if ($('reportTo').value) params.set('to',$('reportTo').value);
  try {
    const data = await api('/api/reports' + (params.size ? '?' + params : ''));
    $('reportTotal').textContent = number(data.totalVideos);
    $('categoryReportRows').innerHTML = simpleRows(data.byCategory,[r=>`<strong>${escapeHtml(r.categoryName)}</strong>`,r=>number(r.videoCount),r=>number(r.totalViews)],'No categories found.');
    $('popularReportRows').innerHTML = simpleRows(data.mostViewed,[r=>`<strong>${escapeHtml(r.title)}</strong>`,r=>escapeHtml(r.categoryName),r=>number(r.views)],'No videos found.');
    $('groupedReportRows').innerHTML = simpleRows(data.groupedVideos,[r=>`<span class="category-badge">${escapeHtml(r.categoryName)}</span>`,r=>`#${r.videoId}`,r=>`<strong>${escapeHtml(r.title)}</strong>`,r=>escapeHtml(r.creatorName),r=>number(r.views)],'No videos found.');
    $('dateReportRows').innerHTML = simpleRows(data.dateRange,[r=>`#${r.videoId}`,r=>`<strong>${escapeHtml(r.title)}</strong>`,r=>escapeHtml(r.categoryName),r=>dateText(r.uploadDate),r=>number(r.views)],'No videos in this date range.');
    $('allReportRows').innerHTML = simpleRows(data.allVideos,[r=>`#${r.videoId}`,r=>`<strong>${escapeHtml(r.title)}</strong>`,r=>escapeHtml(r.categoryName),r=>escapeHtml(r.creatorName),r=>dateText(r.uploadDate),r=>number(r.views)],'No videos found.');
  } catch (e) { toast(e.message, true); for (const id of ['categoryReportRows','popularReportRows','groupedReportRows','dateReportRows','allReportRows']) $(id).innerHTML = `<tr><td class="empty-cell">${escapeHtml(e.message)}</td></tr>`; }
}

$('loginForm').addEventListener('submit', async event => {
  event.preventDefault(); $('loginError').textContent = '';
  const username = $('username').value.trim(), password = $('password').value;
  if (!username || !password) { $('loginError').textContent = 'Enter both username and password.'; return; }
  const button = $('loginForm').querySelector('button[type="submit"]'); setBusy(button,true,'Signing in…');
  try { const data = await api('/api/auth/login',{method:'POST',body:JSON.stringify({username,password})}); state.csrfToken = data.csrfToken; state.username = data.username; $('password').value = ''; showApp(); }
  catch (e) { $('loginError').textContent = e.message; }
  finally { setBusy(button,false); }
});
$('registerForm').addEventListener('submit', async event => {
  event.preventDefault(); $('registerError').textContent = '';
  const form = $('registerForm');
  if (!form.reportValidity()) return;
  const username = $('registerUsername').value.trim();
  const password = $('registerPassword').value;
  if (password !== $('registerConfirm').value) { $('registerError').textContent = 'Passwords do not match.'; return; }
  const button = form.querySelector('button[type="submit"]'); setBusy(button, true, 'Creating account…');
  try {
    const data = await api('/api/auth/register', {method:'POST',body:JSON.stringify({username,password})});
    state.csrfToken = data.csrfToken; state.username = data.username;
    form.reset(); showApp(); toast(`Welcome, ${data.username}!`);
  } catch (e) { $('registerError').textContent = e.message; }
  finally { setBusy(button, false); }
});
$('showRegisterBtn').addEventListener('click', () => { showAuthView('register'); $('registerUsername').focus(); });
$('showSignInBtn').addEventListener('click', () => { showAuthView('login'); $('username').focus(); });
$('togglePassword').addEventListener('click', () => { const input=$('password'); input.type=input.type==='password'?'text':'password'; $('togglePassword').textContent=input.type==='password'?'Show':'Hide'; });
document.querySelectorAll('[data-page]').forEach(b => b.addEventListener('click', () => navigate(b.dataset.page)));
document.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => { resetVideoForm(); navigate(b.dataset.go); }));
$('menuBtn').addEventListener('click', () => $('sidebar').classList.toggle('open'));
$('logoutBtn').addEventListener('click', async () => { try { await api('/api/auth/logout',{method:'POST'}); } catch {} showLogin(); toast('Logged out successfully.'); });
$('videoForm').addEventListener('submit', saveVideo); $('resetVideoBtn').addEventListener('click', resetVideoForm);
$('cancelEditBtn').addEventListener('click', () => { resetVideoForm(); navigate('records'); });
$('searchBtn').addEventListener('click', loadVideos); $('searchInput').addEventListener('keydown', e => { if (e.key==='Enter') loadVideos(); });
$('clearSearchBtn').addEventListener('click', () => { $('searchInput').value=''; loadVideos(); });
$('videoRows').addEventListener('click', event => { const button=event.target.closest('[data-action]'); if (!button) return; const v=state.videos.find(v=>v.videoId===Number(button.dataset.id)); if (!v) return; if (button.dataset.action==='view') showView(v); if (button.dataset.action==='edit') editVideo(v); if (button.dataset.action==='delete') askDelete(v); });
$('closeModalBtn').addEventListener('click', () => $('viewModal').classList.add('hidden'));
$('modalDoneBtn').addEventListener('click', () => $('viewModal').classList.add('hidden'));
$('cancelDeleteBtn').addEventListener('click', () => { $('deleteModal').classList.add('hidden'); state.pendingDelete=null; });
$('confirmDeleteBtn').addEventListener('click', confirmDelete); $('applyDatesBtn').addEventListener('click', loadReports);
document.addEventListener('keydown', e => { if (e.key==='Escape') { $('viewModal').classList.add('hidden'); $('deleteModal').classList.add('hidden'); } });
$('todayLabel').textContent = new Date().toLocaleDateString(undefined,{day:'numeric',month:'long',year:'numeric'});
resetVideoForm();
api('/api/auth/me').then(data => { state.csrfToken=data.csrfToken; state.username=data.username; showApp(); }).catch(() => showLogin());
