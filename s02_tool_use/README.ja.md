# s02: ツール使用

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s01: エージェントループ](../s01_agent_loop/) → **s02** → [s03: 権限システム](../s03_permission/)

> ディスパッチテーブルは、モデルの意図を監査可能な小さな行動群に変える。

---

## 課題

シェル1つで何でもできるが、各操作は不透明で確認しにくい。ツール表は可能な操作を明示し、ファイルパスをワークスペース内に閉じ込める。

## 仕組み

1. 各ツールに名前、説明、JSON スキーマを持たせる。
2. 名前だけでディスパッチし、モデルが渡した文字列を eval しない。
3. すべてのパスをワークスペース基準で解決し、外への脱出を拒否する。

## コードマップ

| File | Role |
|---|---|
| `s02_tool_use/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/StandardTools.java`, `harness/ToolRegistry.java`, `harness/Workspace.java` | 全章で共有する実装。 |

## 実行

```bash
java -cp out s02_tool_use.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- /tools を実行し、モデルが見るスキーマと比べる。
- ../README.md を読ませて、拒否されることを確認する。

## 次章

次は [s03: 権限システム](../s03_permission/README.ja.md) へ。
