# s07: 技能加载

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s06: 子智能体](../s06_subagent/) → **s07** → [s08: 上下文压缩](../s08_context_compact/)

> 只公布目录，任务匹配时才读取正文。

---

## 问题

把所有说明都塞进系统提示词，会让这一轮永远用不到的技能占用上下文。模型只需要知道有什么、怎么取。

## 工作机制

1. 扫描技能目录里的 SKILL.md，读取 front matter。
2. 把名称和一行描述放进提示词。
3. 提供 load_skill，按需读取完整正文。

## 代码地图

| File | Role |
|---|---|
| `s07_skill_loading/Main.java` | 本章入口：把这个机制接进同一个循环。 |
| `harness/Skills.java` | 每章共用的实现。 |

## 运行

```bash
java -cp out s07_skill_loading.Main
```

首次运行前把 .env.example 复制为 .env，填入一个提供方的配置。没有密钥时 --help 仍可使用。

## 动手试试

- 运行 /skills，再请求代码审查，观察 load_skill 被调用。
- 新增一个 SKILL.md，确认无需重启就能出现。

## 下一章

继续阅读 [s08: 上下文压缩](../s08_context_compact/README.zh.md)。
