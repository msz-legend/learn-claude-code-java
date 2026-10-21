# s15: Integrated Harness

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s14: MCP Plugin](../s14_mcp_plugin/) → **s15** → [s16: Workflow Runtime](../s16_workflow_runtime/)

> One loop, every port: the mechanisms are only interesting together.

---

## The Problem

Each mechanism is small on its own. The real harness question is how permissions, plans, memory, tasks, notifications, and compaction share one turn.

## How It Works

1. Wire every tool into one registry and keep child tools smaller.
2. Compose permission, hooks, compaction, and notifications around one loop.
3. Expose /status so the operator can see what the harness holds.

## Code Map

| File | Role |
|---|---|
| `s15_integrated_harness/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/AgentLoop.java`, `harness/Todo.java`, `harness/Skills.java`, `harness/Memory.java`, `harness/TaskStore.java`, `harness/Background.java`, `harness/Cron.java`, `harness/Compactor.java`, `harness/Agents.java` | Shared implementation used by every chapter. |
| `.tasks/, .memory/, .cron/, .compact/` | State written by this chapter. |

## Run

```bash
java -cp out s15_integrated_harness.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Ask for a multi-step task and inspect /status and /tasks.
- Run a slow command, ask a question, and let the notification arrive.

## Next

Continue to [s16: Workflow Runtime](../s16_workflow_runtime/README.md).
