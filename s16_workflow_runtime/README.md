# s16: Workflow Runtime

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s15: Integrated Harness](../s15_integrated_harness/) → **s16** → [s17: Goal Loop](../s17_goal_loop/)

> The host owns the steps; the model chooses a name and fills the work.

---

## The Problem

Free-form agents choose their next step at runtime, which makes a long flow hard to resume and hard to audit. Some work has a known shape.

## How It Works

1. Define workflows as ordered steps with explicit phases.
2. Journal every agent call and step result under .workflows/<run>/.
3. Resume by replaying the journal and skipping completed steps.

## Code Map

| File | Role |
|---|---|
| `s16_workflow_runtime/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Workflow.java`, `harness/AgentLoop.java` | Shared implementation used by every chapter. |
| `.workflows/` | State written by this chapter. |

## Run

```bash
java -cp out s16_workflow_runtime.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run /run review, then rerun with the same run id.
- Run two reviews in parallel and compare the journal.

## Next

Continue to [s17: Goal Loop](../s17_goal_loop/README.md).
