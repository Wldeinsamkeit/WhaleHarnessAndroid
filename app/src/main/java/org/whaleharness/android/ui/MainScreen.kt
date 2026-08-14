package org.whaleharness.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.whaleharness.android.R
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

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        ),
    ) {
      Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (state.tab == AppTab.CHAT) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.pixel_whale),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text("小鲸鱼", fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Text(
                            when (state.tab) {
                                AppTab.SKILLS -> "Skills"
                                AppTab.REMOTE -> "电脑 Harness"
                                else -> "设置"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    if (state.tab == AppTab.SKILLS || state.tab == AppTab.REMOTE) {
                        TextButton(onClick = { viewModel.selectTab(AppTab.SETTINGS) }) { Text("‹ 设置") }
                    }
                },
                actions = {
                    if (state.tab == AppTab.CHAT) {
                        TextButton(onClick = { viewModel.selectTab(AppTab.SETTINGS) }) {
                            Surface(
                                shape = CircleShape,
                                color = if (state.remoteConnected) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    if (state.remoteConnected) "● 电脑已连接" else "设置",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (state.remoteConnected) Color(0xFF138A46) else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (state.tab != AppTab.SKILLS && state.tab != AppTab.REMOTE) {
                BottomDock(state.tab, viewModel::selectTab)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                AppTab.CHAT -> ChatScreen(state, viewModel)
                AppTab.SKILLS -> SkillsScreen(state, viewModel)
                AppTab.SETTINGS -> SettingsScreen(state, viewModel)
                AppTab.REMOTE -> RemoteHarnessScreen(state, viewModel)
            }
        }
      }
    }
}

@Composable
private fun BottomDock(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).shadow(14.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockItem(R.drawable.ic_chat, "小鲸鱼", AppTab.CHAT, selected, onSelect, Modifier.weight(1f))
            DockItem(R.drawable.ic_settings, "设置", AppTab.SETTINGS, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockItem(
    icon: Int,
    label: String,
    tab: AppTab,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = selected == tab
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { onSelect(tab) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
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
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF052C8F), Color(0xFF075EF2), Color(0xFF19C8F4)),
                            ),
                        )
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.pixel_whale),
                            contentDescription = "像素小鲸鱼",
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text("你的电脑 AI，现在也能装进手机", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "扫码连接 DeepSeek Harness，项目和工具仍在你的电脑上运行。",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (state.remoteConfigured) viewModel.selectTab(AppTab.REMOTE) else viewModel.openRemoteScanner()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0754DC)),
                        ) {
                            Text(if (state.remoteConfigured) "进入电脑 Harness" else "扫码连接电脑", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("或者配置手机本地 API") }
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

        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ) {
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("电脑连接", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF062A86), Color(0xFF0867F2), Color(0xFF22CAE9))))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                    ) {
                        Text(
                            if (state.remoteConnected) "● 已连接" else if (state.remoteConfigured) "● 已配对" else "○ 未连接",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text("电脑 Harness", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (state.remoteConfigured) state.remoteBaseUrl else "扫一次码，直接控制项目和会话",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.pixel_whale),
                    contentDescription = null,
                    modifier = Modifier.size(116.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Button(
            onClick = {
                if (state.remoteConfigured) viewModel.selectTab(AppTab.REMOTE) else viewModel.openRemoteScanner()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (state.remoteConfigured) "打开电脑 Harness" else "扫码连接电脑", fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = { viewModel.selectTab(AppTab.REMOTE) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("手动输入电脑地址和配对码") }

        Spacer(Modifier.height(6.dp))
        Text("模型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("🔑", fontSize = 20.sp) }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("配置 API", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.config.apiKey.isBlank()) "尚未配置" else state.config.model,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(if (state.config.apiKey.isBlank()) "●" else "●", color = if (state.config.apiKey.isBlank()) Color(0xFFFF9500) else Color(0xFF34C759))
                }
                OutlinedTextField(
                    value = state.config.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 基础地址") },
                    supportingText = { Text("例如：https://api.deepseek.com") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.config.model,
                    onValueChange = viewModel::updateModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.config.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.saveConfiguration() }, enabled = !state.isBusy) { Text("保存") }
                    Button(onClick = viewModel::testConnection, enabled = !state.isBusy) {
                        Text(if (state.isBusy) "测试中…" else "测试连接")
                    }
                }
            }
        }
        Text(
            "支持 DeepSeek、OpenAI 及兼容 /chat/completions 的 HTTPS 服务。API Key 使用 Android Keystore 加密保存。",
            modifier = Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))
        Text("扩展", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTab(AppTab.SKILLS) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 20.sp) }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Skills", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.skills.count { it.enabled }} 个已启用 · 可导入和编辑",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("安全与本地", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("文件权限", fontWeight = FontWeight.SemiBold)
                Text("只读取你明确选择的文件，最多 5 个，每个 200 KB。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("运行边界", fontWeight = FontWeight.SemiBold)
                Text("本地对话不执行 Shell 或 Git；连接电脑 Harness 后由电脑端按其权限规则执行。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "小鲸鱼 Android 0.3.0 · Harness 原生直连试用版",
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
