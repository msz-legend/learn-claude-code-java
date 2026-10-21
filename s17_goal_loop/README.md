# s17: Goal Loop

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s16: Workflow Runtime](../s16_workflow_runtime/) → **s17** → End

> An independent evaluator decides whether the work is actually done.

---

## The Problem

The worker decides when it is done, which is the wrong judge. A confident summary is not evidence, and stopping early is the default failure.

## How It Works

1. Let the worker end its turn as usual.
2. Send the transcript to an evaluator with no tools and a strict rubric.
3. Accept, reject with a reason, or declare the goal impossible; cap evaluations.

## Code Map

| File | Role |
|---|---|
| `s17_goal_loop/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Goal.java`, `harness/AgentLoop.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s17_goal_loop.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run with a goal that needs a real command, such as making tests pass.
- Give an impossible goal and watch the evaluator stop the loop.

## Next

That is the end of the course. Re-read s01 with the full harness in mind.
