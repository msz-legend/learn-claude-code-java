# s16: ワークフローランタイム

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s15: 統合ハーネス](../s15_integrated_harness/) → **s16** → [s17: ゴールループ](../s17_goal_loop/)

> 手順はホストが持ち、モデルは名前を選んで仕事を埋める。

---

## 課題

自由なエージェントは実行時に次の一手を決めるため、長い流れは再開も監査も難しい。作業によっては形が最初から決まっている。

## 仕組み

1. ワークフローを明示的なフェーズ付きの順序付きステップとして定義する。
2. 各エージェント呼び出しとステップ結果を .workflows/<run>/ に記録する。
3. ジャーナルを再生し、完了済みステップを飛ばして再開する。

## コードマップ

| File | Role |
|---|---|
| `s16_workflow_runtime/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Workflow.java`, `harness/AgentLoop.java` | 全章で共有する実装。 |
| `.workflows/` | 本章が書き出す状態。 |

## 実行

```bash
java -cp out s16_workflow_runtime.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- /run review を実行し、同じ run id でもう一度走らせる。
- 2つのレビューを並列で走らせ、ジャーナルを比べる。

## 次章

次は [s17: ゴールループ](../s17_goal_loop/README.ja.md) へ。
