package org.whaleharness.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.whaleharness.android.R
import org.whaleharness.android.data.ChatMessage
import org.whaleharness.android.data.RemoteSession
import org.whaleharness.android.data.Role
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RemoteHarnessScreen(state: AppUiState, viewModel: MainViewModel) {
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::pairRemote)
    }
    val scanOptions = remember {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("扫描 DeepSeek Harness 显示的移动配对二维码")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
    }

    LaunchedEffect(state.remoteScanRequest) {
        if (state.remoteScanRequest > 0) {
            viewModel.consumeRemoteScanRequest()
            scanner.launch(scanOptions)
        }
    }
    LaunchedEffect(state.remoteConfigured) {
        if (state.remoteConfigured && !state.remoteConnected && !state.remoteBusy) viewModel.testRemote()
    }

    if (state.remoteSessionId != null) {
        RemoteConversation(state, viewModel)
        return
    }

    if (state.remoteConnected) {
        RemoteSessionList(state, viewModel, onScan = { scanner.launch(scanOptions) })
    } else {
        PairingScreen(state, viewModel, onScan = { scanner.launch(scanOptions) })
    }
}

@Composable
private fun PairingScreen(state: AppUiState, viewModel: MainViewModel, onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFEAF2FF),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.pixel_whale),
                    contentDescription = "像素小鲸鱼",
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    contentScale = ContentScale.Fit,
                )
                Text("直接控制电脑 Harness", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("二维码由 DeepSeek Harness 内的移动配对插件生成。手机连接后直接读取电脑会话和发送编程任务，不加载电脑网页。")
                Text("项目、Shell、Git、Skills 和模型仍在你的电脑上运行。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !state.remoteBusy,
        ) { Text(if (state.remoteBusy) "正在配对…" else "扫描 Harness 二维码") }

        Text("也可以输入配对码", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.remoteBaseUrl,
                    onValueChange = viewModel::updateRemoteBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("电脑 Harness 地址") },
                    supportingText = { Text("例如：http://192.168.1.20:43117") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = state.remotePairCode,
                    onValueChange = viewModel::updateRemotePairCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("8 位一次性配对码") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::saveAndTestRemote, enabled = !state.remoteBusy) {
                        Text(if (state.remoteBusy) "连接中…" else "配对并连接")
                    }
                    if (state.remoteConfigured) {
                        OutlinedButton(onClick = viewModel::testRemote, enabled = !state.remoteBusy) { Text("重试") }
                    }
                }
            }
        }

        Text(
            "局域网直连不需要 Tailscale。二维码不包含 DeepSeek API Key，只包含电脑地址和短期一次性配对码。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RemoteSessionList(state: AppUiState, viewModel: MainViewModel, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("电脑项目与会话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.remoteBaseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(Modifier.size(10.dp), RoundedCornerShape(99.dp), color = Color(0xFF34C759)) {}
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = viewModel::createRemoteSession, enabled = !state.remoteBusy) { Text("＋ 新会话") }
            OutlinedButton(onClick = viewModel::refreshRemoteSessions, enabled = !state.remoteBusy) { Text("刷新") }
            TextButton(onClick = onScan, enabled = !state.remoteBusy) { Text("重新配对") }
        }
        if (state.remoteBusy) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  正在读取电脑 Harness…")
            }
        }
        if (state.remoteSessions.isEmpty() && !state.remoteBusy) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("电脑上还没有可显示的会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.remoteSessions, key = { it.id }) { session ->
                    SessionRow(session, onClick = { viewModel.openRemoteSession(session.id) })
                }
            }
        }
        TextButton(
            onClick = viewModel::disconnectRemote,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
        ) { Text("断开这台电脑") }
    }
}

@Composable
private fun SessionRow(session: RemoteSession, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFFE7F0FF),
            ) { Box(contentAlignment = Alignment.Center) { Text("⌘", color = Color(0xFF1565E8), fontWeight = FontWeight.Bold) } }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(session.title, fontWeight = FontWeight.Bold)
                Text(
                    session.cwd ?: DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.updatedAt)),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (session.running) "运行中" else "›", color = if (session.running) Color(0xFF1565E8) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RemoteConversation(state: AppUiState, viewModel: MainViewModel) {
    BackHandler(onBack = viewModel::closeRemoteSession)
    val selected = state.remoteSessions.firstOrNull { it.id == state.remoteSessionId }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::closeRemoteSession) { Text("‹ 项目") }
            Column(Modifier.weight(1f)) {
                Text(selected?.title ?: "电脑会话", fontWeight = FontWeight.Bold)
                Text("直接运行于电脑 Harness", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = viewModel::refreshRemoteHistory) { Text("刷新") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.remoteMessages, key = { it.id }) { RemoteMessageBubble(it) }
            if (state.remoteBusy) item { Text("电脑 Harness 正在执行…", color = Color(0xFF1565E8)) }
        }
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(
                    value = state.remoteDraft,
                    onValueChange = viewModel::updateRemoteDraft,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("给电脑 Harness 一个编程任务…") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(15.dp),
                )
                Button(
                    onClick = viewModel::sendRemote,
                    enabled = state.remoteDraft.isNotBlank() && !state.remoteBusy,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                ) { Text("发送到电脑") }
            }
        }
    }
}

@Composable
private fun RemoteMessageBubble(message: ChatMessage) {
    val user = message.role == Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (user) Color(0xFF1565E8) else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(14.dp),
                color = if (user) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
