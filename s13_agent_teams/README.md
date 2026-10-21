# s13: Agent Teams

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s12: Cron Scheduler](../s12_cron_scheduler/) → **s13** → [s14: MCP Plugin](../s14_mcp_plugin/)

> Persistent teammates with separate contexts, a shared board, and mailboxes.

---

## The Problem

One agent cannot explore independent branches at the same time. Spawning helpers is easy; coordinating them without collisions is the hard part.

## How It Works

1. Give each teammate a name, a role, its own loop, and an optional worktree.
2. Share the task board and claim atomically so two teammates never take the same task.
3. Use append-only mailboxes and cursors so messages are delivered once.

## Code Map

| File | Role |
|---|---|
| `s13_agent_teams/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Team.java`, `harness/TaskStore.java` | Shared implementation used by every chapter. |
| `.team/` | State written by this chapter. |

## Run

```bash
java -cp out s13_agent_teams.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Spawn one teammate for a small task and read the lead inbox.
- Run /team while the teammate is still working.

## Next

Continue to [s14: MCP Plugin](../s14_mcp_plugin/README.md).
