# s14: MCP 插件

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s13: 智能体团队](../s13_agent_teams/) → **s14** → [s15: 集成式运行框架](../s15_integrated_harness/)

> 用标准协议发现外部工具，并把它们和本地工具放在一起。

---

## 问题

有用的工具早就存在于别的进程和语言里。在运行框架里逐个重写，扩展不了。

## 工作机制

1. 把服务器作为子进程启动，使用按行分隔的 JSON-RPC。
2. 初始化、列出工具，并注册为 mcp__server__tool。
3. 通过同一个注册表调用，并信任远端 schema。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s14_mcp_plugin/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Mcp.java` | 每章共用的实现。 |
| `s14_mcp_plugin/DemoMcpServer.java` | 没有 mcp.json 时使用的内置演示服务器。 |

## 运行

```bash
java -cp out s14_mcp_plugin.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 用自带的演示服务器离线运行这一章。
- 运行 /mcp，然后让它回显字符串、计算两数之和。

## 下一章

继续阅读 [s15: 集成式运行框架](../s15_integrated_harness/README.zh.md)。
