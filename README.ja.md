# learn-claude-code（Java 版）

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) を Java 21 に移植した全17章です。素朴なループから始め、権限、フック、メモリ、タスク、チーム、ワークフロー、ゴール評価へと積み上げます。

この移植は**外部ライブラリに依存しません**。必要なのは JDK 21、`javac`、`java` だけです。JSON、HTTP、Anthropic クライアント、OpenAI 互換クライアントはすべて `harness/` に手書きで入っています。

> このリポジトリは元の Python コースの並行版であり、置き換えではありません。ここで仕組みを読み、最短の実装を見たいときに Python 版と比べてください。

---

## 必要なもの

- JDK 21 以降（`java -version`、`javac -version`）
- PowerShell 7、Windows PowerShell 5.1、または POSIX シェル
- モデルプロバイダー: Anthropic API キー、または任意の OpenAI 互換エンドポイント

## クイックスタート

```powershell
Copy-Item .env.example .env
# .env を編集: LLM_PROTOCOL と API キーを1つ設定

.\build.ps1
java -cp out harness.SelfTest
.\run.ps1 s01_agent_loop.Main
```

macOS / Linux では PowerShell なしで同じ手順です。

```bash
cp .env.example .env
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name '*.java' -not -path './out/*')
java -cp out harness.SelfTest
java -cp out s01_agent_loop.Main
```

各章は `--help` を受け付け、モデルに接続しなくてもツールと調整項目を表示します。

## プロバイダー

`LLM_PROTOCOL` がプロトコルを選びます。未設定の場合は、存在する API キーから推測します。

| プロトコル | 必須 | 既定の Base URL | モデル変数 |
|---|---|---|---|
| `anthropic` | `ANTHROPIC_API_KEY` | `https://api.anthropic.com` | `ANTHROPIC_MODEL` |
| `openai` | `OPENAI_API_KEY` | `https://api.openai.com/v1` | `OPENAI_MODEL` |

`MODEL_ID` は上の2つのモデル変数より優先されます。OpenAI 互換の例:

| プロバイダー | `OPENAI_BASE_URL` | `MODEL_ID` |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` |
| Qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| Ollama | `http://localhost:11434/v1` | `llama3.2` |

## 構成

```
harness/                 共有実装
  AgentLoop.java         唯一のループ
  AnthropicClient.java   Messages API クライアント
  OpenAiClient.java      OpenAI 互換クライアント
  StandardTools.java     bash、read_file、write_file、edit_file、glob
  Permission.java        deny / ask / allow ポリシー
  Hooks.java             4つのフック位置
  ContextCompactor.java  アーカイブ、切り詰め、要約
  TaskStore.java         ファイル支えのタスクグラフ
  Team.java              メールボックスとメンバー
  Workflow.java          ジャーナル付きステップ実行
  Goal.java              停止境界の独立評価者

s01_agent_loop/          各章に Main.java の入口
s02_tool_use/
...
s17_goal_loop/
skills/                  s07 と s15 で使う SKILL.md の例
```

実行時の状態は `.memory/`、`.tasks/`、`.cron/`、`.compact/`、`.team/`、`.workflows/` に書かれ、git からは除外されています。

## 章一覧

| 章 | 追加される仕組み |
|---|---|
| [s01 エージェントループ](s01_agent_loop/README.ja.md) | モデル → ツール → 結果 → 繰り返し。ツールは bash のみ。 |
| [s02 ツール使用](s02_tool_use/README.ja.md) | 型付きディスパッチ表とワークスペース内のファイルツール。 |
| [s03 権限システム](s03_permission/README.ja.md) | 実行前の deny/ask/allow 検査。 |
| [s04 フックシステム](s04_hooks/README.ja.md) | 入力、ツール前、ツール後、停止のフック。 |
| [s05 Todo プラン](s05_todo_write/README.ja.md) | ハーネスが持つ計画と、古くなったときのリマインダー。 |
| [s06 サブエージェント](s06_subagent/README.ja.md) | 独立コンテキストの子ループ。返すのはテキストだけ。 |
| [s07 スキル読み込み](s07_skill_loading/README.ja.md) | プロンプトにはカタログ、本文は必要時に取得。 |
| [s08 コンテキスト圧縮](s08_context_compact/README.ja.md) | 層ごとの圧縮と引き継ぎ要約。 |
| [s09 メモリシステム](s09_memory/README.ja.md) | 長期事実の選択、抽出、統合。 |
| [s10 タスクシステム](s10_task_system/README.ja.md) | ディスク上の依存グラフと原子的クレーム。 |
| [s11 バックグラウンドタスク](s11_background_tasks/README.ja.md) | 遅いコマンドをスレッド外へ。通知で結果を届ける。 |
| [s12 Cron スケジューラ](s12_cron_scheduler/README.ja.md) | 未来のプロンプトを永続化。5フィールド解析。 |
| [s13 エージェントチーム](s13_agent_teams/README.ja.md) | メンバー、共有ボード、追記型メールボックス。 |
| [s14 MCP プラグイン](s14_mcp_plugin/README.ja.md) | stdio JSON-RPC のツール発見と名前空間。 |
| [s15 統合ハーネス](s15_integrated_harness/README.ja.md) | 全仕組みを1つのループの周りに構成。 |
| [s16 ワークフローランタイム](s16_workflow_runtime/README.ja.md) | ホストが持つ手順と、再開できるジャーナル。 |
| [s17 ゴールループ](s17_goal_loop/README.ja.md) | 停止境界に置く独立評価者。 |

## 設計メモ

- **ループは1つ。** どの章も最後は同じ `AgentLoop` に着きます。章の入口はツールを選び、ポートを組み合わせるだけです。
- **メッセージはプロバイダー非依存。** `Message.Block` がテキスト、ツール呼び出し、ツール結果を表し、各クライアントが自分の JSON に変換します。
- **失敗もメッセージ。** ブロックされたツールも未知のツールも、エラー印付きの `tool_result` を返します。ループを落とさず、モデルが別の手を選べます。
- **状態はただのファイル。** データベースも常駐プロセスもありません。ハーネスが覚えているものはすべて確認、削除、差分比較できます。
- **教材コードは小さく。** エラー処理は状態を静かに壊す箇所にだけ入れ、生産向けの防御は積みません。

## 移植メモ

元の Python コースは章ごとに独立したスクリプトです。この移植では章の入口を薄く保ち、1つのハーネスを共有して、同じ仕組みを17回コピーしないようにしました。章ごとにより小さいツール面が必要なら、別のレジストリを組みます。

Python 版に近づけるため、テキストブロックと record を使っています。JSON 層を手書きなのは依存ゼロを保つためで、言語機能を隠すためではありません。

## ライセンス

MIT。元のコースと設計は shareAI Lab によるものです。[LICENSE](LICENSE) を参照してください。
