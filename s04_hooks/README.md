# s04: Hook System

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s03: Permission System](../s03_permission/) → **s04** → [s05: TodoWrite](../s05_todo_write/)

> Wrap the loop with policy and observation instead of editing it.

---

## The Problem

Adding a guard or a log line by editing the loop makes every new concern a change to the core. Hooks give the same points a stable name.

## How It Works

1. Run a hook on the user prompt, before a tool, after a tool, and on stop.
2. Let a pre-tool hook block with a reason the model can read.
3. Thread post-tool output through every hook so later hooks see earlier edits.

## Code Map

| File | Role |
|---|---|
| `s04_hooks/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Hooks.java`, `harness/ShellHooks.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s04_hooks.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Ask it to write .env and watch the secrets hook block the call.
- Add a stop hook that prints the message count.

## Next

Continue to [s05: TodoWrite](../s05_todo_write/README.md).
