# s08: コンテキスト圧縮

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s07: スキル読み込み](../s07_skill_loading/) → **s08** → [s09: メモリシステム](../s09_memory/)

> 安い層から順に圧縮し、引き継ぎメモを残す。

---

## 課題

会話はウィンドウを超える。古いメッセージを捨てると決定が消え、全部残すとリクエストが通らない。ハーネスには中間の道が要る。

## 仕組み

1. 大きすぎるツール結果をアーカイブし、パスとプレビューを残す。
2. 会話の形を変える前に、古いツール出力を切り詰める。
3. 古いターンを要約し、直近を残し、必要ならもう一度切り詰める。

## コードマップ

| File | Role |
|---|---|
| `s08_context_compact/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Compactor.java`, `harness/AgentLoop.java` | 全章で共有する実装。 |
| `.compact/` | 本章が書き出す状態。 |

## 実行

```bash
java -cp out s08_context_compact.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- COMPACT_SUMMARIZE_AT_TOKENS=1500 と COMPACT_KEEP_MESSAGES=2 を設定する。
- 大きなファイルを読む前後で /context を実行する。

## 次章

次は [s09: メモリシステム](../s09_memory/README.ja.md) へ。
