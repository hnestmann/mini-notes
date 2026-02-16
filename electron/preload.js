const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  notes: {
    list: () => ipcRenderer.invoke('notes:list'),
    create: (content) => ipcRenderer.invoke('notes:create', content),
    update: (note) => ipcRenderer.invoke('notes:update', note),
    delete: (note) => ipcRenderer.invoke('notes:delete', note),
    getDir: () => ipcRenderer.invoke('notes:getDir')
  },
  webdav: {
    getConfig: () => ipcRenderer.invoke('webdav:getConfig'),
    saveConfig: (config) => ipcRenderer.invoke('webdav:saveConfig', config),
    clearConfig: () => ipcRenderer.invoke('webdav:clearConfig'),
    upload: (config, note) => ipcRenderer.invoke('webdav:upload', config, note),
    delete: (config, note) => ipcRenderer.invoke('webdav:delete', config, note),
    downloadAll: (config) => ipcRenderer.invoke('webdav:downloadAll', config),
    fullSync: (config) => ipcRenderer.invoke('webdav:fullSync', config)
  }
});
