package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.model.SharedPostDraft
import com.example.data.model.UiState
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    entriesState: UiState<List<JournalEntry>>,
    searchQuery: String,
    selectedMood: JournalMood?,
    hasPinConfigured: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onMoodFilterSelect: (JournalMood?) -> Unit,
    onOpenNewEntry: () -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
    onTogglePinned: (JournalEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }
    val todayLabel = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Editorial Masthead
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = todayLabel.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Daily Folio",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (hasPinConfigured) {
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("masthead_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Lock journal vault",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search reflections, tags, or platforms…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("journal_search_input")
            )

            // Mood Filter Ribbon
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                FilterChip(
                    selected = selectedMood == null,
                    onClick = { onMoodFilterSelect(null) },
                    label = { Text("All") }
                )
                JournalMood.entries.forEach { mood ->
                    FilterChip(
                        selected = selectedMood == mood,
                        onClick = {
                            onMoodFilterSelect(if (selectedMood == mood) null else mood)
                        },
                        label = { Text(mood.label) }
                    )
                }
            }

            when (entriesState) {
                is UiState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = entriesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val entries = entriesState.data
                    if (entries.isEmpty()) {
                        EmptyJournalState(
                            hasActiveFilters = searchQuery.isNotBlank() || selectedMood != null,
                            onClearFilters = {
                                onSearchQueryChange("")
                                onMoodFilterSelect(null)
                            },
                            onCreateFirstEntry = onOpenNewEntry
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                JournalEntryCard(
                                    entry = entry,
                                    onClick = { onEditEntry(entry) },
                                    onTogglePin = { onTogglePinned(entry) },
                                    onDelete = { entryToDelete = entry }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (entryToDelete != null) {
        val target = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Reflection?") },
            text = {
                Text("This will permanently remove \"${target.title}\" from your cloud journal.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEntry(target.id)
                        entryToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_entry_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyJournalState(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onCreateFirstEntry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_editorial_desk),
            contentDescription = "Blank journal desk",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        )
        Text(
            text = if (hasActiveFilters) "No matching pages" else "A fresh page awaits",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (hasActiveFilters) {
                "Try clearing your search or mood filter to see all synced entries."
            } else {
                "Capture today's thoughts, pin meaningful moments, or share a post from Instagram or YouTube straight into your folio."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasActiveFilters) {
            OutlinedButton(onClick = onClearFilters) {
                Text("Clear Filters")
            }
        } else {
            Button(
                onClick = onCreateFirstEntry,
                modifier = Modifier.testTag("empty_state_new_entry_button")
            ) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Write First Entry")
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    val timestamp = entry.updatedAt ?: entry.createdAt ?: Timestamp.now()
    val dateLabel = remember(timestamp) { dateFormat.format(timestamp.toDate()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (entry.pinned) 1.5.dp else 1.dp,
                color = if (entry.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("journal_entry_card_${entry.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = entry.moodEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (entry.pinned) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (entry.pinned) "Unpin entry" else "Pin entry",
                            tint = if (entry.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (!entry.sharedPlatform.isNullOrBlank() || !entry.sharedUrl.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${entry.sharedPlatform ?: "Social"}: ${entry.sharedSummary ?: entry.sharedUrl.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!entry.socialSnapshotSummary.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Social time: ${entry.socialSnapshotSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (entry.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    entry.tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorSheet(
    editingEntry: JournalEntry?,
    pendingSharedDraft: SharedPostDraft?,
    todaySocialSummary: String?,
    todaySocialMinutes: Long,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        content: String,
        mood: JournalMood,
        tagsRaw: String,
        pinned: Boolean,
        attachSocialSummary: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(editingEntry, pendingSharedDraft) {
        mutableStateOf(
            editingEntry?.title ?: if (pendingSharedDraft != null) {
                "Reflection on ${pendingSharedDraft.platform} post"
            } else ""
        )
    }
    var content by remember(editingEntry, pendingSharedDraft) {
        mutableStateOf(
            editingEntry?.content ?: if (pendingSharedDraft != null) {
                buildString {
                    if (pendingSharedDraft.summary.isNotBlank()) {
                        append(pendingSharedDraft.summary)
                        append("\n\n")
                    }
                    if (!pendingSharedDraft.url.isNullOrBlank()) {
                        append("Source: ${pendingSharedDraft.url}\n\n")
                    }
                    append("My thoughts: ")
                }
            } else ""
        )
    }
    var mood by remember(editingEntry) {
        mutableStateOf(editingEntry?.moodEnum ?: JournalMood.CALM)
    }
    var tagsRaw by remember(editingEntry, pendingSharedDraft) {
        mutableStateOf(
            editingEntry?.tags?.joinToString(", ")
                ?: pendingSharedDraft?.platform?.lowercase().orEmpty()
        )
    }
    var pinned by remember(editingEntry) {
        mutableStateOf(editingEntry?.pinned ?: false)
    }
    var attachSocial by remember(editingEntry, todaySocialSummary) {
        mutableStateOf(editingEntry?.socialSnapshotSummary != null || todaySocialSummary != null)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (editingEntry == null) "New Folio Entry" else "Edit Entry",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin entry",
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pendingSharedDraft != null && editingEntry == null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Captured from ${pendingSharedDraft.platform} Share Sheet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                label = { Text("Headline") },
                placeholder = { Text("What stood out today?") },
                singleLine = true,
                supportingText = { Text("${title.length}/140") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input")
            )

            Text(
                text = "Mood",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                JournalMood.entries.forEach { item ->
                    FilterChip(
                        selected = mood == item,
                        onClick = { mood = item },
                        label = { Text(item.label) }
                    )
                }
            }

            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 20000) content = it },
                label = { Text("Reflection") },
                placeholder = { Text("Write freely…") },
                minLines = 5,
                maxLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_content_input")
            )

            OutlinedTextField(
                value = tagsRaw,
                onValueChange = { tagsRaw = it },
                label = { Text("Tags (comma separated)") },
                placeholder = { Text("gratitude, morning, focus") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_tags_input")
            )

            if (!todaySocialSummary.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Attach Social Telemetry ($todaySocialMinutes min)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = todaySocialSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = attachSocial,
                            onCheckedChange = { attachSocial = it }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        title,
                        content,
                        mood,
                        tagsRaw,
                        pinned,
                        attachSocial
                    )
                },
                enabled = title.trim().isNotEmpty() && content.trim().isNotEmpty() && !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_entry_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = if (editingEntry == null) "Save & Sync Entry" else "Update Entry",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
