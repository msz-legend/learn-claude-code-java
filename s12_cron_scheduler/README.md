# s12: Cron Scheduler

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s11: Background Tasks](../s11_background_tasks/) → **s12** → [s13: Agent Teams](../s13_agent_teams/)

> Persist future prompts and turn them into new turns while the process runs.

---

## The Problem

Some work should happen later, not now. A timer inside one conversation disappears when that conversation ends.

## How It Works

1. Parse five-field expressions and validate the ranges.
2. Persist jobs and record the last run key so a restart does not repeat them.
3. Poll, queue due prompts, and inject them before the next request.

## Code Map

| File | Role |
|---|---|
| `s12_cron_scheduler/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Cron.java` | Shared implementation used by every chapter. |
| `.cron/` | State written by this chapter. |

## Run

```bash
java -cp out s12_cron_scheduler.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Schedule a one-shot prompt a minute ahead and wait for it.
- Restart the process and run /cron to confirm the job survived.

## Next

Continue to [s13: Agent Teams](../s13_agent_teams/README.md).
