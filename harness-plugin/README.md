# Whale Harness Mobile Companion

这是运行在 **DeepSeek Harness 同一进程**中的移动配对插件，不是网页代理或独立桥接器。

```bash
dsh plugin --profile web add ./harness-plugin/whale-harness-mobile-companion-0.1.0.tgz
dsh web
```

Harness 启动后会在同一个终端显示二维码和 8 位一次性配对码。手机扫码后会换取独立设备令牌，并直接调用 `ctx.apiProxy` 的会话能力。

当前开放的移动端能力：会话列表、新建会话、读取历史、发送任务、停止任务、工作区列表和 Skill 列表。配对码 10 分钟有效且成功使用一次后立即失效；设备令牌只保存在 Harness 进程内，Harness 重启后需要重新配对。
