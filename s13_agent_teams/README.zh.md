# s13: 智能体团队

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s12: 定时调度](../s12_cron_scheduler/) → **s13** → [s14: MCP 插件](../s14_mcp_plugin/)

> 持久的队友，各自独立的上下文，共享的任务板和信箱。

---

## 问题

单个智能体无法同时探索多条独立分支。开一个帮手不难，难的是让它们不冲突地协作。

## 工作机制

1. 给每个队友名字、角色、独立循环，以及可选的工作树。
2. 共享任务板并原子认领，避免两人拿到同一个任务。
3. 用追加式信箱和游标，保证消息只投递一次。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s13_agent_teams/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Team.java`, `harness/TaskStore.java` | 每章共用的实现。 |
| `.team/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s13_agent_teams.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 为一个小任务启动一个队友，然后读 lead 的信箱。
- 在队友工作时运行 /team。

## 下一章

继续阅读 [s14: MCP 插件](../s14_mcp_plugin/README.zh.md)。
