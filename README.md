# 小鲸鱼 Android

小鲸鱼的独立 Android 客户端，使用 Kotlin 与 Jetpack Compose 编写。它既能独立调用模型 API，也能通过 DeepSeek Harness 进程内的移动配对插件直接控制电脑会话。

## 试用版能力

- DeepSeek、OpenAI 和兼容 `/chat/completions` 的 HTTPS API
- API Key 使用 Android Keystore 加密保存
- 真实对话和连接测试
- 从系统文件选择器附加本地文本、代码、JSON 和 XML
- 新建、导入、编辑、启停和删除 Skill
- 已启用 Skill 自动注入模型系统消息
- 扫描 DeepSeek Harness 自己显示的二维码，或输入 8 位一次性配对码
- 原生手机界面读取电脑会话、新建会话、查看历史并发送编程任务
- 不启动独立桥接器，不映射电脑网页，不使用 WebView
- 设备令牌使用 Android Keystore 加密保存
- Android 8.0（API 26）及以上手机和平板

## 安装 APK

手机扫描下方二维码会打开最新 GitHub Release：

![扫码下载小鲸鱼 Android](download-qr.png)

1. 下载 `WhaleHarness-v0.3.0-debug.apk`。
2. 点击 APK；如果系统提示，允许当前文件管理器“安装未知应用”。
3. 安装后打开“小鲸鱼”。
4. 进入“设置”，填写自己的 API 地址、模型与 API Key，并点“测试连接”。

这是本地调试签名的试用包，不能覆盖未来使用其他签名发布的正式包。卸载 App 会删除本地配置。

## 扫码连接电脑 Harness

### 第一次安装移动配对插件

电脑已经安装 DeepSeek Harness 后，在本仓库目录运行一次：

```bash
dsh plugin --profile web add ./harness-plugin/whale-harness-mobile-companion-0.1.0.tgz
```

这会把 `whale-harness-mobile-companion` 加入官方 `web` profile，并自动安装终端二维码依赖。插件与 Harness 在同一个进程中运行，不会启动第二个网页服务或代理进程。

### 每次连接

```bash
dsh web
```

1. Harness 终端会显示小鲸鱼二维码、电脑地址和 8 位一次性配对码。
2. 手机与电脑连入同一 Wi-Fi。
3. 手机打开“小鲸鱼 → 电脑 Harness → 扫描 Harness 二维码”。
4. 配对成功后，手机会原生显示电脑上的会话；选择会话即可读取历史和发送任务。

二维码只携带局域网地址和短期一次性配对码。手机用它换取独立设备令牌，之后直接调用同一 Harness 进程的 `ctx.apiProxy`。一次性配对码 10 分钟有效且成功使用一次后立即失效；Harness 重启后需要重新配对。

## 使用边界

- App 只读取用户通过系统文件选择器明确选择的文件。
- 单个文件最多读取 200 KB，同时最多附加 5 个。
- 文件内容与消息会发送到用户配置的第三方模型服务。
- Skill 是可编辑提示文本，不是可执行程序，启用前应先审阅。
- 本地对话模式不执行 Shell、Git 或下载的原生代码；电脑控制模式中的执行由 DeepSeek Harness 在电脑端完成，并继续受它的审批策略约束。
- HTTP 直连只允许私有局域网、`.local`、localhost 和 Tailscale CGNAT 地址；HTTPS 可用于后续安全远程方案。
- 不要把电脑的 43117 端口直接映射到公网；当前版本只面向可信私人 Wi-Fi。
- 移动插件当前开放会话列表、新建、历史、发送、停止、工作区列表和 Skill 列表；设置写入与审批交互仍在后续范围。
- 不要把 API Key、私人文件或敏感日志提交到公开仓库。

## 本地构建

需要 JDK 17 和 Android SDK 36：

```bash
./gradlew test assembleDebug
```

移动配对插件测试：

```bash
cd harness-plugin
npm test
```

原始构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 开源协议

MIT。小鲸鱼不是 DeepSeek 官方产品，也不提供模型 API。
