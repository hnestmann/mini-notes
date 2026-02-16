package com.mininotes.app.sync

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.mininotes.app.data.Note
import com.mininotes.app.data.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class SyncResult {
    data object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class WebDavSync(
    private val notesDir: File,
    private val config: WebDavConfig
) {
    private fun createSardine(): Sardine {
        val sardine = OkHttpSardine()
        sardine.setCredentials(config.username, config.password)
        return sardine
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun remotePath(filename: String): String {
        val base = ensureTrailingSlash(config.url)
        val encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        return "$base$encoded"
    }

    suspend fun upload(note: Note): SyncResult = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine()
            val filename = "${note.id}.md"
            val url = remotePath(filename)
            sardine.put(url, note.content.toByteArray(StandardCharsets.UTF_8), "text/markdown")
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun delete(note: Note): SyncResult = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine()
            val filename = "${note.id}.md"
            val url = remotePath(filename)
            if (sardine.exists(url)) {
                sardine.delete(url)
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Delete failed")
        }
    }

    suspend fun uploadAll(notes: List<Note>): SyncResult = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine()
            for (note in notes) {
                val filename = "${note.id}.md"
                val url = remotePath(filename)
                sardine.put(url, note.content.toByteArray(StandardCharsets.UTF_8), "text/markdown")
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun downloadAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine()
            val base = ensureTrailingSlash(config.url)
            val resources = sardine.list(base)
            notesDir.mkdirs()
            for (resource in resources) {
                if (resource.isDirectory) continue
                val name = resource.name ?: continue
                if (!name.toString().endsWith(".md")) continue
                val href = resource.href.toString()
                val url = if (href.startsWith("http://") || href.startsWith("https://")) {
                    href
                } else {
                    URI.create(base).resolve(href).toString()
                }
                val inputStream = sardine.get(url)
                val content = inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                inputStream.close()
                val file = File(notesDir, name)
                file.writeText(content)
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun fullSync(getNotes: suspend () -> List<Note>): SyncResult = withContext(Dispatchers.IO) {
        val downloadResult = downloadAll()
        if (downloadResult is SyncResult.Error) return@withContext downloadResult
        uploadAll(getNotes())
    }
}
