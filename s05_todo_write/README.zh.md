# s05: 待办计划

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s04: 钩子系统](../s04_hooks/) → **s05** → [s06: 子智能体](../s06_subagent/)

> 把计划放在运行框架里，才能随时重新展示。

---

## 问题

长任务会漂移：模型忘了计划，重复劳动，或者悄悄漏掉一步。只存在于提示词里的计划，会在下一次摘要时消失。

## 工作机制

1. 保存一个有序列表，状态分为 pending、in_progress、completed。
2. 最多允许一项处于进行中，下一步就不会含糊。
3. 统计工具轮次，计划过期时重新注入。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s05_todo_write/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Todo.java`, `harness/Hooks.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s05_todo_write.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 让它做三步任务，每步之后运行 /todo。
- 连续几轮不更新待办，观察提醒出现。

## 下一章

继续阅读 [s06: 子智能体](../s06_subagent/README.zh.md)。
