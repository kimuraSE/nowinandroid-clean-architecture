![Now in Android](docs/images/nia-splash.jpg "Now in Android")

# Now in Android — Clean Architecture リアーキ版

本リポジトリは、Google 公式サンプル [**Now in Android**](https://github.com/android/nowinandroid) の
アーキテクチャを **Clean Architecture + MVVM + 単一データフロー（UDF）+ ドメイン駆動設計（DDD）** に
作り変えた、**今後の Android アプリ開発の参照用サンプル**です。

アプリの機能（トピックのフォロー、ニュース購読、ブックマーク、検索）はそのままに、
**層構成・依存方向・状態管理**を学習しやすい形へリアーキしています。Jetpack Compose / Hilt /
Room / DataStore / Retrofit / WorkManager といった技術スタックは原典を踏襲します。

## ドキュメント

設計・実装・レビューの判断は、まず以下を参照してください。

| ドキュメント | 内容 |
|---|---|
| [docs/architecture/clean-architecture.md](docs/architecture/clean-architecture.md) | **正典**：層の定義・依存ルール・UseCase / Repository / presentation(UDF) / Mapper / テスト規約 |
| [docs/architecture/ddd.md](docs/architecture/ddd.md) | **正典**：Entity / Value Object / 集約 / ユビキタス言語 |
| [docs/architecture/components.md](docs/architecture/components.md) | **正典**：モジュール依存図・UseCase 一覧・実行ステップ |
| [COMPONENT.md](COMPONENT.md) | 各モジュールの役割と「絶対に破ってはいけない依存ルール」（モジュール早見表） |
| [REFERENCE.md](REFERENCE.md) | 「〇〇を実装するならどのファイルが手本か」を要素別・シナリオ別に索引化 |
| [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md) | AI エージェント・開発者向けの作業指針 |

> コードとドキュメントが食い違う場合は **ドキュメントを正としてコードを直します**。
> （参考）元 NIA の設計解説: [Architecture Learning Journey](docs/ArchitectureLearningJourney.md) / [Modularization Learning Journey](docs/ModularizationLearningJourney.md)

## アーキテクチャ要約

```
presentation ──→ core/usecase ──→ core/model ←── data
 (feature/*・app)  (UseCase+Repository IF)  (Entity/VO)   (実装・Mapper)
```

- **層**: `core/model`（Entity 層）← `core/usecase`（UseCase + Repository インターフェース）← `core/data`（実装）。presentation（`feature/*`・`app`）は `core/usecase` のみに依存し、`core/data` を直接参照しない（DI 配線を行う `app` を除く）。
- **依存方向**: 常に最内の `core/model` へ向ける。`core/model` は何にも依存しない純 Kotlin。
- **UseCase 経由**: ViewModel は Repository を直接参照せず、必ず UseCase を経由する。UseCase の公開メソッドは `operator fun invoke` のみ（観察系は `Flow<T>`、操作系は `suspend … : Result<Unit>`）。
- **UDF**: 画面ごとに **単一 `UiState`**（sealed interface: Loading / Success / Error）と **単一 `onEvent(event)`**。ViewModel が公開するのは `uiState: StateFlow<XxxUiState>` と `fun onEvent(XxxEvent)` の 2 つだけ。
- **VO**: ID は `@JvmInline value class`（`TopicId` / `NewsResourceId`）で型安全に。
- **Mapper**: data ⇄ domain の変換は data 側に `toDomain()` / `toEntity()` / `toNetwork()` の拡張関数で置く。
- **DDD**: 戦術的パターン中心（Entity / VO / 集約 / ユビキタス言語）。

## モジュール構成

- **`app`**: アプリのエントリ。全 feature の束ね、ナビゲーションホスト、Hilt DI 配線（合成ルート）。
- **`feature/<name>/api`**: 公開契約（ナビゲーション `NavKey` 等）。`feature/<name>/impl`: 画面実装（`UiState` / `Event` / `ViewModel` / `Screen`）。
- **`core/model`**: Entity / Value Object。`core/usecase`: UseCase と Repository インターフェース。`core/data`（+ `core/database` / `core/network` / `core/datastore`）: 実装とデータソース。
- **`core/ui` / `core/designsystem` / `core/common` / `core/analytics` / `core/notifications` / `core/navigation`**: 横断的な支援モジュール。

モジュールごとの役割と依存可否の詳細は [COMPONENT.md](COMPONENT.md) を参照。

## 機能

[Now in Android](https://developer.android.com/series/now-in-android) シリーズのコンテンツ（動画・記事など）を一覧表示します。
ユーザーは興味のあるトピックをフォローでき、フォロー中のトピックに新着があると通知を受け取れます。記事のブックマークや全文検索にも対応します。

![For You / Interests / Topic 各画面のスクリーンショット](docs/images/screenshots.png "Now in Android のスクリーンショット")

## ビルド & 実行

Android Studio（最新の安定版）で開き、Run Configuration を `app` にして実行します。

- product flavor: `demo` / `prod`、build type: `debug` / `release`
- 通常開発は **`demoDebug`** バリアントを使う（`demo` はローカルの静的データ、`prod` はバックエンド接続だが現在非公開）

```bash
# ビルド（通常）
./gradlew assembleDemoDebug

# フォーマット修正（ktlint / spotless）
./gradlew spotlessApply
```

## テスト

テストではモックライブラリを使わず、本番実装を **テストダブル（Test/Fake リポジトリ）** に差し替えます。
ViewModel テストは本番の UseCase をテストリポジトリ上に構築し、**`onEvent` で操作して `uiState` の遷移を検証**します。

```bash
# ローカル単体テスト（demoDebug）
./gradlew testDemoDebug

# 単一クラス
./gradlew testDemoDebugUnitTest --tests "com.example.MyTestClass"

# スクリーンショットテスト（Roborazzi）
./gradlew verifyRoborazziDemoDebug   # 検証
./gradlew recordRoborazziDemoDebug   # 基準画像の再生成

# 計装テスト（Gradle Managed Device）
./gradlew pixel6api31aospDebugAndroidTest
```

> [!NOTE]
> テストは `demoDebug` バリアントのみ対象です。`./gradlew test` や `connectedAndroidTest`（全バリアント）は実行しないでください。
> スクリーンショットの基準画像は CI（Linux）で生成するため、ワークステーションからはコミットしません。

実装の手本ファイルやテストの書き方は [REFERENCE.md](REFERENCE.md) を参照。

## 原典・ライセンス

本プロジェクトは Google の [Now in Android](https://github.com/android/nowinandroid)（Apache License 2.0）を基にした派生物です。
オリジナルおよび本リポジトリは Apache License 2.0 で配布されます。詳細は [LICENSE](LICENSE) を参照してください。
