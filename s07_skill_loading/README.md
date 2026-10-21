# s07: Skill Loading

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s06: Subagent](../s06_subagent/) → **s07** → [s08: Context Compact](../s08_context_compact/)

> Advertise a catalog, load the body only when the task matches.

---

## The Problem

Pasting every instruction into the system prompt spends context on skills the turn will never use. The model only needs to know what exists and how to ask.

## How It Works

1. Scan skill folders for SKILL.md and read the front matter.
2. Put names and one-line descriptions in the prompt.
3. Expose load_skill so the full body is fetched on demand.

## Code Map

| File | Role |
|---|---|
| `s07_skill_loading/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Skills.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s07_skill_loading.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run /skills, then ask for a code review and watch load_skill fire.
- Add a new SKILL.md and confirm it appears without a restart.

## Next

Continue to [s08: Context Compact](../s08_context_compact/README.md).
