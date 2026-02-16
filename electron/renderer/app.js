const api = window.electronAPI;

let notes = [];
let currentNote = null;
let webDavConfig = null;
let debounceTimer = null;

const SCREENS = { LIST: 'list', EDIT: 'edit', SETTINGS: 'settings' };
let currentScreen = SCREENS.LIST;

function formatDate(timestamp) {
  const d = new Date(timestamp);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${months[d.getMonth()]} ${d.getDate()}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function showScreen(screen) {
  currentScreen = screen;
  document.getElementById('list-screen').style.display = screen === SCREENS.LIST ? 'block' : 'none';
  document.getElementById('edit-screen').style.display = screen === SCREENS.EDIT ? 'block' : 'none';
  document.getElementById('settings-screen').style.display = screen === SCREENS.SETTINGS ? 'block' : 'none';

  document.getElementById('title').textContent =
    screen === SCREENS.LIST ? 'Notes' : screen === SCREENS.EDIT ? 'Note' : 'Settings';

  document.getElementById('back-btn').style.display =
    screen === SCREENS.EDIT || screen === SCREENS.SETTINGS ? 'flex' : 'none';
  document.getElementById('settings-btn').style.display =
    screen === SCREENS.LIST ? 'flex' : 'none';
}

async function loadNotes() {
  notes = await api.notes.list();
  renderNotesList();
}

function renderNotesList() {
  const container = document.getElementById('notes-list');
  container.innerHTML = notes
    .map(
      (note) => `
    <div class="note-item" data-id="${note.id}">
      <div class="note-item-content">
        <div class="note-item-date">${formatDate(note.updatedAt)}</div>
        ${note.content ? `<div class="note-item-preview">${escapeHtml(note.content)}</div>` : ''}
      </div>
      <button class="note-item-delete" data-id="${note.id}" title="Delete">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
      </button>
    </div>
  `
    )
    .join('');

  container.querySelectorAll('.note-item').forEach((el) => {
    el.addEventListener('click', (e) => {
      if (!e.target.closest('.note-item-delete')) {
        openNote(el.dataset.id);
      }
    });
  });
  container.querySelectorAll('.note-item-delete').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      deleteNote(el.dataset.id);
    });
  });
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function openNote(id) {
  currentNote = notes.find((n) => n.id === id);
  if (currentNote) {
    document.getElementById('note-content').value = currentNote.content;
    showScreen(SCREENS.EDIT);
  }
}

async function deleteNote(id) {
  const note = notes.find((n) => n.id === id);
  if (!note) return;
  await api.notes.delete(note);
  if (webDavConfig) {
    try {
      await api.webdav.delete(webDavConfig, note);
    } catch (e) {
      console.error('WebDAV delete failed:', e);
    }
  }
  await loadNotes();
}

function scheduleDebouncedUpload() {
  if (debounceTimer) clearTimeout(debounceTimer);
  if (!webDavConfig) return;
  debounceTimer = setTimeout(async () => {
    debounceTimer = null;
    if (!currentNote) return;
    const content = document.getElementById('note-content').value;
    const updated = { ...currentNote, content };
    await api.notes.update(updated);
    try {
      await api.webdav.upload(webDavConfig, updated);
    } catch (e) {
      console.error('WebDAV upload failed:', e);
    }
    currentNote = updated;
    await loadNotes();
  }, 5000);
}

async function init() {
  webDavConfig = await api.webdav.getConfig();
  if (webDavConfig && webDavConfig.url) {
    try {
      await api.webdav.downloadAll(webDavConfig);
      await loadNotes();
    } catch (e) {
      console.error('Initial sync failed:', e);
    }
  } else {
    await loadNotes();
  }

  document.getElementById('new-note-btn').addEventListener('click', async () => {
    const note = await api.notes.create('');
    currentNote = note;
    document.getElementById('note-content').value = '';
    showScreen(SCREENS.EDIT);
    await loadNotes();
  });

  document.getElementById('back-btn').addEventListener('click', () => {
    if (currentScreen === SCREENS.EDIT && currentNote) {
      const content = document.getElementById('note-content').value;
      api.notes.update({ ...currentNote, content });
    }
    currentNote = null;
    showScreen(SCREENS.LIST);
    loadNotes();
  });

  document.getElementById('settings-btn').addEventListener('click', () => {
    showScreen(SCREENS.SETTINGS);
    document.getElementById('webdav-url').value = webDavConfig?.url || '';
    document.getElementById('webdav-username').value = webDavConfig?.username || '';
    document.getElementById('webdav-password').value = webDavConfig?.password || '';
    document.getElementById('sync-status').textContent = '';
  });

  document.getElementById('note-content').addEventListener('input', () => {
    if (currentNote) {
      currentNote = { ...currentNote, content: document.getElementById('note-content').value };
      scheduleDebouncedUpload();
    }
  });

  document.getElementById('save-btn').addEventListener('click', async () => {
    if (debounceTimer) clearTimeout(debounceTimer);
    if (!currentNote) return;
    const content = document.getElementById('note-content').value;
    const updated = { ...currentNote, content };
    await api.notes.update(updated);
    if (webDavConfig) {
      try {
        await api.webdav.upload(webDavConfig, updated);
      } catch (e) {
        console.error('WebDAV upload failed:', e);
      }
    }
    currentNote = null;
    showScreen(SCREENS.LIST);
    await loadNotes();
  });

  document.getElementById('save-config-btn').addEventListener('click', async () => {
    const url = document.getElementById('webdav-url').value.trim().replace(/\/$/, '');
    const username = document.getElementById('webdav-username').value.trim();
    const password = document.getElementById('webdav-password').value;
    const config = { url: url ? url + '/' : url, username, password };
    await api.webdav.saveConfig(config);
    webDavConfig = config.url ? config : null;
    document.getElementById('sync-status').textContent = 'Saved';
  });

  document.getElementById('clear-config-btn').addEventListener('click', async () => {
    await api.webdav.clearConfig();
    webDavConfig = null;
    document.getElementById('webdav-url').value = '';
    document.getElementById('webdav-username').value = '';
    document.getElementById('webdav-password').value = '';
    document.getElementById('sync-status').textContent = 'Cleared';
  });

  document.getElementById('sync-now-btn').addEventListener('click', async () => {
    const url = document.getElementById('webdav-url').value.trim().replace(/\/$/, '');
    const username = document.getElementById('webdav-username').value.trim();
    const password = document.getElementById('webdav-password').value;
    const config = { url: url ? url + '/' : url, username, password };
    if (!config.url || !config.username || !config.password) {
      document.getElementById('sync-status').textContent = 'Configure WebDAV first';
      return;
    }
    document.getElementById('sync-status').textContent = 'Syncing...';
    try {
      await api.webdav.fullSync(config);
      await loadNotes();
      document.getElementById('sync-status').textContent = 'Sync complete';
    } catch (e) {
      document.getElementById('sync-status').textContent = `Error: ${e.message}`;
    }
  });
}

init();
