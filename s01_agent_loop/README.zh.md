# s01: 智能体循环

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

起点 → **s01** → [s02: 工具调用](../s02_tool_use/)

> 一个循环加一个 shell 工具，就足以把聊天模型变成智能体。

---

## 问题

模型能提出一条 shell 命令，却在输出后停下。没有运行框架，人只能逐条执行命令再把结果贴回去。

## 工作机制

1. 把对话和工具定义一起发给模型。
2. 追加助手返回的内容块；没有 tool_use 块就直接返回文本。
3. 执行每个 tool_use，为每次调用追加一条 tool_result，然后继续循环。

## 代码地图

| File | Role |
|---|---|
| `s01_agent_loop/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/AgentLoop.java`, `harness/Cli.java`, `harness/Workspace.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s01_agent_loop.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 让它列出工作区，再读取最大的文件。
- 问一个不需要工具的问题，确认循环一轮后就退出。

## 下一章

继续阅读 [s02: 工具调用](../s02_tool_use/README.zh.md)。
