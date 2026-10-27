package com.localvault.passwordbook.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localvault.passwordbook.data.DEFAULT_GROUP_ID
import com.localvault.passwordbook.data.VaultEntry
import com.localvault.passwordbook.data.VaultGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    entries: List<VaultEntry>,
    groups: List<VaultGroup>,
    query: String,
    groupFilter: String?,
    onQueryChange: (String) -> Unit,
    onGroupFilterChange: (String?) -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit
) {
    val filtered = remember(entries, query, groupFilter) {
        entries
            .filter { groupFilter == null || it.groupId == groupFilter }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.account.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.updatedAt }
    }

    val clearAction: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
        {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "清除搜索")
            }
        }
    } else {
        null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "本地密码本",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Outlined.Lock, contentDescription = "锁定")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增密码")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text("搜索名称或账户") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = clearAction,
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = groupFilter == null,
                        onClick = { onGroupFilterChange(null) },
                        label = { Text("全部") }
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = groupFilter == group.id,
                            onClick = { onGroupFilterChange(group.id) },
                            label = { Text(group.name) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    hasQuery = query.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                val sections = if (query.isBlank() && groupFilter == null) {
                    buildSections(filtered, groups)
                } else {
                    emptyList()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sections.isEmpty()) {
                        items(filtered, key = { it.id }) { entry ->
                            EntryCard(entry = entry, onClick = { onOpenEntry(entry.id) })
                        }
                    } else {
                        sections.forEach { (group, groupEntries) ->
                            item(key = "header_${group.id}") {
                                GroupHeader(name = group.name, count = groupEntries.size)
                            }
                            items(groupEntries, key = { it.id }) { entry ->
                                EntryCard(entry = entry, onClick = { onOpenEntry(entry.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildSections(
    entries: List<VaultEntry>,
    groups: List<VaultGroup>
): List<Pair<VaultGroup, List<VaultEntry>>> {
    val byGroup = entries.groupBy { it.groupId }
    val sections = groups.mapNotNull { group ->
        val list = byGroup[group.id].orEmpty()
        if (list.isEmpty()) null else group to list
    }
    // 未知分组的记录归入默认分组，避免「消失」
    val knownIds = groups.map { it.id }.toSet()
    val orphans = entries.filter { it.groupId !in knownIds }
    if (orphans.isEmpty()) return sections

    val defaultGroup = groups.firstOrNull { it.id == DEFAULT_GROUP_ID }
        ?: VaultGroup(DEFAULT_GROUP_ID, "默认")
    val existing = sections.firstOrNull { it.first.id == DEFAULT_GROUP_ID }
    return if (existing != null) {
        sections.map {
            if (it.first.id == DEFAULT_GROUP_ID) it.first to (it.second + orphans) else it
        }
    } else {
        listOf(defaultGroup to orphans) + sections
    }
}

@Composable
private fun GroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCard(entry: VaultEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.account.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.account,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (entry.subPassword.isNotBlank()) {
                Text(
                    "子密码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasQuery) "没有匹配的记录" else "还没有保存任何密码",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasQuery) "换个关键词试试" else "点击右下角 + 添加第一条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
