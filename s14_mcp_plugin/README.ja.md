# s14: MCP プラグイン

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s13: エージェントチーム](../s13_agent_teams/) → **s14** → [s15: 統合ハーネス](../s15_integrated_harness/)

> 標準プロトコルで外部ツールを発見し、ローカルツールの隣に並べる。

---

## 課題

有用なツールはすでに別のプロセスや言語にある。ハーネス内で1つずつ作り直すのはスケールしない。

## 仕組み

1. サーバーを子プロセスとして起動し、改行区切りの JSON-RPC で話す。
2. 初期化し、ツールを一覧し、mcp__server__tool として登録する。
3. 同じレジストリ経由で呼び、リモートのスキーマを信頼する。

## コードマップ

| File | Role |
|---|---|
| `s14_mcp_plugin/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Mcp.java` | 全章で共有する実装。 |
| `s14_mcp_plugin/DemoMcpServer.java` | mcp.json がない場合に使う同梱デモサーバー。 |

## 実行

```bash
java -cp out s14_mcp_plugin.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- 同梱のデモサーバーでオフライン実行する。
- /mcp を実行し、文字列のエコーと整数の加算を頼む。

## 次章

次は [s15: 統合ハーネス](../s15_integrated_harness/README.ja.md) へ。
