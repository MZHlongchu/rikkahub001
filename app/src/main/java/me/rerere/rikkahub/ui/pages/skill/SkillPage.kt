package me.rerere.rikkahub.ui.pages.skill

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.AgentSkill
import me.rerere.rikkahub.data.model.SkillSource
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Switch
import me.rerere.rikkahub.ui.components.ui.SwitchSize
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillPage(vm: SkillVM = koinViewModel()) {
    val skills by vm.skills.collectAsStateWithLifecycle()
    val isImporting by vm.isImporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.importFromZip(it) { result ->
                result.onSuccess {
                    Toast.makeText(context, context.getString(R.string.skill_import_success), Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, context.getString(R.string.skill_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skill_page_title)) },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = {
                        zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    }
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Lucide.FileUp, contentDescription = stringResource(R.string.skill_import_zip))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.skill_tab_local)) },
                    icon = { Icon(Lucide.Sparkles, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.skill_tab_marketplace)) },
                    icon = { Icon(Lucide.Globe, null, modifier = Modifier.size(18.dp)) }
                )
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> LocalSkillsTab(
                        skills = skills,
                        onToggle = { vm.toggleSkill(it) },
                        onDelete = { vm.deleteSkill(it) },
                        onUpdate = { vm.updateSkill(it) },
                    )
                    1 -> MarketplaceTab(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun LocalSkillsTab(
    skills: List<AgentSkill>,
    onToggle: (kotlin.uuid.Uuid) -> Unit,
    onDelete: (kotlin.uuid.Uuid) -> Unit,
    onUpdate: (AgentSkill) -> Unit,
) {
    if (skills.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Lucide.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.skill_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.skill_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(skills, key = { it.id.toString() }) { skill ->
            SkillCard(
                skill = skill,
                onToggle = { onToggle(skill.id) },
                onDelete = { onDelete(skill.id) },
                onUpdate = onUpdate,
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: AgentSkill,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (AgentSkill) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (skill.enabled)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (skill.description.isNotBlank()) {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = skill.enabled,
                    onCheckedChange = { onToggle() },
                    size = SwitchSize.Small
                )
            }

            // 元信息
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (skill.author.isNotBlank()) {
                    Text(
                        text = skill.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "v${skill.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (skill.source) {
                        SkillSource.LOCAL -> stringResource(R.string.skill_source_local)
                        SkillSource.MARKETPLACE -> stringResource(R.string.skill_source_marketplace)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 展开内容预览
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Text(
                        text = skill.content.take(500) + if (skill.content.length > 500) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showEditSheet) {
        SkillEditSheet(
            skill = skill,
            onDismiss = { showEditSheet = false },
            onSave = {
                onUpdate(it)
                showEditSheet = false
            }
        )
    }
}

@Composable
private fun SkillEditSheet(
    skill: AgentSkill,
    onDismiss: () -> Unit,
    onSave: (AgentSkill) -> Unit,
) {
    var name by remember { mutableStateOf(skill.name) }
    var description by remember { mutableStateOf(skill.description) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.skill_edit_title),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.skill_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.skill_description)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        onSave(skill.copy(name = name, description = description))
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_confirm))
                }
            }
        }
    }
}

@Composable
private fun MarketplaceTab(vm: SkillVM) {
    val marketplaceSkills by vm.marketplaceSkills.collectAsStateWithLifecycle()
    val isLoading by vm.isLoadingMarketplace.collectAsStateWithLifecycle()
    val error by vm.marketplaceError.collectAsStateWithLifecycle()
    val isImporting by vm.isImporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.loadMarketplaceSkills()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.skill_search_hint)) },
            leadingIcon = { Icon(Lucide.Search, null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { vm.loadMarketplaceSkills(searchQuery) }) {
                    Icon(Lucide.Search, null)
                }
            }
        )

        // 浏览市场按钮
        TextButton(
            onClick = { context.openUrl("https://skillsmp.com/zh") },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(Lucide.Globe, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.skill_browse_marketplace))
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.skill_marketplace_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { vm.loadMarketplaceSkills(searchQuery) }) {
                            Text(stringResource(R.string.skill_retry))
                        }
                    }
                }
            }
            marketplaceSkills.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.skill_marketplace_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(marketplaceSkills, key = { it.id }) { item ->
                        MarketplaceSkillCard(
                            item = item,
                            isInstalling = isImporting,
                            onInstall = {
                                vm.installFromMarketplace(item) { result ->
                                    result.onSuccess {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.skill_install_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }.onFailure { e ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.skill_install_failed, e.message ?: ""),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceSkillCard(
    item: me.rerere.rikkahub.data.api.MarketplaceSkillItem,
    isInstalling: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onInstall,
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Lucide.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.skill_install))
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item.author.isNotBlank()) {
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "v${item.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.downloadCount > 0) {
                    Text(
                        text = "${item.downloadCount} downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
