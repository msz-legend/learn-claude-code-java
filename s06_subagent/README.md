# s06: Subagent

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s05: TodoWrite](../s05_todo_write/) → **s06** → [s07: Skill Loading](../s07_skill_loading/)

> Give noisy investigation its own context and return only the conclusion.

---

## The Problem

Every file read and dead end fills the parent context. A child agent can absorb that noise, but only if its result comes back as a compact summary.

## How It Works

1. Start the child with a fresh message list and a written brief.
2. Give the child the tools it needs, not the parent's whole history.
3. Return only the final text and cap the child's turns.

## Code Map

| File | Role |
|---|---|
| `s06_subagent/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Agents.java`, `harness/AgentLoop.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s06_subagent.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Delegate a repository survey and compare context size with doing it inline.
- Lower SUBAGENT_MAX_TURNS and watch the child stop early.

## Next

Continue to [s07: Skill Loading](../s07_skill_loading/README.md).
