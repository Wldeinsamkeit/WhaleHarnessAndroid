package org.whaleharness.android.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
internal fun RemoteHarnessScreen(state: AppUiState, viewModel: MainViewModel) {
    var showsComputer by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::pairRemote)
    }

    if (showsComputer) {
        val entryUrl = viewModel.remoteEntryUrl()
        BackHandler { showsComputer = false }
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showsComputer = false }) { Text("‹ 连接") }
                    Text("电脑 Harness", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = RoundedCornerShape(99.dp),
                        color = if (state.remoteConnected) Color(0xFF34C759) else Color(0xFFFF9500),
                    ) {}
                }
            }
            if (entryUrl == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("请先扫码连接电脑") }
            } else {
                HarnessWebView(entryUrl, Modifier.fillMaxSize())
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("💻  扫码直连电脑", fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)
                Text("手机和电脑连入同一 Wi-Fi，扫描桥接器显示的二维码即可配对。项目、会话、Shell 和 Git 仍在你的电脑上运行。")
                Text("令牌只加密保存在本机，HTTP 配对仅允许局域网地址。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("对准电脑上的小鲸鱼配对码")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN),
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("扫描电脑二维码") }

        Text("手动连接", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.remoteBaseUrl,
                    onValueChange = viewModel::updateRemoteBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("桥接器地址") },
                    supportingText = { Text("例如：http://192.168.1.20:3081") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = state.remoteToken,
                    onValueChange = viewModel::updateRemoteToken,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.remoteConfigured) "配对令牌（留空保持不变）" else "配对令牌") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::saveAndTestRemote, enabled = !state.remoteBusy) {
                        Text(if (state.remoteBusy) "连接中…" else "保存并测试")
                    }
                    if (state.remoteConfigured) {
                        OutlinedButton(onClick = viewModel::disconnectRemote) { Text("断开") }
                    }
                }
            }
        }

        if (state.remoteConfigured) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(99.dp),
                            color = if (state.remoteConnected) Color(0xFF34C759) else Color(0xFFFF9500),
                        ) {}
                        Text(
                            if (state.remoteConnected) "  电脑 Harness 已就绪" else "  已配对，等待连接测试",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(state.remoteBaseUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = viewModel::testRemote, enabled = !state.remoteBusy) { Text("重新测试") }
                        Button(onClick = { showsComputer = true }, enabled = state.remoteConnected) { Text("打开电脑 Harness") }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "局域网版不需要 Tailscale，但离开当前 Wi-Fi 就会断开。不要把 3081 端口直接暴露到公网。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HarnessWebView(entryUrl: String, modifier: Modifier = Modifier) {
    val allowedHost = remember(entryUrl) { entryUrl.toUri().host }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = "${settings.userAgentString} WhaleHarnessAndroid/0.2"
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return request.url.host != allowedHost
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(MOBILE_ADAPTATION_SCRIPT, null)
                    }
                }
                loadUrl(entryUrl)
            }
        },
        update = { webView ->
            if (webView.url == null) webView.loadUrl(entryUrl)
        },
    )
}

private const val MOBILE_ADAPTATION_SCRIPT = """
(() => {
  if (!document.querySelector('meta[name="viewport"]')) {
    const viewport = document.createElement('meta');
    viewport.name = 'viewport';
    viewport.content = 'width=device-width, initial-scale=1, viewport-fit=cover';
    document.head.appendChild(viewport);
  }
  if (!document.getElementById('whale-harness-mobile-style')) {
    const style = document.createElement('style');
    style.id = 'whale-harness-mobile-style';
    style.textContent = `
      html, body, #root { width: 100%; max-width: 100vw; min-height: 100%; overflow-x: hidden; }
      input, textarea, select, button { font-size: 16px !important; }
      pre, code { white-space: pre-wrap !important; overflow-wrap: anywhere; }
      @media (max-width: 700px) {
        [role="dialog"] { max-width: calc(100vw - 24px) !important; max-height: calc(100vh - 24px) !important; }
        aside, [data-sidebar] { max-width: 88vw !important; }
      }
    `;
    document.head.appendChild(style);
  }
})();
"""
