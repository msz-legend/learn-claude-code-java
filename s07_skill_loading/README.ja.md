# s07: スキル読み込み

[English](README.md) · [中文](README.zh.md) · [日本語](README.ja.md)

[s06: サブエージェント](../s06_subagent/) → **s07** → [s08: コンテキスト圧縮](../s08_context_compact/)

> カタログだけを見せ、タスクが合うときだけ本文を読む。

---

## 課題

すべての指示をシステムプロンプトに貼ると、そのターンで使わないスキルにコンテキストを使う。モデルは何があるかと、どう取り出すかだけ知ればよい。

## 仕組み

1. スキルフォルダの SKILL.md を走査し、front matter を読む。
2. 名前と1行の説明をプロンプトに入れる。
3. load_skill を用意し、本文は必要時に取得する。

## コードマップ

| File | Role |
|---|---|
| `s07_skill_loading/Main.java` | 本章の入口。この仕組みを1つのループに接続する。 |
| `harness/Skills.java` | 全章で共有する実装。 |

## 実行

```bash
java -cp out s07_skill_loading.Main
```

初回実行前に .env.example を .env にコピーし、いずれかのプロバイダーを設定してください。認証情報がなくても --help は動きます。

## 試してみる

- /skills を実行し、コードレビューを頼んで load_skill が動くのを見る。
- 新しい SKILL.md を追加し、再起動なしで出ることを確認する。

## 次章

次は [s08: コンテキスト圧縮](../s08_context_compact/README.ja.md) へ。
