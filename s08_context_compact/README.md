# s08: Context Compact

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s07: Skill Loading](../s07_skill_loading/) → **s08** → [s09: Memory System](../s09_memory/)

> Compress in layers, cheapest first, and keep a handoff note.

---

## The Problem

Conversations outgrow the window. Dropping old messages loses decisions; keeping everything fails the request. The harness needs a middle path.

## How It Works

1. Archive oversized tool results and leave a path plus a preview.
2. Truncate old tool output before touching the conversation shape.
3. Summarize older turns, keep recent messages, and repeat truncation if needed.

## Code Map

| File | Role |
|---|---|
| `s08_context_compact/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Compactor.java`, `harness/AgentLoop.java` | Shared implementation used by every chapter. |
| `.compact/` | State written by this chapter. |

## Run

```bash
java -cp out s08_context_compact.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Set COMPACT_SUMMARIZE_AT_TOKENS=1500 and COMPACT_KEEP_MESSAGES=2.
- Run /context before and after a long file read.

## Next

Continue to [s09: Memory System](../s09_memory/README.md).
