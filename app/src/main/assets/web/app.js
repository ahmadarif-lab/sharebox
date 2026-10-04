(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const enc = encodeURIComponent;
  const join = (a, b) => (a ? a + '/' + b : b);
  const dlUrl = (p, inline) => '/api/download?path=' + enc(p) + (inline ? '&inline=1' : '');
  const extOf = (n) => (n.split('.').pop() || '').toLowerCase();

  const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function fmtSize(n) {
    n = Number(n) || 0;
    if (n < 1024) return n ? n + ' B' : '';
    const units = ['KB', 'MB', 'GB', 'TB'];
    let v = n / 1024, i = 0;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return (v >= 100 ? v.toFixed(0) : v.toFixed(1)) + ' ' + units[i];
  }

  function fmtDate(t) {
    if (!t) return '';
    const d = new Date(t);
    return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
      d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  }

  function fmtEta(sec) {
    sec = Math.max(0, Math.round(sec));
    if (sec < 60) return sec + 's';
    const m = Math.floor(sec / 60), s = sec % 60;
    if (m < 60) return m + 'm ' + s + 's';
    return Math.floor(m / 60) + 'h ' + (m % 60) + 'm';
  }

  function toast(msg) {
    let el = $('#toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.remove('show'), 2600);
  }

  async function api(url, opts) {
    const r = await fetch(url, opts);
    const text = await r.text();
    let j = null;
    try { j = JSON.parse(text); } catch (e) { /* not json */ }
    if (!r.ok) throw new Error((j && j.error) || (r.status + ' ' + r.statusText));
    return j;
  }

  // ---------- icons ----------

  const S = (p) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>';
  const I = {
    folder: S('<path d="M3 7.5A2.5 2.5 0 015.5 5h3.2l2 2.2h7.8A2.5 2.5 0 0121 9.7v7.8a2.5 2.5 0 01-2.5 2.5h-13A2.5 2.5 0 013 17.5z"/>'),
    image: S('<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><circle cx="9" cy="10" r="1.6"/><path d="M4.5 17.5l4.5-4.5 3.5 3.5 3-3 4 4"/>'),
    video: S('<rect x="3.5" y="5.5" width="17" height="13" rx="2.5"/><path d="M10.5 9.5l4 2.5-4 2.5z"/>'),
    music: S('<path d="M9 18.5V6.8l9-2v11.7"/><circle cx="6.8" cy="18.5" r="2.3"/><circle cx="15.8" cy="16.5" r="2.3"/>'),
    doc: S('<path d="M6.5 3.5h7L18 8v12.5H6.5z"/><path d="M13.5 3.5V8H18M9.5 12.5h5m-5 3.5h5"/>'),
    archive: S('<path d="M4.5 7.5l7.5-4 7.5 4v9l-7.5 4-7.5-4z"/><path d="M4.5 7.5L12 11.5l7.5-4M12 11.5V20"/>'),
    apk: S('<rect x="7" y="2.5" width="10" height="19" rx="2.6"/><path d="M10.5 18.5h3"/>'),
    file: S('<path d="M6.5 3.5h7L18 8v12.5H6.5z"/><path d="M13.5 3.5V8H18"/>'),
    download: S('<path d="M12 4.5v10m0 0l-4-4m4 4l4-4M5 19.5h14"/>'),
    dots: S('<circle cx="5.5" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.3" fill="currentColor" stroke="none"/>'),
    arrowIn: S('<path d="M12 4.5v13m0 0l5-5m-5 5l-5-5"/>'),
    arrowOut: S('<path d="M12 19.5v-13m0 0l5 5m-5-5l-5 5"/>'),
    pause: S('<path d="M9.5 5.5v13M14.5 5.5v13"/>'),
    play: S('<path d="M7.5 5.2l11 6.8-11 6.8z"/>'),
    close: S('<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>'),
    info: S('<circle cx="12" cy="12" r="8.5"/><path d="M12 11.2v5.3M12 8.2v.3"/>'),
    openFolder: S('<path d="M3 7.5A2.5 2.5 0 015.5 5h3.2l2 2.2h7.8A2.5 2.5 0 0121 9.7v7.8a2.5 2.5 0 01-2.5 2.5h-13A2.5 2.5 0 013 17.5z"/><path d="M3.6 11.5h16.8"/>'),
    emptyFolder: S('<path d="M3 7.5A2.5 2.5 0 015.5 5h3.2l2 2.2h7.8A2.5 2.5 0 0121 9.7v7.8a2.5 2.5 0 01-2.5 2.5h-13A2.5 2.5 0 013 17.5z"/>'),
    emptySearch: S('<circle cx="10.8" cy="10.8" r="6.3"/><path d="M15.5 15.5L20 20"/>'),
  };

  const IMG = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'avif', 'svg']);
  const VID = new Set(['mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', '3gp', 'webm', 'm4v', 'ts']);
  const AUD = new Set(['mp3', 'm4a', 'aac', 'wav', 'ogg', 'opus', 'flac', 'amr', 'mid']);
  const ARC = new Set(['zip', 'rar', '7z', 'tar', 'gz', 'xz', 'bz2', 'iso']);

  function iconFor(name, dir) {
    if (dir) return { cls: 'folder', svg: I.folder };
    const e = extOf(name);
    if (IMG.has(e)) return { cls: 'image', svg: I.image };
    if (VID.has(e)) return { cls: 'video', svg: I.video };
    if (AUD.has(e)) return { cls: 'music', svg: I.music };
    if (e === 'apk') return { cls: 'apk', svg: I.apk };
    if (ARC.has(e)) return { cls: 'archive', svg: I.archive };
    if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv', 'md'].includes(e)) {
      return { cls: 'doc', svg: I.doc };
    }
    return { cls: 'file', svg: I.file };
  }

  // ---------- state ----------

  const state = {
    nav: 'all',
    path: '',
    entries: [],
    view: [],
    sel: new Set(),
    q: '',
    sortDesc: false,
  };

  const CAT_TITLE = {
    all: 'All Files', recent: 'Recent', images: 'Images', videos: 'Videos',
    music: 'Music', documents: 'Documents', archives: 'Archives', others: 'Others',
  };

  // ---------- load & render ----------

  async function load() {
    beginLoad();
    try {
      let d;
      if (state.q) {
        const cat = state.nav === 'all' ? '' : state.nav;
        d = await api('/api/find?cat=' + enc(cat) + '&q=' + enc(state.q));
      } else if (state.nav === 'all') {
        d = await api('/api/list?path=' + enc(state.path));
        state.path = d.path || '';
      } else {
        d = await api('/api/find?cat=' + enc(state.nav));
      }
      const raw = d.entries || [];
      state.entries = raw
        .filter((e) => !e.name.startsWith('.'))
        .map((e) => Object.assign(e, {
          rel: e.path != null ? e.path : join(state.path, e.name),
        }));
      state.sel.clear();
      render();
    } catch (e) {
      toast(e.message);
    } finally {
      endLoad();
    }
  }

  let loadTokens = 0;

  function beginLoad() {
    loadTokens++;
    $('#loadbar').hidden = false;
  }

  function endLoad() {
    loadTokens = Math.max(0, loadTokens - 1);
    if (loadTokens === 0) $('#loadbar').hidden = true;
  }

  function sorted() {
    const list = state.entries.slice();
    list.sort((a, b) => {
      if (a.dir !== b.dir) return a.dir ? -1 : 1;
      const r = a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' });
      return state.sortDesc ? -r : r;
    });
    return list;
  }

  function render() {
    renderCrumbs();
    renderRows();
    $('#cntAll').textContent = state.nav === 'all' && !state.q ? String(state.entries.length) : '';    updateSelbar();
  }

  function renderCrumbs() {
    const el = $('#crumbs');
    if (state.q) {
      const inCat = state.nav === 'all' ? 'All Files' : CAT_TITLE[state.nav];
      el.innerHTML = '<span class="here">Search \u201C' + esc(state.q) + '\u201D in ' + esc(inCat) + '</span>';
      return;
    }
    if (state.nav !== 'all') {
      el.innerHTML = '<span class="here">' + esc(CAT_TITLE[state.nav] || state.nav) + '</span>';
      return;
    }
    const parts = state.path ? state.path.split('/') : [];
    let acc = '';
    let html = '<a href="#" data-path="">Root</a>';
    for (const p of parts) {
      acc = join(acc, p);
      html += '<span class="sep">/</span><a href="#" data-path="' + esc(acc) + '">' + esc(p) + '</a>';
    }
    el.innerHTML = html;
  }

  function renderRows() {
    const rowsEl = $('#rows');
    const list = sorted();
    state.view = list;
    lastSelIndex = -1;
    $('#empty').hidden = list.length > 0;
    $('#empty').innerHTML = state.q
      ? '<div>' + I.emptySearch + '<b>No files match your search</b><span>Try another keyword.</span></div>'
      : '<div>' + I.emptyFolder + '<b>This folder is empty</b><span>Upload files, or drag &amp; drop them here.</span></div>';

    rowsEl.innerHTML = list.map((e) => {
      const ic = iconFor(e.name, e.dir);
      const checked = state.sel.has(e.rel) ? ' checked' : '';
      const sel = state.sel.has(e.rel) ? ' sel' : '';
      const type = e.dir ? 'Folder' : (extOf(e.name).toUpperCase() || 'File');
      return '<div class="row' + (e.dir ? ' dir' : '') + sel + '" data-rel="' + esc(e.rel) + '" data-dir="' + (e.dir ? 1 : 0) + '">' +
        '<label class="check"><input type="checkbox"' + checked + '></label>' +
        '<div class="fcell">' +
        '<span class="fico ' + ic.cls + '">' + ic.svg + '</span>' +
        '<span class="fname" title="' + esc(e.name) + '">' + esc(e.name) + '</span>' +
        '</div>' +
        '<span class="dim">' + (e.dir ? '\u2014' : fmtSize(e.size)) + '</span>' +
        '<span class="dim">' + esc(type) + '</span>' +
        '<span class="dim">' + fmtDate(e.mtime) + '</span>' +
        '<span class="acts">' +
        '<button class="ico" data-act="primary" title="' + (e.dir ? 'Download as zip' : 'Download') + '">' +
        (e.dir ? I.archive : I.download) + '</button>' +
        '<button class="ico" data-act="menu" title="More">' + I.dots + '</button>' +
        '</span>' +
        '</div>';
    }).join('');
  }

  function entryByRel(rel) {
    return state.entries.find((e) => e.rel === rel) || null;
  }

  // ---------- row interactions ----------

  let lastSelIndex = -1;

  function rowEls() {
    return [...$('#rows').querySelectorAll('.row')];
  }

  /** Sinkronkan tampilan seleksi tanpa render ulang (supaya checkbox tidak ke-reset). */
  function applySelection() {
    const rows = rowEls();
    for (const row of rows) {
      const on = state.sel.has(row.dataset.rel);
      row.classList.toggle('sel', on);
      const cb = row.querySelector('input[type=checkbox]');
      if (cb) cb.checked = on;
    }
    $('#selAll').checked = rows.length > 0 && state.sel.size === rows.length;
    updateSelbar();
  }

  /** Pilih/batalkan rentang index (buat shift+klik). */
  function selectRange(fromIdx, toIdx, on) {
    const rows = rowEls();
    const a = Math.min(fromIdx, toIdx);
    const b = Math.max(fromIdx, toIdx);
    for (let i = a; i <= b; i++) {
      const row = rows[i];
      if (!row) continue;
      if (on) state.sel.add(row.dataset.rel);
      else state.sel.delete(row.dataset.rel);
    }
    lastSelIndex = toIdx;
    applySelection();
  }

  $('#rows').addEventListener('click', (e) => {
    const row = e.target.closest('.row');
    if (!row) return;
    const entry = entryByRel(row.dataset.rel);
    if (!entry) return;
    const idx = rowEls().indexOf(row);

    // checkbox: klik biasa = toggle, shift+klik = pilih rentang
    const cb = e.target.closest('input[type=checkbox]');
    if (cb) {
      if (e.shiftKey && lastSelIndex >= 0 && lastSelIndex !== idx) {
        selectRange(lastSelIndex, idx, cb.checked);
      } else {
        if (cb.checked) state.sel.add(entry.rel);
        else state.sel.delete(entry.rel);
        lastSelIndex = idx;
        applySelection();
      }
      return;
    }

    const act = e.target.closest('[data-act]');
    if (act) {
      e.stopPropagation();
      if (act.dataset.act === 'menu') {
        openMenu(act, entry);
      } else if (act.dataset.act === 'primary') {
        if (entry.dir) location.href = '/api/zip?path=' + enc(entry.rel);
        else location.href = dlUrl(entry.rel);
      }
      return;
    }

    // shift+klik di badan row juga memilih rentang
    if (e.shiftKey && lastSelIndex >= 0) {
      selectRange(lastSelIndex, idx, true);
      return;
    }

    if (e.target.closest('.fname')) {
      if (entry.dir && state.nav === 'all') {
        state.path = entry.rel;
        load();
      } else if (entry.dir) {
        toast('Open from All Files to browse folders');
      } else {
        preview(entry);
      }
    }
  });

  function updateSelbar() {
    const bar = $('#selbar');
    bar.hidden = state.sel.size === 0;
    document.body.classList.toggle('has-sel', state.sel.size > 0);
    $('#selCount').textContent = state.sel.size + ' selected';
  }

  // ---------- context menu ----------

  let menuEl = null;

  function closeMenu() {
    if (menuEl) {
      menuEl.remove();
      menuEl = null;
    }
  }

  function openMenu(anchor, entry) {
    closeMenu();
    const m = document.createElement('div');
    m.className = 'menu';
    const items = [];
    if (entry.dir) {
      items.push(['zip', 'Download as zip']);
    } else {
      items.push(['preview', 'Preview']);
      items.push(['download', 'Download']);
    }
    items.push(['rename', 'Rename']);
    items.push(['delete', 'Delete']);
    m.innerHTML = items.map(([a, l]) =>
      '<button data-act="' + a + '"' + (a === 'delete' ? ' class="danger"' : '') + '>' + l + '</button>'
    ).join('');
    document.body.appendChild(m);
    const r = anchor.getBoundingClientRect();
    m.style.left = Math.max(8, Math.min(r.right - 168, window.innerWidth - 184)) + 'px';
    m.style.top = Math.min(r.bottom + 6, window.innerHeight - m.offsetHeight - 10) + 'px';
    m.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-act]');
      if (!b) return;
      closeMenu();
      handleAction(b.dataset.act, entry);
    });
    menuEl = m;
  }

  document.addEventListener('click', (e) => {
    if (menuEl && !e.target.closest('.menu')) closeMenu();
  });
  window.addEventListener('resize', closeMenu);

  function handleAction(act, entry) {
    if (act === 'download') location.href = dlUrl(entry.rel);
    else if (act === 'zip') location.href = '/api/zip?path=' + enc(entry.rel);
    else if (act === 'preview') preview(entry);
    else if (act === 'rename') renameEntry(entry);
    else if (act === 'delete') deletePaths([entry.rel], '"' + entry.name + '"');
  }

  // ---------- selection actions ----------

  $('#selAll').addEventListener('change', () => {
    const on = $('#selAll').checked;
    const rows = rowEls();
    state.sel.clear();
    if (on) rows.forEach((r) => state.sel.add(r.dataset.rel));
    lastSelIndex = on && rows.length ? rows.length - 1 : -1;
    applySelection();
  });

  $('#btnClearSel').addEventListener('click', () => {
    state.sel.clear();
    lastSelIndex = -1;
    applySelection();
  });

  $('#btnDeleteSel').addEventListener('click', () => {
    const rels = [...state.sel];
    if (!rels.length) return;
    deletePaths(rels, rels.length + ' item(s)');
  });

  $('#btnDownloadSel').addEventListener('click', () => {
    const entries = state.entries.filter((e) => state.sel.has(e.rel));
    let i = 0;
    const next = () => {
      if (i >= entries.length) return;
      const e = entries[i++];
      if (e.dir) location.href = '/api/zip?path=' + enc(e.rel);
      else location.href = dlUrl(e.rel);
      setTimeout(next, 600);
    };
    next();
  });

  // ---------- file operations ----------

  async function deletePaths(paths, label) {
    if (!confirm('Delete ' + label + '? This cannot be undone.')) return;
    try {
      await api('/api/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paths }),
      });
      toast('Deleted');
      state.sel.clear();
      load();
    } catch (e) {
      toast(e.message);
    }
  }

  async function renameEntry(entry) {
    const v = await promptBox('Rename', entry.name, 'Rename');
    if (!v || v === entry.name) return;
    try {
      await api('/api/rename', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: entry.rel, newName: v }),
      });
      load();
    } catch (e) {
      toast(e.message);
    }
  }

  async function newFolder() {
    const name = await promptBox('New folder', '', 'Create');
    if (!name) return;
    try {
      await api('/api/mkdir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: state.nav === 'all' ? state.path : '', name }),
      });
      load();
    } catch (e) {
      toast(e.message);
    }
  }

  // ---------- preview ----------

  const PREVIEW_VID = ['mp4', 'webm', 'mov', 'm4v', '3gp'];
  const PREVIEW_AUD = ['mp3', 'm4a', 'aac', 'wav', 'ogg', 'opus', 'flac'];

  let previewOpen = false;
  let previewList = [];
  let previewIndex = -1;
  let zoom = { s: 1, tx: 0, ty: 0 };

  function isImageEntry(entry) {
    return entry && !entry.dir && IMG.has(extOf(entry.name));
  }

  /** Semua yang bisa dibuka di preview (image/video/audio/pdf) — dipakai buat navigasi kiri/kanan. */
  function isPreviewable(entry) {
    if (!entry || entry.dir) return false;
    const e = extOf(entry.name);
    return IMG.has(e) || PREVIEW_VID.includes(e) || PREVIEW_AUD.includes(e) || e === 'pdf';
  }

  function currentPreviewEntry() {
    return previewList[previewIndex] || null;
  }

  /** Sinkronkan class body: sembunyikan halaman saat ada overlay full-screen. */
  function syncOverlayClass() {
    document.body.classList.toggle('overlay', previewOpen || !!dialogDone);
  }

  function preview(entry) {
    if (!isPreviewable(entry)) {
      location.href = dlUrl(entry.rel);
      return;
    }
    // Navigasi kiri/kanan mengikuti urutan yang tampil di tabel (image + video + audio + pdf).
    previewList = (state.view.length ? state.view : state.entries).filter(isPreviewable);
    previewIndex = previewList.findIndex((x) => x.rel === entry.rel);
    if (previewIndex < 0) {
      previewList = [entry];
      previewIndex = 0;
    }
    renderPreview();
    $('#preview').hidden = false;
    if (!previewOpen) {
      previewOpen = true;
      history.pushState({ sb: 'preview' }, '');
    }
    syncOverlayClass();
  }

  function renderPreview() {
    const entry = currentPreviewEntry();
    if (!entry) return;
    const e = extOf(entry.name);
    const u = dlUrl(entry.rel, true);
    const body = $('#previewBody');
    const isImage = isImageEntry(entry);

    $('#previewName').textContent = entry.name;
    $('#pvZoom').hidden = !isImage;
    const many = previewList.length > 1;
    $('#pvNav').hidden = !many;
    $('#pvPrevFloat').hidden = !many;
    $('#pvNextFloat').hidden = !many;
    if (many) $('#pvCount').textContent = (previewIndex + 1) + ' / ' + previewList.length;

    resetZoom();
    showLoading();
    $('#pvDlLabel').textContent = entry.size > 0 ? 'Download \u00B7 ' + fmtSize(entry.size) : 'Download';
    if (isImage) {
      body.innerHTML = '<div class="zwrap" id="zWrap"><img id="zImg" src="' + esc(u) + '" alt=""></div>';
      const img = $('#zImg');
      img.addEventListener('load', hideLoading);
      img.addEventListener('error', () => {
        hideLoading();
        toast('Failed to load preview');
      });
      if (img.complete && img.naturalWidth > 0) hideLoading();
      setupZoom();
    } else if (PREVIEW_VID.includes(e)) {
      body.innerHTML = '<video src="' + esc(u) + '" controls autoplay playsinline></video>';
      const v = body.querySelector('video');
      v.addEventListener('loadeddata', hideLoading);
      v.addEventListener('error', () => {
        hideLoading();
        toast('Failed to play this video');
      });
    } else if (PREVIEW_AUD.includes(e)) {
      body.innerHTML = '<audio src="' + esc(u) + '" controls autoplay></audio>';
      const a = body.querySelector('audio');
      a.addEventListener('loadeddata', hideLoading);
      a.addEventListener('error', hideLoading);
    } else if (e === 'pdf') {
      body.innerHTML = '<iframe src="' + esc(u) + '"></iframe>';
      const f = body.querySelector('iframe');
      f.addEventListener('load', hideLoading);
    } else {
      hideLoading();
    }
  }

  function showLoading() {
    $('#pvLoading').hidden = false;
  }

  function hideLoading() {
    $('#pvLoading').hidden = true;
  }

  function previewStep(delta) {
    if (!previewOpen || previewList.length <= 1) return;
    const next = previewIndex + delta;
    if (next < 0 || next >= previewList.length) return;
    previewIndex = next;
    renderPreview();
  }

  function closePreview(fromPop) {
    if (!previewOpen) return;
    previewOpen = false;
    $('#preview').hidden = true;
    $('#previewBody').innerHTML = '';
    hideLoading();
    previewList = [];
    previewIndex = -1;
    syncOverlayClass();
    if (!fromPop) history.back();
  }

  $('#previewClose').addEventListener('click', () => closePreview(false));
  $('#pvDownload').addEventListener('click', () => {
    const entry = currentPreviewEntry();
    if (!entry) return;
    const a = document.createElement('a');
    a.href = dlUrl(entry.rel);
    a.download = entry.name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    toast('Downloading \u00B7 ' + entry.name);
  });
  $('#pvPrev').addEventListener('click', () => previewStep(-1));
  $('#pvNext').addEventListener('click', () => previewStep(1));
  $('#pvPrevFloat').addEventListener('click', () => previewStep(-1));
  $('#pvNextFloat').addEventListener('click', () => previewStep(1));

  // ---------- zoom ----------

  function applyZoom() {
    const img = $('#zImg');
    if (!img) return;
    img.style.transform = 'translate(' + zoom.tx + 'px,' + zoom.ty + 'px) scale(' + zoom.s + ')';
    const label = $('#pvZoomLabel');
    if (label) label.textContent = Math.round(zoom.s * 100) + '%';
    const wrap = $('#zWrap');
    if (wrap) wrap.classList.toggle('zoomed', zoom.s > 1.01);
  }

  function resetZoom() {
    zoom = { s: 1, tx: 0, ty: 0 };
    applyZoom();
  }

  function zoomAt(clientX, clientY, factor) {
    const wrap = $('#zWrap');
    if (!wrap) return;
    const r = wrap.getBoundingClientRect();
    const px = clientX - r.left;
    const py = clientY - r.top;
    const cx = r.width / 2;
    const cy = r.height / 2;
    const ns = Math.min(8, Math.max(0.2, zoom.s * factor));
    const k = ns / zoom.s;
    zoom.tx = (px - cx) * (1 - k) + zoom.tx * k;
    zoom.ty = (py - cy) * (1 - k) + zoom.ty * k;
    zoom.s = ns;
    applyZoom();
  }

  function zoomCenter(factor) {
    const wrap = $('#zWrap');
    if (!wrap) return;
    const r = wrap.getBoundingClientRect();
    zoomAt(r.left + r.width / 2, r.top + r.height / 2, factor);
  }

  function setupZoom() {
    const wrap = $('#zWrap');
    if (!wrap) return;
    applyZoom();

    wrap.addEventListener('wheel', (e) => {
      e.preventDefault();
      zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.15 : 1 / 1.15);
    }, { passive: false });

    let drag = null;
    wrap.addEventListener('pointerdown', (e) => {
      if (e.pointerType === 'touch' || zoom.s <= 1) return;
      drag = { x: e.clientX, y: e.clientY };
      wrap.classList.add('dragging');
      try { wrap.setPointerCapture(e.pointerId); } catch (_) { /* ignore */ }
    });
    wrap.addEventListener('pointermove', (e) => {
      if (!drag || e.pointerType === 'touch') return;
      zoom.tx += e.clientX - drag.x;
      zoom.ty += e.clientY - drag.y;
      drag.x = e.clientX;
      drag.y = e.clientY;
      applyZoom();
    });
    const endDrag = () => {
      drag = null;
      wrap.classList.remove('dragging');
    };
    wrap.addEventListener('pointerup', endDrag);
    wrap.addEventListener('pointercancel', endDrag);

    wrap.addEventListener('dblclick', (e) => {
      if (zoom.s > 1.01) resetZoom();
      else zoomAt(e.clientX, e.clientY, 2);
    });

    // touch: 1 finger = pan (saat zoom), 2 fingers = pinch zoom
    let pinch = null;
    let pan = null;
    const dist = (t) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
    const mid = (t) => ({ x: (t[0].clientX + t[1].clientX) / 2, y: (t[0].clientY + t[1].clientY) / 2 });
    wrap.addEventListener('touchstart', (e) => {
      if (e.touches.length === 2) {
        pan = null;
        pinch = { d: dist(e.touches), s: zoom.s, tx: zoom.tx, ty: zoom.ty };
        e.preventDefault();
      } else if (e.touches.length === 1 && zoom.s > 1) {
        pan = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      }
    }, { passive: false });
    wrap.addEventListener('touchmove', (e) => {
      if (pinch && e.touches.length === 2) {
        const r = wrap.getBoundingClientRect();
        const m = mid(e.touches);
        const ns = Math.min(8, Math.max(0.2, pinch.s * (dist(e.touches) / pinch.d)));
        const k = ns / pinch.s;
        zoom.tx = (m.x - r.left - r.width / 2) * (1 - k) + pinch.tx * k;
        zoom.ty = (m.y - r.top - r.height / 2) * (1 - k) + pinch.ty * k;
        zoom.s = ns;
        applyZoom();
        e.preventDefault();
      } else if (pan && e.touches.length === 1) {
        const t = e.touches[0];
        zoom.tx += t.clientX - pan.x;
        zoom.ty += t.clientY - pan.y;
        pan.x = t.clientX;
        pan.y = t.clientY;
        applyZoom();
        e.preventDefault();
      }
    }, { passive: false });
    const endTouch = () => { pinch = null; pan = null; };
    wrap.addEventListener('touchend', endTouch);
    wrap.addEventListener('touchcancel', endTouch);
  }

  $('#pvZoomIn').addEventListener('click', () => zoomCenter(1.25));
  $('#pvZoomOut').addEventListener('click', () => zoomCenter(1 / 1.25));
  $('#pvZoomReset').addEventListener('click', resetZoom);

  // ---------- dialog ----------

  let dialogDone = null;

  function closeDialog(v, fromPop) {
    if (!dialogDone) return;
    const done = dialogDone;
    dialogDone = null;
    $('#dlgBackdrop').hidden = true;
    syncOverlayClass();
    if (!fromPop) history.back();
    done(v);
  }

  function promptBox(title, value, okLabel) {
    return new Promise((resolve) => {
      $('#dlgTitle').textContent = title;
      const input = $('#dlgInput');
      input.value = value || '';
      const ok = $('#dlgOk');
      ok.textContent = okLabel || 'OK';
      $('#dlgBackdrop').hidden = false;
      setTimeout(() => { input.focus(); input.select(); }, 30);
      dialogDone = resolve;
      history.pushState({ sb: 'dialog' }, '');
      syncOverlayClass();
      ok.onclick = () => closeDialog(input.value.trim() || null, false);
      $('#dlgCancel').onclick = () => closeDialog(null, false);
      input.onkeydown = (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          closeDialog(input.value.trim() || null, false);
        }
      };
    });
  }

  // Esc (PC) & back button/gesture (Android) menutup overlay yang terbuka.
  window.addEventListener('popstate', () => {
    if (previewOpen) {
      closePreview(true);
      return;
    }
    if (dialogDone) {
      closeDialog(null, true);
      return;
    }
    closeMenu();
  });

  document.addEventListener('keydown', (e) => {
    if (previewOpen) {
      if (e.key === 'Escape') {
        closePreview(false);
        return;
      }
      if (previewList.length > 1) {
        if (e.key === 'ArrowLeft') {
          previewStep(-1);
          e.preventDefault();
          return;
        }
        if (e.key === 'ArrowRight') {
          previewStep(1);
          e.preventDefault();
          return;
        }
      }
      if (isImageEntry(currentPreviewEntry())) {
        if (e.key === '+' || e.key === '=') {
          zoomCenter(1.25);
          e.preventDefault();
          return;
        }
        if (e.key === '-' || e.key === '_') {
          zoomCenter(1 / 1.25);
          e.preventDefault();
          return;
        }
        if (e.key === '0') {
          resetZoom();
          e.preventDefault();
          return;
        }
      }
    }
    if (e.key !== 'Escape') return;
    if (dialogDone) {
      closeDialog(null, false);
      return;
    }
    closeMenu();
  });

  // ---------- uploads ----------

  const fileInput = $('#fileInput');
  let pendingUploads = 0;
  let reloadTimer = null;

  function uploadDir() {
    return state.nav === 'all' ? state.path : '';
  }

  function uploadFiles(files) {
    for (const f of files) uploadOne(f);
  }

  function uploadOne(file) {
    pendingUploads++;
    const dir = uploadDir();
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', '/api/upload?path=' + enc(dir) + '&name=' + enc(file.name));
    const finish = (ok, msg) => {
      pendingUploads--;
      if (ok) {
        toast('Uploaded \u00B7 ' + file.name + ' \u2192 ' + (dir || 'Root'));
        scheduleReload();
      } else {
        toast('Upload failed: ' + file.name + (msg ? ' (' + msg + ')' : ''));
      }
      pollProgress();
    };
    xhr.onload = () => finish(xhr.status >= 200 && xhr.status < 300, 'HTTP ' + xhr.status);
    xhr.onerror = () => finish(false, 'error');
    xhr.onabort = () => finish(false, 'aborted');
    xhr.send(file);
  }

  function scheduleReload() {
    clearTimeout(reloadTimer);
    reloadTimer = setTimeout(() => {
      if (pendingUploads === 0) load();
    }, 500);
  }

  $('#btnUpload').addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', () => {
    if (fileInput.files.length) uploadFiles(fileInput.files);
    fileInput.value = '';
  });
  $('#btnNewFolder').addEventListener('click', newFolder);

  // drag & drop (hint hanya muncul saat dragging)
  let dragDepth = 0;
  const dropHint = $('#dropHint');
  window.addEventListener('dragenter', (e) => {
    if (e.dataTransfer && Array.from(e.dataTransfer.types).includes('Files')) {
      dragDepth++;
      dropHint.hidden = false;
    }
  });
  window.addEventListener('dragleave', () => {
    dragDepth--;
    if (dragDepth <= 0) {
      dragDepth = 0;
      dropHint.hidden = true;
    }
  });
  window.addEventListener('dragover', (e) => e.preventDefault());
  window.addEventListener('drop', (e) => {
    e.preventDefault();
    dragDepth = 0;
    dropHint.hidden = true;
    if (e.dataTransfer && e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files);
  });

  // ---------- search / sort / crumbs / nav ----------

  let searchTimer = null;
  $('#search').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    const v = e.target.value.trim();
    searchTimer = setTimeout(() => {
      state.q = v;
      load();
    }, 300);
  });

  $('#sortName').addEventListener('click', () => {
    state.sortDesc = !state.sortDesc;
    renderRows();
  });

  $('#crumbs').addEventListener('click', (e) => {
    const a = e.target.closest('a[data-path]');
    if (a) {
      e.preventDefault();
      state.path = a.dataset.path;
      load();
    }
  });

  $('#nav').addEventListener('click', (e) => {
    const a = e.target.closest('a[data-nav]');
    if (!a) return;
    e.preventDefault();
    setNav(a.dataset.nav);
  });

  function setNav(nav) {
    state.nav = nav;
    state.path = '';
    state.q = '';
    $('#search').value = '';
    document.querySelectorAll('#nav a').forEach((a) => {
      a.classList.toggle('active', a.dataset.nav === nav);
    });
    load();
  }

  // ---------- live progress ----------

  const progEl = $('#progress');
  const doneSeen = new Map();
  // Baseline per-sesi: id transfer tertinggi saat halaman dibuka. Riwayat yang sudah
  // selesai sebelum halaman ini dibuka tidak ditampilkan lagi di panel.
  let baselineId = 0;
  const seenLive = new Set();
  const dismissedIds = new Set();

  async function initProgressBaseline() {
    try {
      const d = await api('/api/progress');
      baselineId = (d.transfers || []).reduce((m, t) => Math.max(m, t.id), 0);
    } catch (e) { /* ignore */ }
  }

  async function pollProgress() {
    if (document.hidden) return;
    try {
      const d = await api('/api/progress');
      renderProgress(d.transfers || []);
    } catch (e) { /* ignore */ }
  }

  function renderProgress(items) {
    const now = Date.now();
    const shown = [];
    for (const t of items) {
      if (dismissedIds.has(t.id)) continue;
      if (t.state === 'running') {
        seenLive.add(t.id);
        doneSeen.delete(t.id);
        shown.push(t);
        continue;
      }
      // Hanya tampilkan hasil akhir untuk transfer yang muncul di sesi ini.
      if (t.id <= baselineId && !seenLive.has(t.id)) continue;
      if (!doneSeen.has(t.id)) doneSeen.set(t.id, now);
      if (now - doneSeen.get(t.id) < 8000) shown.push(t);
    }
    for (const id of [...doneSeen.keys()]) {
      if (!items.some((t) => t.id === id)) doneSeen.delete(id);
    }

    progEl.innerHTML = shown.slice(0, 5).map((t) => {
      const running = t.state === 'running';
      const isIncoming = t.dir === 'in';
      const icon = isIncoming ? I.arrowIn : I.arrowOut;
      const dirClass = isIncoming ? ' in' : ' out';
      const pct = t.total > 0 ? Math.min(100, Math.round((t.done / t.total) * 100)) : null;
      const speed = t.speed > 0 ? fmtSize(t.speed) + '/s' : '';
      const size = t.total > 0 ? fmtSize(t.done) + ' / ' + fmtSize(t.total) : fmtSize(t.done);
      let status;
      if (running && t.paused) status = 'Paused \u00B7 ' + size;
      else if (running) {
        const eta = (t.total > 0 && t.speed > 0) ? ' \u00B7 ETA ' + fmtEta((t.total - t.done) / t.speed) : '';
        status = size + (speed ? ' \u00B7 ' + speed : '') + eta;
      } else if (t.state === 'done') status = 'Done \u00B7 ' + fmtSize(t.total > 0 ? t.total : t.done);
      else status = 'Failed' + (t.error ? ': ' + t.error : '');
      // Transfer masuk: tunjukkan folder tujuannya (upload browser = folder yang dibuka,
      // kiriman dari HP lain = Inbox) supaya nggak nebak-nebak file-nya ke mana.
      if (isIncoming && t.folder != null) status += ' \u2192 ' + (t.folder === '' ? 'Root' : t.folder);

      const done = t.state === 'done';
      // Tombol folder hanya untuk transfer MASUK (upload/file diterima): membuka folder
      // tujuannya di HP. Untuk download, folder lokal komputer tidak bisa dibuka dari
      // halaman web (sandbox browser), jadi yang ditampilkan tombol info.
      const canOpen = done && isIncoming && t.folder != null;
      const folderAttr = canOpen
        ? ' data-act="open" data-folder="' + esc(t.folder) + '"'
        : '';
      const btnFolder = canOpen
        ? '<button class="ico up-btn" data-act="open" data-folder="' + esc(t.folder) + '" title="Open folder on the phone">' + I.openFolder + '</button>'
        : '';
      const btnWhere = done && !isIncoming
        ? '<button class="ico up-btn" data-act="where" title="Where is the file?">' + I.info + '</button>'
        : '';
      const btnPause = running
        ? (t.paused
          ? '<button class="ico up-btn" data-act="resume" data-id="' + t.id + '" title="Resume">' + I.play + '</button>'
          : '<button class="ico up-btn" data-act="pause" data-id="' + t.id + '" title="Pause">' + I.pause + '</button>')
        : '';
      const btnCancel = running
        ? '<button class="ico up-btn" data-act="cancel" data-id="' + t.id + '" title="Cancel">' + I.close + '</button>'
        : '';
      const indeterminate = running && !t.paused && t.total <= 0;
      const width = done ? 100 : (pct == null ? 35 : pct);
      const btnDismiss = running
        ? ''
        : '<button class="ico up-btn" data-act="dismiss" data-id="' + t.id + '" title="Dismiss">' + I.close + '</button>';
      return '<div class="up ' + t.state + dirClass + (t.paused ? ' paused' : '') +
        (folderAttr ? ' clickable' : '') + '"' + folderAttr +
        ' data-id="' + t.id + '" data-state="' + t.state + '">' +
        '<div class="up-head">' +
        '<div class="up-name">' + icon + '<span class="up-txt">' + esc(t.name) + '</span></div>' +
        btnFolder + btnWhere + btnPause + btnCancel + btnDismiss +
        '</div>' +
        '<div class="bar"><div class="fill' + (indeterminate ? ' indet' : '') + '" style="transform:scaleX(' + (width / 100) + ')"></div></div>' +
        '<div class="up-pct">' + esc(status) + '</div>' +
        '</div>';
    }).join('');
  }

  async function transferAction(id, action) {    try {
      await api('/api/' + action, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id }),
      });
    } catch (e) {
      toast(e.message);
    }
    pollProgress();
  }

  progEl.addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) return;
    if (b.dataset.act === 'open') {
      const folder = b.dataset.folder || '';
      setNav('all');
      state.path = folder;
      load();
      toast('Opened ' + (folder || 'Root') + ' on the phone');
      return;
    }
    if (b.dataset.act === 'where') {
      toast('Saved to your computer\u2019s Downloads folder');
      return;
    }
    const id = Number(b.dataset.id);
    if (b.dataset.act === 'pause') transferAction(id, 'pause');
    else if (b.dataset.act === 'resume') transferAction(id, 'resume');
    else if (b.dataset.act === 'cancel') transferAction(id, 'cancel');
    else if (b.dataset.act === 'dismiss') dismissTransfer(id);
  });

  /** Tutup kartu transfer yang sudah selesai (tombol ✕ atau swipe). */
  function dismissTransfer(id) {
    dismissedIds.add(id);
    doneSeen.delete(id);
    pollProgress();
  }

  // Swipe kiri/kanan untuk menutup kartu transfer yang sudah selesai (layar sentuh).
  let swipe = null;
  progEl.addEventListener('pointerdown', (e) => {
    if (e.pointerType !== 'touch') return;
    const card = e.target.closest('.up');
    if (!card || card.dataset.state === 'running') return;
    swipe = { card, id: Number(card.dataset.id), x: e.clientX, y: e.clientY, dx: 0 };
  });
  progEl.addEventListener('pointermove', (e) => {
    if (!swipe) return;
    const dx = e.clientX - swipe.x;
    const dy = e.clientY - swipe.y;
    if (Math.abs(dy) > 16 && Math.abs(dx) < 10) {
      swipe.card.style.transform = '';
      swipe.card.style.opacity = '';
      swipe = null;
      return;
    }
    swipe.dx = dx;
    swipe.card.style.transform = 'translateX(' + dx + 'px)';
    swipe.card.style.opacity = String(Math.max(.3, 1 - Math.abs(dx) / 240));
  });
  const endSwipe = () => {
    if (!swipe) return;
    const card = swipe.card;
    const id = swipe.id;
    const dx = swipe.dx;
    swipe = null;
    if (Math.abs(dx) > 64) {
      card.style.transition = 'transform .18s ease-out, opacity .18s ease-out';
      card.style.transform = 'translateX(' + (dx > 0 ? 340 : -340) + 'px)';
      card.style.opacity = '0';
      setTimeout(() => dismissTransfer(id), 160);
    } else {
      card.style.transition = 'transform .16s ease-out, opacity .16s ease-out';
      card.style.transform = '';
      card.style.opacity = '';
    }
  };
  progEl.addEventListener('pointerup', endSwipe);
  progEl.addEventListener('pointercancel', endSwipe);

  // ---------- side panel (storage / url / qr / theme) ----------

  async function loadInfo() {
    try {
      const i = await api('/api/info');
      const total = i.totalBytes || 0, free = i.freeBytes || 0, used = Math.max(0, total - free);
      const pct = total > 0 ? Math.round((used / total) * 100) : 0;
      $('#stFree').textContent = fmtSize(free) + ' free of ' + fmtSize(total);
      $('#stUsed').textContent = fmtSize(used) + ' used';
      $('#stPct').textContent = pct + '%';
      $('#stFill').style.transform = 'scaleX(' + (pct / 100) + ')';
      // Nama server: tampil hanya kalau pernah diatur di app (name kosong = disembunyikan).
      const nm = (i.name || '').trim();
      const nameEl = $('#srvName');
      nameEl.textContent = nm;
      nameEl.hidden = !nm;
      document.title = nm ? nm + ' \u00b7 ShareBox' : 'ShareBox';
    } catch (e) { /* ignore */ }
  }

  function initConnect() {
    const url = location.origin + '/';
    $('#urlText').textContent = url;
    $('#hostText').textContent = location.host;
    $('#qrImg').src = '/api/qr?text=' + enc(url) + '&size=300';
    $('#btnCopy').addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(url);
        toast('Address copied');
      } catch (e) {
        toast(url);
      }
    });
  }

  function initTheme() {
    const saved = localStorage.getItem('sb.theme');
    if (saved === 'dark') document.documentElement.classList.add('dark');
    $('#btnTheme').addEventListener('click', () => {
      const dark = document.documentElement.classList.toggle('dark');
      localStorage.setItem('sb.theme', dark ? 'dark' : 'light');
    });
  }

  // ---------- init ----------

  setNav('all');
  initTheme();
  initConnect();
  loadInfo();
  initProgressBaseline().then(pollProgress);
  setInterval(pollProgress, 800);
  setInterval(() => {
    if (!document.hidden && pendingUploads === 0 && state.sel.size === 0 && !state.q) loadInfo();
  }, 30000);
})();
