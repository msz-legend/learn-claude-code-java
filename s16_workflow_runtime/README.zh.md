# s16: 工作流运行时

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s15: 集成式运行框架](../s15_integrated_harness/) → **s16** → [s17: 目标循环](../s17_goal_loop/)

> 步骤由宿主掌握；模型只选名字并完成工作。

---

## 问题

自由智能体在运行时决定下一步，长流程因此难以恢复、也难以审计。有些工作本来就有已知形状。

## 工作机制

1. 把工作流定义为带明确阶段的有序步骤。
2. 把每次智能体调用和步骤结果记到 .workflows/<run>/。
3. 重放日志、跳过已完成步骤，实现恢复。

## 代码地图

| 文件 | 作用 |
|---|---|
| `s16_workflow_runtime/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Workflow.java`, `harness/AgentLoop.java` | 每章共用的实现。 |
| `.workflows/` | 本章写入的状态。 |

## 运行

```bash
java -cp out s16_workflow_runtime.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 运行 /run review，再用同一个 run id 重跑。
- 并行跑两个审查，对比日志内容。

## 下一章

继续阅读 [s17: 目标循环](../s17_goal_loop/README.zh.md)。
