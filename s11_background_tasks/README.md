# s11: Background Tasks

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s10: Task System](../s10_task_system/) → **s11** → [s12: Cron Scheduler](../s12_cron_scheduler/)

> Move slow commands off the conversation thread and deliver the result later.

---

## The Problem

A long build blocks the conversation, and the model has nothing to do while it waits. The wait is often unnecessary.

## How It Works

1. Start the command in a goroutine or thread and return an id immediately.
2. Keep status and output in a job table.
3. Inject each completed job once as a harness notification.

## Code Map

| File | Role |
|---|---|
| `s11_background_tasks/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Background.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s11_background_tasks.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run a sleep command in the background and keep chatting.
- Run /jobs before and after the job finishes.

## Next

Continue to [s12: Cron Scheduler](../s12_cron_scheduler/README.md).
