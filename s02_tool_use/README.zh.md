# s02: 工具调用

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s01: 智能体循环](../s01_agent_loop/) → **s02** → [s03: 权限系统](../s03_permission/)

> 一张调度表把模型的意图收敛成一组可审计的动作。

---

## 问题

一个 shell 什么都能做，但每个动作都不透明，也很难检查。工具表把可用动作写清楚，并把文件路径限制在工作区内。

## 工作机制

1. 每个工具都有名字、说明和 JSON schema。
2. 严格按名字调度，绝不执行模型给出的字符串。
3. 所有路径相对工作区解析，越界就拒绝。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s02_tool_use/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/StandardTools.java`, `harness/ToolRegistry.java`, `harness/Workspace.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s02_tool_use.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 运行 /tools，对照模型看到的 schema。
- 让它读取 ../README.md，观察拒绝结果。

## 下一章

继续阅读 [s03: 权限系统](../s03_permission/README.zh.md)。
