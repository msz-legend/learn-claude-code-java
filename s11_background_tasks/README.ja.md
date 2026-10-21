# s11: バックグラウンドタスク

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s10: タスクシステム](../s10_task_system/) → **s11** → [s12: Cron スケジューラ](../s12_cron_scheduler/)

> 遅いコマンドを会話スレッドの外に出し、結果を後で届ける。

---

## 課題

長いビルドは会話を塞ぎ、モデルは待つ間なにもできない。その待ち時間は多くの場合不要だ。

## 仕組み

1. goroutine かスレッドでコマンドを開始し、すぐに id を返す。
2. ジョブ表に状態と出力を保持する。
3. 完了したジョブを1回だけハーネス通知として注入する。

## コードマップ

| File | Role |
|---|---|
| `s11_background_tasks/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Background.java` | 全章で共有する実装。 |

## 実行

```bash
java -cp out s11_background_tasks.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- sleep コマンドをバックグラウンドで走らせ、会話を続ける。
- ジョブ完了の前後で /jobs を実行する。

## 次章

次は [s12: Cron スケジューラ](../s12_cron_scheduler/README.ja.md) へ。
