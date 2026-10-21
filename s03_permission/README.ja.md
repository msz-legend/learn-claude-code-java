# s03: 権限システム

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s02: ツール使用](../s02_tool_use/) → **s03** → [s04: フックシステム](../s04_hooks/)

> 実行前に操作を検査し、判断結果をモデルに見える形で返す。

---

## 課題

ゲートのないツール表は破壊的なコマンドも平気で実行する。実行後の警告では遅く、ポリシーは呼び出しの前に置く必要がある。

## 仕組み

1. 決して実行させないパターンをハード拒否する。
2. 緩いルールは ask として人間の判断に回す。
3. 例外ではなくブロックされた tool_result を返し、モデルが別の手を選べるようにする。

## コードマップ

| File | Role |
|---|---|
| `s03_permission/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Permission.java`, `harness/AgentLoop.java` | 全章で共有する実装。 |

## 実行

```bash
java -cp out s03_permission.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- /rules を実行し、その後で一時ディレクトリを削除させる。
- PERMISSION_MODE=allow にして挙動を比べる。

## 次章

次は [s04: フックシステム](../s04_hooks/README.ja.md) へ。
