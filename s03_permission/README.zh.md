# s03: 权限系统

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s02: 工具调用](../s02_tool_use/) → **s03** → [s04: 钩子系统](../s04_hooks/)

> 在动作执行前检查，并把判断结果明确返回给模型。

---

## 问题

没有闸门的工具表会毫不犹豫地执行破坏性命令。执行之后再警告已经太晚，策略必须放在调用之前。

## 工作机制

1. 硬性拒绝永远不该运行的命令模式。
2. 匹配较软的规则并标记为 ask，交给人工决定。
3. 返回一条被阻止的 tool_result，而不是抛异常，让模型能改路。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s03_permission/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Permission.java`, `harness/AgentLoop.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s03_permission.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 运行 /rules，然后让它删除一个临时目录。
- 设置 PERMISSION_MODE=allow，对比行为差异。

## 下一章

继续阅读 [s04: 钩子系统](../s04_hooks/README.zh.md)。
