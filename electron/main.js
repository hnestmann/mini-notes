const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs').promises;
const Store = require('electron-store');

const store = new Store({ name: 'webdav-settings' });
const NOTES_DIR = path.join(app.getPath('userData'), 'notes');

function ensureNotesDir() {
  return fs.mkdir(NOTES_DIR, { recursive: true });
}

async function listNotes() {
  await ensureNotesDir();
  const files = await fs.readdir(NOTES_DIR);
  const notes = [];
  for (const file of files) {
    if (!file.endsWith('.md')) continue;
    try {
      const content = await fs.readFile(path.join(NOTES_DIR, file), 'utf-8');
      const stat = await fs.stat(path.join(NOTES_DIR, file));
      notes.push({
        id: path.basename(file, '.md'),
        content,
        updatedAt: stat.mtimeMs
      });
    } catch (e) {
      console.error('Failed to read note:', file, e);
    }
  }
  return notes.sort((a, b) => b.updatedAt - a.updatedAt);
}

async function createNote(content = '') {
  const { randomUUID } = require('crypto');
  const id = randomUUID();
  const filePath = path.join(NOTES_DIR, `${id}.md`);
  await ensureNotesDir();
  await fs.writeFile(filePath, content, 'utf-8');
  const stat = await fs.stat(filePath);
  return { id, content, updatedAt: stat.mtimeMs };
}

async function updateNote(note) {
  const filePath = path.join(NOTES_DIR, `${note.id}.md`);
  await fs.writeFile(filePath, note.content, 'utf-8');
  const stat = await fs.stat(filePath);
  return { ...note, updatedAt: stat.mtimeMs };
}

async function deleteNote(note) {
  const filePath = path.join(NOTES_DIR, `${note.id}.md`);
  await fs.unlink(filePath);
}

function getWebDavConfig() {
  const url = store.get('webdav_url', '');
  if (!url) return null;
  return {
    url: store.get('webdav_url', ''),
    username: store.get('webdav_username', ''),
    password: store.get('webdav_password', '')
  };
}

function saveWebDavConfig(config) {
  store.set('webdav_url', config.url.trim().replace(/\/$/, ''));
  store.set('webdav_username', config.username);
  store.set('webdav_password', config.password);
}

function clearWebDavConfig() {
  store.delete('webdav_url');
  store.delete('webdav_username');
  store.delete('webdav_password');
}

async function createWebDavClient(config) {
  const { createClient } = await import('webdav');
  const baseUrl = config.url.endsWith('/') ? config.url : config.url + '/';
  return createClient(baseUrl, {
    username: config.username,
    password: config.password
  });
}

async function webDavUpload(config, note) {
  const client = await createWebDavClient(config);
  const filename = `${note.id}.md`;
  await client.putFileContents(filename, note.content, { overwrite: true });
}

async function webDavDelete(config, note) {
  const client = await createWebDavClient(config);
  const filename = `${note.id}.md`;
  try {
    if (await client.exists(filename)) {
      await client.deleteFile(filename);
    }
  } catch (e) {
    // File may not exist
  }
}

async function webDavDownloadAll(config) {
  const client = await createWebDavClient(config);
  await ensureNotesDir();
  const items = await client.getDirectoryContents('/');
  for (const item of items) {
    if (item.type === 'directory' || !item.basename.endsWith('.md')) continue;
    const content = await client.getFileContents(item.filename, { format: 'text' });
    const filePath = path.join(NOTES_DIR, item.basename);
    await fs.writeFile(filePath, content, 'utf-8');
  }
}

async function webDavUploadAll(config, notes) {
  const client = await createWebDavClient(config);
  for (const note of notes) {
    await client.putFileContents(`${note.id}.md`, note.content, { overwrite: true });
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 600,
    height: 800,
    minWidth: 400,
    minHeight: 500,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// IPC handlers
ipcMain.handle('notes:list', () => listNotes());
ipcMain.handle('notes:create', (_, content) => createNote(content));
ipcMain.handle('notes:update', (_, note) => updateNote(note));
ipcMain.handle('notes:delete', (_, note) => deleteNote(note));
ipcMain.handle('notes:getDir', () => NOTES_DIR);

ipcMain.handle('webdav:getConfig', () => getWebDavConfig());
ipcMain.handle('webdav:saveConfig', (_, config) => saveWebDavConfig(config));
ipcMain.handle('webdav:clearConfig', () => clearWebDavConfig());
ipcMain.handle('webdav:upload', (_, config, note) => webDavUpload(config, note));
ipcMain.handle('webdav:delete', (_, config, note) => webDavDelete(config, note));
ipcMain.handle('webdav:downloadAll', (_, config) => webDavDownloadAll(config));
ipcMain.handle('webdav:fullSync', async (_, config) => {
  await webDavDownloadAll(config);
  const notes = await listNotes();
  await webDavUploadAll(config, notes);
});
