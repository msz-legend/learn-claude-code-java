# s11: 后台任务

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s10: 任务系统](../s10_task_system/) → **s11** → [s12: 定时调度](../s12_cron_scheduler/)

> 把慢命令移出对话线程，稍后送回结果。

---

## 问题

长时间构建会堵住对话，模型在等待期间无事可做。这个等待往往没有必要。

## 工作机制

1. 用 goroutine 或线程启动命令，立刻返回任务 id。
2. 在任务表里保存状态和输出。
3. 每个完成的任务只注入一次，作为运行框架通知。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s11_background_tasks/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Background.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s11_background_tasks.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 后台运行一个 sleep 命令，同时继续对话。
- 在任务结束前后分别运行 /jobs。

## 下一章

继续阅读 [s12: 定时调度](../s12_cron_scheduler/README.zh.md)。
