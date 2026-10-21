# s10: 任务系统

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s09: 记忆系统](../s09_memory/) → **s10** → [s11: 后台任务](../s11_background_tasks/)

> 一个文件支撑的任务图，用原子认领让多个智能体共享。

---

## 问题

多个智能体需要能扛住崩溃、又不会重复分配的工作状态。内存里的列表两样都做不到。

## 工作机制

1. 每个任务一个 JSON 文件，原子创建。
2. 用 blockedBy 表达依赖，未清空时拒绝认领。
3. 在文件锁下认领，每个 owner 只允许一个进行中任务。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s10_task_system/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/TaskStore.java` | 每章共用的实现。 |
| `.tasks/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s10_task_system.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 创建两个任务，让第二个依赖第一个。
- 用同一个 owner 认领两个，读一读错误信息。

## 下一章

继续阅读 [s11: 后台任务](../s11_background_tasks/README.zh.md)。
