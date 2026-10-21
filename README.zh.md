# learn-claude-code（Java 版）

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

这是 [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) 的 Java 21 移植版，共 17 章。课程从最朴素的循环出发，逐步加入权限、钩子、记忆、任务、团队、工作流和目标评估器。

这个移植**不依赖任何第三方库**：只需要 JDK 21、`javac` 和 `java`。JSON、HTTP、Anthropic 客户端和 OpenAI 兼容客户端都在 `harness/` 里手写实现。

> 本仓库是原 Python 课程的平行版本，不取代原项目。先在这里读机制，需要最短实现时再对照 Python 源码。

---

## 环境要求

- JDK 21 或更高版本（`java -version`、`javac -version`）
- PowerShell 7、Windows PowerShell 5.1，或 POSIX shell
- 一个模型提供方：Anthropic API Key，或任意 OpenAI 兼容端点

## 快速开始

```powershell
Copy-Item .env.example .env
# 编辑 .env：设置 LLM_PROTOCOL 和一个 API Key

.\build.ps1
java -cp out harness.SelfTest
.\run.ps1 s01_agent_loop.Main
```

macOS 或 Linux 上不用 PowerShell：

```bash
cp .env.example .env
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name '*.java' -not -path './out/*')
java -cp out harness.SelfTest
java -cp out s01_agent_loop.Main
```

每一章都接受 `--help`，不连接模型也能看到该章的工具和可调参数。

## 模型提供方

`LLM_PROTOCOL` 决定使用哪种协议。不设置时，运行框架会根据存在的 API Key 推断。

| 协议 | 必需变量 | 常见 Base URL | 模型变量 |
|---|---|---|---|
| `anthropic` | `ANTHROPIC_API_KEY` | `https://api.anthropic.com` | `ANTHROPIC_MODEL` |
| `openai` | `OPENAI_API_KEY` | `https://api.openai.com/v1` | `OPENAI_MODEL` |

`MODEL_ID` 会覆盖上面两个模型变量。OpenAI 兼容配置示例：

| 提供方 | `OPENAI_BASE_URL` | `MODEL_ID` |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` |
| Qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| Ollama | `http://localhost:11434/v1` | `llama3.2` |

## 目录结构

```
harness/                 共享实现
  AgentLoop.java         唯一的循环
  AnthropicClient.java   Messages API 客户端
  OpenAiClient.java      OpenAI 兼容客户端
  StandardTools.java     bash、read_file、write_file、edit_file、glob
  Permission.java        deny / ask / allow 策略
  Hooks.java             四个钩子位置
  ContextCompactor.java  归档、截断、摘要
  TaskStore.java         文件支撑的任务图
  Team.java              信箱与队友
  Workflow.java          带日志的步骤运行器
  Goal.java              停止边界上的独立评估器

s01_agent_loop/          每章一个 Main.java 入口
s02_tool_use/
...
s17_goal_loop/
skills/                  s07 和 s15 使用的 SKILL.md 示例
```

运行时状态写在 `.memory/`、`.tasks/`、`.cron/`、`.compact/`、`.team/` 和 `.workflows/`，这些目录已被 git 忽略。

## 章节

| 章节 | 新增内容 |
|---|---|
| [s01 智能体循环](s01_agent_loop/README.zh.md) | 模型 → 工具 → 结果 → 继续，只有一个 bash 工具。 |
| [s02 工具调用](s02_tool_use/README.zh.md) | 带类型的调度表，文件工具限制在工作区内。 |
| [s03 权限系统](s03_permission/README.zh.md) | 工具执行前的 deny/ask/allow 检查。 |
| [s04 钩子系统](s04_hooks/README.zh.md) | 输入、工具前、工具后和停止钩子。 |
| [s05 待办计划](s05_todo_write/README.zh.md) | 运行框架持有的计划，以及计划过期提醒。 |
| [s06 子智能体](s06_subagent/README.zh.md) | 独立上下文的子循环，只返回文本。 |
| [s07 技能加载](s07_skill_loading/README.zh.md) | 提示词里放目录，正文按需加载。 |
| [s08 上下文压缩](s08_context_compact/README.zh.md) | 分层压缩，保留交接摘要。 |
| [s09 记忆系统](s09_memory/README.zh.md) | 长期事实的选择、提取与合并。 |
| [s10 任务系统](s10_task_system/README.zh.md) | 磁盘上的依赖图与原子认领。 |
| [s11 后台任务](s11_background_tasks/README.zh.md) | 慢命令移出线程，以通知送回。 |
| [s12 定时调度](s12_cron_scheduler/README.zh.md) | 持久化未来提示，五段式解析。 |
| [s13 智能体团队](s13_agent_teams/README.zh.md) | 队友、共享任务板、追加式信箱。 |
| [s14 MCP 插件](s14_mcp_plugin/README.zh.md) | stdio JSON-RPC 工具发现与命名空间。 |
| [s15 集成式运行框架](s15_integrated_harness/README.zh.md) | 所有机制围绕同一个循环组合。 |
| [s16 工作流运行时](s16_workflow_runtime/README.zh.md) | 宿主掌握的步骤，可恢复的日志。 |
| [s17 目标循环](s17_goal_loop/README.zh.md) | 停止边界上的独立评估器。 |

## 设计说明

- **只有一个循环。** 每章最终都走到同一个 `AgentLoop`。章节入口只负责选择工具、组合各个端口。
- **消息模型与提供方无关。** `Message.Block` 表示文本、工具调用和工具结果，各客户端再翻译成自己的 JSON。
- **失败也是消息。** 被阻止的工具和未知工具都会返回带错误标记的 `tool_result`，模型可以改路，而不是让循环崩溃。
- **状态就是普通文件。** 没有数据库，也没有隐藏进程。运行框架记住的东西都可以查看、删除和对比。
- **教学代码保持小。** 只在会静默损坏状态的地方做错误处理，不堆生产级防御。

## 移植说明

原 Python 课程每章一个独立脚本。这个移植让章节入口保持很薄，共享同一套运行框架，避免把同一个机制复制 17 份。某章需要更小的工具面时，单独构造一个注册表。

为了贴近 Python 原版，代码里使用了文本块和 record。手写 JSON 层是为了保持零依赖，不是为了遮掩语言特性。

## 许可证

MIT。原始课程与设计归 shareAI Lab。见 [LICENSE](LICENSE)。
