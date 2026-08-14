# 小鲸鱼 Android

小鲸鱼的独立 Android 客户端，使用 Kotlin 与 Jetpack Compose 编写。它不依赖 iOS 工程，也不需要电脑桥接器。

## 试用版能力

- DeepSeek、OpenAI 和兼容 `/chat/completions` 的 HTTPS API
- API Key 使用 Android Keystore 加密保存
- 真实对话和连接测试
- 从系统文件选择器附加本地文本、代码、JSON 和 XML
- 新建、导入、编辑、启停和删除 Skill
- 已启用 Skill 自动注入模型系统消息
- Android 8.0（API 26）及以上手机和平板

## 安装 APK

1. 把 `WhaleHarness-v0.1.0-debug.apk` 传到安卓手机。
2. 点击 APK；如果系统提示，允许当前文件管理器“安装未知应用”。
3. 安装后打开“小鲸鱼”。
4. 进入“设置”，填写自己的 API 地址、模型与 API Key，并点“测试连接”。

这是本地调试签名的试用包，不能覆盖未来使用其他签名发布的正式包。卸载 App 会删除本地配置。

## 使用边界

- App 只读取用户通过系统文件选择器明确选择的文件。
- 单个文件最多读取 200 KB，同时最多附加 5 个。
- 文件内容与消息会发送到用户配置的第三方模型服务。
- Skill 是可编辑提示文本，不是可执行程序，启用前应先审阅。
- 当前版本不包含电脑桥接、Shell、Git、插件市场或任意代码执行。
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
