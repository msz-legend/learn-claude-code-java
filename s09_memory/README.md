# s09: Memory System

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s08: Context Compact](../s08_context_compact/) → **s09** → [s10: Task System](../s10_task_system/)

> A small durable store with selection, extraction, and consolidation.

---

## The Problem

Each session starts from zero, so the same project facts get rediscovered. Stuffing everything into the prompt only moves the problem.

## How It Works

1. Select relevant entries before a request instead of dumping the store.
2. Extract a few durable facts from a finished exchange.
3. Consolidate duplicates and cap the store so it stays useful.

## Code Map

| File | Role |
|---|---|
| `s09_memory/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Memory.java` | Shared implementation used by every chapter. |
| `.memory/` | State written by this chapter. |

## Run

```bash
java -cp out s09_memory.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Remember a build command, restart, and recall it.
- Add near-duplicates and run consolidate_memory.

## Next

Continue to [s10: Task System](../s10_task_system/README.md).
