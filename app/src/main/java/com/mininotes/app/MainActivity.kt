package com.mininotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mininotes.app.data.FileNoteRepository
import com.mininotes.app.data.Note
import com.mininotes.app.data.WebDavConfig
import com.mininotes.app.data.WebDavSettings
import com.mininotes.app.sync.SyncResult
import com.mininotes.app.sync.WebDavSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FileNoteRepository(this)
        val webDavSettings = WebDavSettings(this)

        setContent {
            MiniNotesTheme {
                MiniNotesApp(
                    repository = repository,
                    webDavSettings = webDavSettings
                )
            }
        }
    }
}

@Composable
fun MiniNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = Color.White,
            onSurface = Color.Black,
            primary = Color.Black,
            onPrimary = Color.White,
            outline = Color.Black
        ),
        content = content
    )
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatUpdatedAt(timestamp: Long): String = dateFormat.format(Date(timestamp))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MiniNotesApp(
    repository: FileNoteRepository,
    webDavSettings: WebDavSettings
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var webDavConfig by remember { mutableStateOf(webDavSettings.getConfig()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        webDavConfig?.let { config ->
            val sync = WebDavSync(repository.notesDir, config)
            val result = sync.downloadAll()
            if (result is SyncResult.Success) repository.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            is Screen.List -> "Notes"
                            is Screen.Edit -> "Note"
                            is Screen.Settings -> "Settings"
                        },
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    when (currentScreen) {
                        is Screen.Edit, is Screen.Settings -> {
                            IconButton(onClick = {
                                editingNote?.let { note ->
                                    scope.launch { repository.update(note) }
                                }
                                currentScreen = Screen.List
                                editingNote = null
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_back),
                                    contentDescription = "Back"
                                )
                            }
                        }
                        else -> {}
                    }
                },
                actions = {
                    when (currentScreen) {
                        is Screen.List -> {
                            IconButton(onClick = { currentScreen = Screen.Settings }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Settings"
                                )
                            }
                        }
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        when (currentScreen) {
            is Screen.List -> {
                NoteListScreen(
                    modifier = Modifier.padding(paddingValues),
                    repository = repository,
                    onAddNote = {
                        scope.launch {
                            val note = repository.insert("")
                            editingNote = note
                            currentScreen = Screen.Edit
                        }
                    },
                    onEditNote = { note ->
                        editingNote = note
                        currentScreen = Screen.Edit
                    },
                    onDeleteNote = { note ->
                        scope.launch {
                            repository.delete(note)
                            webDavConfig?.let { config ->
                                WebDavSync(repository.notesDir, config).delete(note)
                            }
                        }
                    }
                )
            }
            is Screen.Edit -> {
                editingNote?.let { note ->
                    NoteEditScreen(
                        modifier = Modifier.padding(paddingValues),
                        note = note,
                        repository = repository,
                        webDavConfig = webDavConfig,
                        onNoteChange = { editingNote = it },
                        onSave = {
                            scope.launch {
                                repository.update(it)
                                webDavConfig?.let { config ->
                                    WebDavSync(repository.notesDir, config).upload(it)
                                }
                                currentScreen = Screen.List
                                editingNote = null
                            }
                        }
                    )
                }
            }
            is Screen.Settings -> {
                SettingsScreen(
                    modifier = Modifier.padding(paddingValues),
                    webDavSettings = webDavSettings,
                    repository = repository,
                    onConfigUpdated = { webDavConfig = webDavSettings.getConfig() }
                )
            }
        }
    }
}

sealed class Screen {
    data object List : Screen()
    data object Edit : Screen()
    data object Settings : Screen()
}

@Composable
fun NoteListScreen(
    modifier: Modifier = Modifier,
    repository: FileNoteRepository,
    onAddNote: () -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    val notes by repository.allNotes.collectAsState()

    LaunchedEffect(Unit) { repository.refresh() }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Button(
                onClick = onAddNote,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("New Note", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp)) }
        items(notes) { note ->
            NoteListItem(
                note = note,
                onClick = { onEditNote(note) },
                onDelete = { onDeleteNote(note) }
            )
        }
    }
}

@Composable
fun NoteListItem(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatUpdatedAt(note.updatedAt),
                    fontSize = 14.sp,
                    color = Color.Black
                )
                if (note.content.isNotEmpty()) {
                    Text(
                        text = note.content,
                        fontSize = 14.sp,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete"
                )
            }
        }
    }
}

@Composable
fun NoteEditScreen(
    modifier: Modifier = Modifier,
    note: Note,
    repository: FileNoteRepository,
    webDavConfig: WebDavConfig?,
    onNoteChange: (Note) -> Unit,
    onSave: (Note) -> Unit
) {
    var content by remember(note.id) { mutableStateOf(note.content) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleDebouncedUpload(currentNote: Note) {
        debounceJob?.cancel()
        if (webDavConfig != null) {
            debounceJob = scope.launch {
                delay(5000)
                repository.update(currentNote)
                WebDavSync(repository.notesDir, webDavConfig).upload(currentNote)
            }
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = {
                content = it
                val updatedNote = note.copy(content = it)
                onNoteChange(updatedNote)
                scheduleDebouncedUpload(updatedNote)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            maxLines = Int.MAX_VALUE,
            placeholder = { Text("Type your note...") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Black,
                focusedPlaceholderColor = Color.Black.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = Color.Black.copy(alpha = 0.5f),
                cursorColor = Color.Black,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
        Button(
            onClick = {
                debounceJob?.cancel()
                onSave(note.copy(content = content))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("Save")
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    webDavSettings: WebDavSettings,
    repository: FileNoteRepository,
    onConfigUpdated: () -> Unit = {}
) {
    var url by remember { mutableStateOf(webDavSettings.getConfig()?.url ?: "") }
    var username by remember { mutableStateOf(webDavSettings.getConfig()?.username ?: "") }
    var password by remember { mutableStateOf(webDavSettings.getConfig()?.password ?: "") }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("WebDAV Sync", fontSize = 16.sp, color = Color.Black)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Folder URL") },
            placeholder = { Text("https://cloud.example.com/remote.php/dav/files/user/Notes/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = textFieldColors()
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = textFieldColors()
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = textFieldColors()
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val config = WebDavConfig(
                        url = url.trim().trimEnd('/').let { if (it.isNotEmpty()) "$it/" else it },
                        username = username.trim(),
                        password = password
                    )
                    webDavSettings.saveConfig(config)
                    onConfigUpdated()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Save")
            }
            Button(
                onClick = {
                    webDavSettings.clearConfig()
                    url = ""
                    username = ""
                    password = ""
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Clear")
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))
        Button(
            onClick = {
                scope.launch {
                    val config = WebDavConfig(url.trim(), username.trim(), password)
                    if (!config.isConfigured) {
                        syncStatus = "Configure WebDAV first"
                        return@launch
                    }
                    syncStatus = "Syncing..."
                    val sync = WebDavSync(repository.notesDir, config)
                    val result = sync.fullSync {
                        repository.refresh()
                        repository.allNotes.value
                    }
                    repository.refresh()
                    syncStatus = when (result) {
                        is SyncResult.Success -> "Sync complete"
                        is SyncResult.Error -> "Error: ${result.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sync),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text("Sync Now", modifier = Modifier.padding(start = 8.dp))
        }
        syncStatus?.let { status ->
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            Text(status, fontSize = 14.sp, color = Color.Black)
        }
    }
}

@Composable
private fun textFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color.Black,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black,
    cursorColor = Color.Black,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black
)
