# s03: Permission System

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s02: Tool Use](../s02_tool_use/) → **s03** → [s04: Hook System](../s04_hooks/)

> Check the action before it runs, and keep the decision visible to the model.

---

## The Problem

A tool table with no gate will run destructive commands without hesitation. Warnings after execution are too late; the policy has to sit before the call.

## How It Works

1. Hard-deny patterns that should never run.
2. Match softer rules and mark them ask, so a human can decide.
3. Return a blocked tool_result instead of throwing, so the model can recover.

## Code Map

| File | Role |
|---|---|
| `s03_permission/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Permission.java`, `harness/AgentLoop.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s03_permission.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run /rules, then ask it to delete a scratch directory.
- Set PERMISSION_MODE=allow and compare the behavior.

## Next

Continue to [s04: Hook System](../s04_hooks/README.md).
