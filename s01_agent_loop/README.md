# s01: The Agent Loop

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

Start → **s01** → [s02: Tool Use](../s02_tool_use/)

> One loop and one shell tool are enough to turn a chat model into an agent.

---

## The Problem

The model can propose a shell command but stops after emitting it. Without a harness, a human has to run every command and paste the result back.

## How It Works

1. Send the conversation and the tool specs to the model.
2. Append the assistant blocks; if there is no tool_use block, return the text.
3. Run every tool_use, append one tool_result per call, and loop.

## Code Map

| File | Role |
|---|---|
| `s01_agent_loop/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/AgentLoop.java`, `harness/Cli.java`, `harness/Workspace.java` | Shared implementation used by every chapter. |

## Run

```bash
java -cp out s01_agent_loop.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Ask it to list the workspace and then read the largest file.
- Ask a question that needs no tool and confirm the loop exits after one turn.

## Next

Continue to [s02: Tool Use](../s02_tool_use/README.md).
