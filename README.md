# learn-claude-code (Java)

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

A 17-chapter port of [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) to Java 21. The course builds one coding agent from a bare loop to permissions, hooks, memory, tasks, teams, workflows, and a goal evaluator.

The port has **no third-party dependencies**: JDK 21, `javac`, and `java` only. JSON, HTTP, the Anthropic client, and the OpenAI-compatible client are all in `harness/`.

> This repository is a sibling of the original Python course, not a replacement. Read a chapter's concept here, then compare it with the Python source when you want the shortest version.

---

## Requirements

- JDK 21 or newer (`java -version`, `javac -version`)
- PowerShell 7, Windows PowerShell 5.1, or a POSIX shell
- One model provider: an Anthropic API key, or any OpenAI-compatible endpoint

## Quick start

```powershell
Copy-Item .env.example .env
# edit .env: set LLM_PROTOCOL and one API key

.\build.ps1
java -cp out harness.SelfTest
.\run.ps1 s01_agent_loop.Main
```

On macOS or Linux, the same steps without PowerShell:

```bash
cp .env.example .env
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name '*.java' -not -path './out/*')
java -cp out harness.SelfTest
java -cp out s01_agent_loop.Main
```

Every chapter accepts `--help` and prints its tools and tuning knobs without contacting a model.

## Providers

`LLM_PROTOCOL` selects the wire format. If it is unset, the harness infers the protocol from whichever key is present.

| Protocol | Required | Typical base URL | Model variable |
|---|---|---|---|
| `anthropic` | `ANTHROPIC_API_KEY` | `https://api.anthropic.com` | `ANTHROPIC_MODEL` |
| `openai` | `OPENAI_API_KEY` | `https://api.openai.com/v1` | `OPENAI_MODEL` |

`MODEL_ID` overrides both model variables. OpenAI-compatible examples:

| Provider | `OPENAI_BASE_URL` | `MODEL_ID` |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` |
| Qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| Ollama | `http://localhost:11434/v1` | `llama3.2` |

## Layout

```
harness/                 shared implementation
  AgentLoop.java         the one loop
  AnthropicClient.java   Messages API client
  OpenAiClient.java      OpenAI-compatible client
  StandardTools.java     bash, read_file, write_file, edit_file, glob
  Permission.java        deny / ask / allow policy
  Hooks.java             four hook points
  ContextCompactor.java  archive, truncate, summarize
  TaskStore.java         file-backed task graph
  Team.java              mailboxes and teammates
  Workflow.java          journaled step runner
  Goal.java              independent stop-boundary evaluator

s01_agent_loop/          each chapter has a Main.java entry point
s02_tool_use/
...
s17_goal_loop/
skills/                  SKILL.md examples for s07 and s15
```

State created at runtime lives under `.memory/`, `.tasks/`, `.cron/`, `.compact/`, `.team/`, and `.workflows/`. These directories are ignored by git.

## Chapters

| Chapter | What it adds |
|---|---|
| [s01 Agent Loop](s01_agent_loop/README.md) | Model → tool → result → repeat, with one bash tool. |
| [s02 Tool Use](s02_tool_use/README.md) | A typed dispatch table and workspace-confined file tools. |
| [s03 Permission](s03_permission/README.md) | Deny/ask/allow checks before a tool runs. |
| [s04 Hooks](s04_hooks/README.md) | Prompt, pre-tool, post-tool, and stop hooks. |
| [s05 TodoWrite](s05_todo_write/README.md) | A harness-owned plan with stale-plan reminders. |
| [s06 Subagent](s06_subagent/README.md) | Fresh-context child loops that return only text. |
| [s07 Skill Loading](s07_skill_loading/README.md) | Catalog in the prompt, body loaded on demand. |
| [s08 Context Compact](s08_context_compact/README.md) | Layered compaction with a handoff summary. |
| [s09 Memory](s09_memory/README.md) | Durable facts with selection, extraction, consolidation. |
| [s10 Task System](s10_task_system/README.md) | Dependency graph and atomic claims on disk. |
| [s11 Background Tasks](s11_background_tasks/README.md) | Slow commands off-thread, delivered as notifications. |
| [s12 Cron Scheduler](s12_cron_scheduler/README.md) | Persisted future prompts with a five-field parser. |
| [s13 Agent Teams](s13_agent_teams/README.md) | Teammates, shared board, append-only mailboxes. |
| [s14 MCP Plugin](s14_mcp_plugin/README.md) | stdio JSON-RPC tool discovery and namespacing. |
| [s15 Integrated Harness](s15_integrated_harness/README.md) | All mechanisms composed around one loop. |
| [s16 Workflow Runtime](s16_workflow_runtime/README.md) | Host-owned steps with a resumable journal. |
| [s17 Goal Loop](s17_goal_loop/README.md) | An independent evaluator at the stop boundary. |

## Design notes

- **One loop.** Every chapter reaches the same `AgentLoop`. A chapter entry point only chooses tools and composes the ports.
- **Provider-neutral messages.** `Message.Block` models text, tool use, and tool results; each client translates to its own JSON shape.
- **Failures are messages.** Blocked tools and unknown tools return a `tool_result` with an error flag, so the model can recover instead of crashing the loop.
- **Durable state is plain files.** No database, no hidden daemon. You can inspect, delete, or diff everything the harness remembers.
- **Teaching code stays small.** Error handling is present where it prevents silent corruption, not as production hardening.

## Port notes

The Python course gives each chapter a standalone script. This port keeps chapter entry points thin and shares one harness, so the same mechanism is not copied 17 times. When a chapter needs a smaller tool surface, it builds a separate registry.

Text blocks and records are used where they keep the code close to the Python original. The hand-written JSON layer exists so the project stays dependency-free, not to hide the language.

## License

MIT. Original course and design by shareAI Lab. See [LICENSE](LICENSE).
