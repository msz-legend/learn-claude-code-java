# s09: 记忆系统

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s08: 上下文压缩](../s08_context_compact/) → **s09** → [s10: 任务系统](../s10_task_system/)

> 一个小型持久存储，负责选择、提取和合并。

---

## 问题

每次会话都从零开始，同样的项目事实被反复发现。把所有内容塞进提示词只是把问题挪了个位置。

## 工作机制

1. 请求前挑选相关条目，而不是倾倒整个存储。
2. 从一轮完成的对话里提取少量长期事实。
3. 合并重复项并限制总量，保持存储可用。

## 代码地图

| File | Role |
|---|---|
| `s09_memory/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Memory.java` | 每章共用的实现。 |
| `.memory/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s09_memory.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 记住一条构建命令，重启后再召回。
- 加入近似重复的条目，运行 consolidate_memory。

## 下一章

继续阅读 [s10: 任务系统](../s10_task_system/README.zh.md)。
