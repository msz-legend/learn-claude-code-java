# s10: Task System

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s09: Memory System](../s09_memory/) → **s10** → [s11: Background Tasks](../s11_background_tasks/)

> A file-backed graph with atomic claims, shared by every agent.

---

## The Problem

Multiple agents need work state that survives a crash and does not double-assign. An in-memory list cannot provide either.

## How It Works

1. Store one JSON file per task and create it atomically.
2. Model dependencies with blockedBy and refuse claims until they clear.
3. Claim under a file lock and allow one in-progress task per owner.

## Code Map

| File | Role |
|---|---|
| `s10_task_system/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/TaskStore.java` | Shared implementation used by every chapter. |
| `.tasks/` | State written by this chapter. |

## Run

```bash
java -cp out s10_task_system.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Create two tasks where the second blocks on the first.
- Try to claim both from the same owner and read the error.

## Next

Continue to [s11: Background Tasks](../s11_background_tasks/README.md).
