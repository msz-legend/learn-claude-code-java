# s15: 集成式运行框架

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s14: MCP 插件](../s14_mcp_plugin/) → **s15** → [s16: 工作流运行时](../s16_workflow_runtime/)

> 一个循环，所有接口：这些机制放在一起才有意思。

---

## 问题

每个机制单独看都不大。真正的运行框架问题是权限、计划、记忆、任务、通知和压缩如何共享同一轮对话。

## 工作机制

1. 把所有工具接进一个注册表，子智能体的工具保持更小。
2. 在同一个循环上组合权限、钩子、压缩和通知。
3. 提供 /status，让操作者看到运行框架持有什么。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s15_integrated_harness/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/AgentLoop.java`, `harness/Todo.java`, `harness/Skills.java`, `harness/Memory.java`, `harness/TaskStore.java`, `harness/Background.java`, `harness/Cron.java`, `harness/Compactor.java`, `harness/Agents.java` | 每章共用的实现。 |
| `.tasks/, .memory/, .cron/, .compact/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s15_integrated_harness.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 让它做多步任务，查看 /status 和 /tasks。
- 跑一个慢命令，继续提问，等通知到达。

## 下一章

继续阅读 [s16: 工作流运行时](../s16_workflow_runtime/README.zh.md)。
