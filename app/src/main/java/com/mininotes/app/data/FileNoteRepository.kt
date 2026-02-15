package com.mininotes.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileNoteRepository(private val context: Context) {
    val notesDir = File(context.filesDir, "notes").also { it.mkdirs() }
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val allNotes: StateFlow<List<Note>> = _notes.asStateFlow()


    suspend fun refresh() = withContext(Dispatchers.IO) {
        val notes = notesDir.listFiles { f -> f.extension == "md" }
            ?.mapNotNull { file ->
                try {
                    val content = file.readText()
                    Note(
                        id = file.nameWithoutExtension,
                        content = content,
                        updatedAt = file.lastModified()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
        _notes.value = notes
    }

    suspend fun insert(content: String = ""): Note = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(notesDir, "$id.md")
        file.writeText(content)
        refresh()
        _notes.value.first { it.id == id }
    }

    suspend fun update(note: Note) = withContext(Dispatchers.IO) {
        val file = File(notesDir, "${note.id}.md")
        file.writeText(note.content)
        refresh()
    }

    suspend fun delete(note: Note) = withContext(Dispatchers.IO) {
        File(notesDir, "${note.id}.md").delete()
        refresh()
    }
}
