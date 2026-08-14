package org.whaleharness.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.whaleharness.android.data.ChatMessage
import org.whaleharness.android.data.LocalAttachment
import org.whaleharness.android.data.Role
import org.whaleharness.android.data.Skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhaleHarnessApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐳", fontSize = 25.sp)
                        Text(" 小鲸鱼", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TabItem("💬", "对话", AppTab.CHAT, state.tab, viewModel::selectTab)
                TabItem("✨", "Skills", AppTab.SKILLS, state.tab, viewModel::selectTab)
                TabItem("⚙️", "设置", AppTab.SETTINGS, state.tab, viewModel::selectTab)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                AppTab.CHAT -> ChatScreen(state, viewModel)
                AppTab.SKILLS -> SkillsScreen(state, viewModel)
                AppTab.SETTINGS -> SettingsScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    icon: String,
    label: String,
    tab: AppTab,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = { Text(icon, fontSize = 20.sp) },
        label = { Text(label) },
    )
}

@Composable
private fun ChatScreen(state: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris.mapNotNull { readTextAttachment(context, it) })
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🐳", fontSize = 84.sp)
                Spacer(Modifier.height(12.dp))
                Text("今天想让小鲸鱼做什么？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("配置自己的 API、Skill 和文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { MessageBubble(it) }
                if (state.isBusy) item { Text("小鲸鱼正在思考…", color = MaterialTheme.colorScheme.primary) }
            }
        }

        Surface(tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (state.attachments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.attachments, key = { it.id }) { attachment ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📄 ${attachment.name}", maxLines = 1)
                                    TextButton(onClick = { viewModel.removeAttachment(attachment.id) }) { Text("×") }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入任务或问题…") },
                    minLines = 2,
                    maxLines = 5,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        fileLauncher.launch(arrayOf("text/*", "application/json", "application/xml"))
                    }) { Text("📎 添加文件") }
                    Button(
                        onClick = viewModel::send,
                        enabled = state.draft.isNotBlank() && !state.isBusy,
                    ) { Text("发送") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(14.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkillsScreen(state: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Skill?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readTextAttachment(context, it) }?.let { viewModel.importSkill(it.name, it.content) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { editing = Skill(name = "", prompt = "") }) { Text("新建 Skill") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/json")) }) {
                Text("导入文件")
            }
        }
        Text(
            "启用的 Skill 会作为系统指令发送给你配置的模型。启用前请先阅读内容。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.skills, key = { it.id }) { skill ->
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, fontWeight = FontWeight.Bold)
                                Text(skill.prompt, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = skill.enabled,
                                onCheckedChange = { viewModel.toggleSkill(skill, it) },
                            )
                        }
                        Row {
                            TextButton(onClick = { editing = skill }) { Text("编辑") }
                            TextButton(onClick = { viewModel.deleteSkill(skill) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { skill ->
        SkillEditor(
            skill = skill,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveSkill(it)
                editing = null
            },
        )
    }
}

@Composable
private fun SkillEditor(skill: Skill, onDismiss: () -> Unit, onSave: (Skill) -> Unit) {
    var name by remember(skill.id) { mutableStateOf(skill.name) }
    var prompt by remember(skill.id) { mutableStateOf(skill.prompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill.name.isBlank()) "新建 Skill" else "编辑 Skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Markdown 指令") }, minLines = 7)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(skill.copy(name = name.trim(), prompt = prompt.trim())) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SettingsScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("模型 API", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "支持 DeepSeek、OpenAI 及兼容 /chat/completions 的 HTTPS 服务。API Key 使用 Android Keystore 加密保存。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.config.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API 基础地址") },
            supportingText = { Text("例如：https://api.deepseek.com") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.config.model,
            onValueChange = viewModel::updateModel,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.config.apiKey,
            onValueChange = viewModel::updateApiKey,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.saveConfiguration() }, enabled = !state.isBusy) { Text("保存") }
            Button(onClick = viewModel::testConnection, enabled = !state.isBusy) {
                Text(if (state.isBusy) "测试中…" else "测试连接")
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("本地能力", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("• 最多同时附加 5 个文本文件\n• 单个文件最多读取 200 KB\n• 不申请整个存储空间权限\n• 当前不包含电脑桥接、Shell 或 Git")
        Text("版本 0.1.0 · 开源试用版", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun readTextAttachment(context: Context, uri: Uri): LocalAttachment? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "本地文件"
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 200_000) { "文件超过 200 KB" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: return null
    LocalAttachment(name = name, content = bytes.toString(Charsets.UTF_8))
}.getOrNull()
