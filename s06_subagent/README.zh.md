# s06: 子智能体

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s05: 待办计划](../s05_todo_write/) → **s06** → [s07: 技能加载](../s07_skill_loading/)

> 把嘈杂的调查放进独立上下文，只带回结论。

---

## 问题

每次读文件和走弯路都会填满父上下文。子智能体可以吸收这些噪音，前提是结果以简短摘要返回。

## 工作机制

1. 用全新的消息列表和一份写清楚的简报启动子智能体。
2. 只给它需要的工具，而不是父级的全部历史。
3. 只返回最终文本，并限制子智能体的轮数。

## 代码地图

| File | Role |
|---|---|
| `s06_subagent/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Agents.java`, `harness/AgentLoop.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s06_subagent.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 把仓库调研交给子智能体，对比上下文大小。
- 调低 SUBAGENT_MAX_TURNS，观察它提前停止。

## 下一章

继续阅读 [s07: 技能加载](../s07_skill_loading/README.zh.md)。
