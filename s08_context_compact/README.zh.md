# s08: 上下文压缩

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s07: 技能加载](../s07_skill_loading/) → **s08** → [s09: 记忆系统](../s09_memory/)

> 分层压缩，先做最便宜的，并保留一份交接笔记。

---

## 问题

对话会超出窗口。直接丢掉旧消息会丢失决策，全留着又发不出去。运行框架需要一条中间路线。

## 工作机制

1. 归档过大的工具结果，留下路径和预览。
2. 先截断旧工具输出，再动对话结构。
3. 摘要早期轮次，保留近期消息，必要时再截断一次。

## 代码地图

| File | Role |
|---|---|
| `s08_context_compact/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Compactor.java`, `harness/AgentLoop.java` | 每章共用的实现。 |
| `.compact/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s08_context_compact.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 设置 COMPACT_SUMMARIZE_AT_TOKENS=1500 和 COMPACT_KEEP_MESSAGES=2。
- 读一个大文件前后分别运行 /context。

## 下一章

继续阅读 [s09: 记忆系统](../s09_memory/README.zh.md)。
