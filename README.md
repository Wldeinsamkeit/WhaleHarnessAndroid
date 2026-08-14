# 小鲸鱼 Android

小鲸鱼的独立 Android 客户端，使用 Kotlin 与 Jetpack Compose 编写。界面与 iOS 版保持同一套“小鲸鱼 / 设置”结构，同时可以独立调用 API，或通过局域网控制电脑上的 DeepSeek Harness。

## 试用版能力

- DeepSeek、OpenAI 和兼容 `/chat/completions` 的 HTTPS API
- API Key 使用 Android Keystore 加密保存
- 真实对话和连接测试
- 从系统文件选择器附加本地文本、代码、JSON 和 XML
- 新建、导入、编辑、启停和删除 Skill
- 已启用 Skill 自动注入模型系统消息
- 扫描电脑配对码，自动写入桥接器地址和令牌
- 局域网连接测试，手机端操作电脑 Harness 的项目、会话与工具
- 配对令牌使用 Android Keystore 加密保存
- Android 8.0（API 26）及以上手机和平板

## 安装 APK

手机扫描下方二维码会打开最新 GitHub Release：

![扫码下载小鲸鱼 Android](download-qr.png)

1. 下载 `WhaleHarness-v0.2.0-debug.apk`。
2. 点击 APK；如果系统提示，允许当前文件管理器“安装未知应用”。
3. 安装后打开“小鲸鱼”。
4. 进入“设置”，填写自己的 API 地址、模型与 API Key，并点“测试连接”。

这是本地调试签名的试用包，不能覆盖未来使用其他签名发布的正式包。卸载 App 会删除本地配置。

## 扫码连接电脑 Harness

### macOS 一键启动

1. 电脑安装 Node.js 20 或更高版本。
2. 下载并解压本仓库源码。
3. 双击 `desktop-bridge/start-mac.command`；首次被 macOS 阻止时，右键该文件选“打开”。
4. 启动器会检查 `127.0.0.1:3080`，必要时自动启动 DeepSeek Harness，然后在终端显示配对二维码。
5. 手机连入同一 Wi-Fi，打开“设置 → 电脑 Harness → 扫描电脑二维码”。
6. 出现“电脑 Harness 已就绪”后，点“打开电脑 Harness”。

如果已经手动启动 Harness，也可以在 `desktop-bridge` 目录运行：

```bash
node bridge.mjs
```

桥接器默认代理 `http://127.0.0.1:3080`，并在 `0.0.0.0:3081` 侦听局域网连接。配对令牌每次启动都会重新随机生成。

> 当前版本的小鲸鱼导航、设置和连接页是原生 Compose UI；Harness 项目/会话控制页使用受限 WebView 加载电脑 Web UI，并注入手机端口宽度适配。它不是电脑屏幕镜像，但也还不是全原生的项目/会话渲染器。

## 使用边界

- App 只读取用户通过系统文件选择器明确选择的文件。
- 单个文件最多读取 200 KB，同时最多附加 5 个。
- 文件内容与消息会发送到用户配置的第三方模型服务。
- Skill 是可编辑提示文本，不是可执行程序，启用前应先审阅。
- 本地对话模式不执行 Shell、Git 或下载的原生代码；电脑控制模式中的执行由 DeepSeek Harness 在电脑端完成，并继续受它的审批策略约束。
- HTTP 桥接只允许私有局域网、`.local`、localhost 和 Tailscale CGNAT 地址；HTTPS 可用于用户自建的安全穿透。
- 不要把电脑的 3081 端口映射到公网；局域网 HTTP 不适合不可信 Wi-Fi。
- 不要把 API Key、私人文件或敏感日志提交到公开仓库。

## 本地构建

需要 JDK 17 和 Android SDK 36：

```bash
./gradlew test assembleDebug
```

原始构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 开源协议

MIT。小鲸鱼不是 DeepSeek 官方产品，也不提供模型 API。
