# s02: Tool Use

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s01: The Agent Loop](../s01_agent_loop/) → **s02** → [s03: Permission System](../s03_permission/)

> A dispatch table turns the model's intentions into a small, auditable set of actions.

---

## The Problem

One shell can do anything, but every action is opaque and hard to check. A tool table makes the available actions explicit and keeps file paths inside the workspace.

## How It Works

1. Give each tool a name, a description, and a JSON schema.
2. Dispatch strictly by name; never eval a model-provided string.
3. Resolve every path against the workspace root and reject escapes.

## Code Map

| File | Role |
|---|---|
| `s02_tool_use/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/StandardTools.java`, `harness/ToolRegistry.java`, `harness/Workspace.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s02_tool_use.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run /tools and compare the list with the schemas the model sees.
- Ask it to read ../README.md and observe the refusal.

## Next

Continue to [s03: Permission System](../s03_permission/README.md).
