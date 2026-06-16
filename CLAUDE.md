# CLAUDE.md

このリポジトリで作業する Claude Code（および開発者）向けの指針。本プロジェクトは **Google 公式サンプル NowInAndroid のアーキテクチャを、Clean Architecture + MVVM + 単一データフロー（UDF）+ ドメイン駆動設計（DDD）へリアーキしたもの**であり、「今後の Android アプリ開発の参照用サンプル」を目的とする。

## アーキテクチャの正典（必読）

実装・レビュー・設計判断は以下の 3 ドキュメントに従う。コードとドキュメントが食い違う場合はドキュメントを正とし、コードを直す（リアーキ完了までの過渡期は旧 NIA アーキが残っている箇所がある）。

| ドキュメント | 内容 |
|---|---|
| [docs/architecture/clean-architecture.md](./docs/architecture/clean-architecture.md) | 層の定義・依存ルール・UseCase / Repository / presentation(UDF) / Mapper / テスト規約 |
| [docs/architecture/ddd.md](./docs/architecture/ddd.md) | Entity / Value Object / 集約 / ユビキタス言語の定義と適用範囲 |
| [docs/architecture/components.md](./docs/architecture/components.md) | ターゲットのモジュール依存図・UseCase 一覧（19個）・bookmarks の詳細図・実行ステップ |

## アーキテクチャ要約

詳細は上記ドキュメント参照。最低限守るルール:

- **層**: `core/model`（Entity 層）← `core/usecase`（UseCase + Repository インターフェース）← `core/data`（実装）。presentation（`feature/*`・`app`）は `core/usecase` のみに依存し、`core/data` を直接参照しない（DI 配線を行う `app` を除く）
- **依存方向**: 常に最内の `core/model` へ向ける。`core/model` は独立モジュールとして維持する（`core/usecase` へ吸収しない）
- **UseCase 経由**: ViewModel は Repository を直接参照せず、必ず UseCase を経由する。公開メソッドは `operator fun invoke` のみ
  - 観察系 `Observe〜UseCase`: `operator fun invoke(...): Flow<T>`
  - 操作系（動詞始まり）: `suspend operator fun invoke(...): Result<Unit>`（エラーは `kotlin.Result`）
- **UDF**: 画面ごとに単一 `UiState`（sealed interface: Loading / Success / Error）と単一 `onEvent(event)`。ViewModel が公開するのは `uiState: StateFlow<XxxUiState>` と `fun onEvent(XxxEvent)` の 2 つのみ
- **VO**: ID は `@JvmInline value class`（`TopicId` / `NewsResourceId`）
- **Mapper**: data 側モジュールに `toDomain()` / `toEntity()` / `toNetwork()` の拡張関数として置く。`core/model` / `core/usecase` は data のモデルを知らない
- **DDD**: 戦術的パターン中心（Entity / VO / 集約 / ユビキタス言語）。境界づけコンテキスト分割・ドメインイベントは適用しない

## リアーキの進め方

[components.md §5](./docs/architecture/components.md) の順序で進める:

1. **基盤（依存逆転）**: Repository インターフェースを `core/usecase` へ移動、`core/usecase` を `core/model` のみ依存に、`core/data` を `core/usecase` 依存に。ID の value class 化、`UserData` への振る舞い追加
2. **UseCase 層**: 19 UseCase を作成し単体テスト追加。既存 3 UseCase（`GetFollowableTopicsUseCase` 等）はリネーム
3. **feature 順次変換**: bookmarks → settings → topic → interests → search → foryou の順に UiState + onEvent 化
4. **同期**: AGENT.md / AGENTS.md / README を新アーキの説明に更新

各ステップの完了条件は **`./gradlew assembleDemoDebug` とユニットテストが green** であること。main に直接コミットし、段階単位（基盤 / UseCase 層 / 各 feature）でコミットする。

- 対象: 全 feature（foryou / bookmarks / interests / search / settings / topic）と core 層
- `sync` は依存逆転への追従修正のみ。`app-nia-catalog` / `benchmarks` は対象外（ビルド維持のみ）
- 既存テストは新アーキ（UseCase 単体テスト・onEvent 経由の ViewModel テスト）に移植する

## ビルド & テストコマンド

product flavor は `demo` / `prod`、build type は `debug` / `release`。

- ビルド: `./gradlew assemble{Variant}`（通常 `assembleDemoDebug`）
- フォーマット修正: `./gradlew spotlessApply`
- ユニットテスト: `./gradlew {variant}Test`（例 `./gradlew testDemoDebug`）
- 単一テスト: `./gradlew {variant}Test --tests "com.example.MyTestClass"`
- スクリーンショットテスト: `./gradlew verifyRoborazziDemoDebug`
- 計装テスト（GMD）: `./gradlew pixel6api31aospDebugAndroidTest`

### テスト作法

- 計装テストの UI 機能テストは `ComponentActivity` 上の `ComposeTestRule` のみを使う。`MainActivity` を起動する大きいテストは `:app` モジュールに置く
- ローカルテスト: アサーションは [google/truth](https://github.com/google/truth)、複雑なコルーチンは [cashapp/turbine](https://github.com/cashapp/turbine)、コルーチンは [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- スクリーンショットテストは CI で生成するため、ワークステーションからリポジトリにコミットしない

## モジュール構成メモ

- feature は `feature/<name>/api`（NavKey 等の公開契約）と `feature/<name>/impl`（Screen / ViewModel）に分かれる
- ビルド規約は `build-logic/convention/` の convention plugin に集約（`nowinandroid.android.feature.impl` 等）。feature 共通の依存はここで注入される。依存方向の変更は個別 `build.gradle.kts` か convention plugin で行う
