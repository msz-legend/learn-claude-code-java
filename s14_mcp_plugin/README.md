# s14: MCP Plugin

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s13: Agent Teams](../s13_agent_teams/) → **s14** → [s15: Integrated Harness](../s15_integrated_harness/)

> Discover external tools over a standard protocol and expose them beside local tools.

---

## The Problem

Useful tools already live in other processes and languages. Re-implementing each one inside the harness does not scale.

## How It Works

1. Start the server as a child process and speak newline-delimited JSON-RPC.
2. Initialize, list tools, and register them as mcp__server__tool.
3. Call through the same registry and trust the remote schema.

## Code Map

| File | Role |
|---|---|
| `s14_mcp_plugin/Main.java` | Chapter entry point: wires this mechanism into one loop. |
| `harness/Mcp.java` | Shared implementation used by every chapter. |
| `s14_mcp_plugin/DemoMcpServer.java` | Bundled demo server used when mcp.json is absent. |

## Run

```bash
java -cp out s14_mcp_plugin.Main
```

Copy .env.example to .env and fill in one provider before the first run. --help works without credentials.

## Try This

- Run the chapter offline with the bundled demo server.
- Run /mcp, then ask it to echo a string and add two numbers.

## Next

Continue to [s15: Integrated Harness](../s15_integrated_harness/README.md).
