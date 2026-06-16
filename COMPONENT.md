# COMPONENT.md — モジュールの役割と絶対ルール

> 本書は **AI 駆動開発（詳細機能設計・実装）のリファレンス**。各 Gradle モジュールの役割と、Clean Architecture 上で「絶対に破ってはいけない依存ルール」を **1 モジュール = 1 カード**で定義する。
>
> - アーキテクチャの**規範（なぜ・全体像）**は [docs/architecture/clean-architecture.md](docs/architecture/clean-architecture.md) / [ddd.md](docs/architecture/ddd.md) / [components.md](docs/architecture/components.md) を正典とする。本書はそれを**モジュール単位の早見表**に落としたもの。
> - 参照すべき実装ファイルの在処は [REFERENCE.md](REFERENCE.md) を見る。

## 0. 使い方（AI 駆動開発）

- モジュールを追加・変更するときは、まず本書で**そのモジュールが属する層**と**依存可否**を確認する。
- 「依存してはいけない」に挙げた依存を入れたくなったら、それは**層違反のサイン**。設計を見直す（例: ViewModel が Repository を直接呼びたくなったら UseCase を挟む）。
- コードと本書・正典 docs が食い違う場合は、**docs を正としてコードを直す**。

## 1. 凡例

- **層**: Clean Architecture 上の位置（Entity / UseCase / data / presentation / 横断 / テスト支援 / 対象外）。
- **依存してよい**: その module の `build.gradle.kts` で依存して良い相手。
- **依存してはいけない**: 入れると層違反になる相手（＝絶対ルールの核）。
- **絶対ルール**: そのモジュールで常に成り立たせる不変条件。
- **代表ファイル**: 役割を体現する実在ファイル（網羅は [REFERENCE.md](REFERENCE.md)）。

## 2. レイヤ別 依存方向の早見表

依存は常に内側（`core/model`）へ向く。外側が内側を知り、内側は外側を知らない。

```
presentation ──→ core/usecase ──→ core/model ←── data
```

| 層 | モジュール | 依存の向き先（要約） |
|---|---|---|
| presentation | `feature/*/impl`・`app` | → `core/usecase`・`core/model`・`core/ui`・`feature/*/api`（`app` のみ DI 配線で `core/data`） |
| data | `core/data`・`core/database`・`core/network`・`core/datastore`・`sync/work` | → `core/usecase`（IF を実装）・`core/model` |
| UseCase | `core/usecase` | → `core/model` のみ |
| Entity | `core/model` | → なし（最内） |

> 補足: presentation（`feature/*/impl`）は `core/usecase` のみに依存し、`core/data` 以下のデータ層は参照しない（DI 配線を行う `app` のみ例外）。単体テストで実装が必要な場合は `core/testing`（`core/data` を `api` 公開）経由で test double を使う。

## 3. モジュール詳細

### 3.1 Entity 層

#### `:core:model`
- **役割**: Entity / Value Object / 読み取り用ビュー。振る舞いを持つドメインモデル（Enterprise Business Rules）。
- **層**: Entity（最内）
- **依存してよい**: なし（純 Kotlin。`kotlinx-datetime` のみ許可）
- **依存してはいけない**: 他の全モジュール、Android SDK、coroutines、Hilt、data のモデル
- **絶対ルール**:
  - 誰にも依存しない最内モジュールとして維持する（`core/usecase` へ吸収しない）。
  - ID は `@JvmInline value class`（`TopicId` / `NewsResourceId`）で表し、取り違えをコンパイル時に防ぐ。
  - Room エンティティ・DTO・Proto などデータ層のモデルを一切 import しない。
  - 判断ロジックは Entity 自身に置く（`UserData.isFollowing()` 等。貧血モデルにしない＝DDD 戦術パターン）。
- **代表ファイル**: `FollowableTopic.kt`, `NewsResourceId.kt`, `UserData.kt`

### 3.2 UseCase 層（Application Business Rules）

#### `:core:usecase`
- **役割**: UseCase（操作系・観察系）、**Repository インターフェース**、ドメインのポート（`SyncManager`）。Clean Architecture の「ドメイン層」のうち Application Business Rules を担う。
- **層**: UseCase
- **依存してよい**: `core/model` のみ（＋ `kotlinx-coroutines` / `javax.inject`）
- **依存してはいけない**: `core/data` 以下のデータ層、`feature/*`、`app`、Android SDK（純 Kotlin を保つ）
- **絶対ルール**:
  - Repository は**インターフェースのみ**を置く（実装は `core/data`＝依存逆転）。
  - presentation からドメインへのアクセスは必ずこの層の UseCase を経由する（Repository を直接公開しない）。
  - UseCase の公開メソッドは `operator fun invoke` のみ。観察系 `Observe〜` は `Flow<T>`、操作系（動詞始まり）は `suspend …: Result<Unit>`。
  - パッケージ構成: UseCase は直下 `core.usecase.*`、Repository IF は `core.usecase.repository.*`、ポートは `core.usecase.util.*`。
- **代表ファイル**: `ObserveBookmarkedNewsUseCase.kt`, `BookmarkNewsResourceUseCase.kt`, `repository/UserDataRepository.kt`, `util/SyncManager.kt`

### 3.3 data 層

#### `:core:data`
- **役割**: Repository インターフェースの**実装**、データソース（database / network / datastore）の統合、Mapper（`toDomain` / `toEntity`）、Hilt DI バインディング。
- **層**: data（統合）
- **依存してよい**: `core/usecase`（実装する IF）・`core/model`・`core/database`・`core/network`・`core/datastore`・`core/common`・`core/analytics`・`core/notifications`
- **依存してはいけない**: `feature/*`・`app`・あらゆる presentation（UI を知らない）
- **絶対ルール**:
  - Repository 実装はここに置く（IF は `core/usecase`＝依存逆転）。実装クラスは `internal`、Hilt で IF 型として束縛する。
  - 実装名は実装特性を表す（`OfflineFirstNewsRepository`）か `Default〜`。
  - data ⇄ domain のモデル変換はここ等 data 系に置く（`core/model` / `core/usecase` は data モデルを知らない）。
- **代表ファイル**: `repository/OfflineFirstUserDataRepository.kt`, `repository/CompositeUserNewsResourceRepository.kt`, `model/NewsResource.kt`（Mapper）, `di/DataModule.kt`

#### `:core:database`
- **役割**: Room データベース・DAO・Room エンティティ。Room エンティティ → ドメインモデルの Mapper（`toDomain`）。
- **層**: data（ローカル永続化）
- **依存してよい**: `core/model`（Mapper のため）
- **依存してはいけない**: `core/usecase`・`core/data`・`feature/*`・`app`（上位を知らない）
- **絶対ルール**: Room 固有モデル（`*Entity` / `Populated*`）はこのモジュールに閉じる。外へはドメインモデルに変換して渡す。
- **代表ファイル**: `model/NewsResourceEntity.kt`, `model/PopulatedNewsResource.kt`（`toDomain`）, `dao/`

#### `:core:network`
- **役割**: Retrofit / OkHttp によるリモートデータソースと DTO。DTO → ドメインモデルの Mapper（`toDomain`）。
- **層**: data（リモート）
- **依存してよい**: `core/model`・`core/common`
- **依存してはいけない**: `core/usecase`・`core/data`・`feature/*`・`app`
- **絶対ルール**: DTO（`Network*`）はこのモジュールに閉じる。外へはドメインモデルに変換して渡す。
- **代表ファイル**: `model/NetworkNewsResource.kt`, `model/NetworkTopic.kt`（`toDomain`）

#### `:core:datastore`
- **役割**: Proto DataStore による設定・ユーザーデータの永続化（`NiaPreferencesDataSource`）。Proto ⇄ `UserData` の変換。
- **層**: data（ローカル永続化）
- **依存してよい**: `core/datastore-proto`・`core/model`・`core/common`
- **依存してはいけない**: `core/usecase`・`core/data`・`feature/*`・`app`
- **絶対ルール**: Proto 型はこのモジュール（と proto モジュール）に閉じる。外へは `UserData` 等のドメインモデルで渡す。
- **代表ファイル**: `NiaPreferencesDataSource.kt`

#### `:core:datastore-proto`
- **役割**: DataStore 用の Protocol Buffers スキーマ定義と生成コード。純 JVM ライブラリ。
- **層**: data（スキーマ）
- **依存してよい**: なし（protobuf のみ）
- **依存してはいけない**: 他の全モジュール
- **絶対ルール**: `.proto` 定義と生成物のみ。ビジネスロジックを持たない。
- **代表ファイル**: `src/main/proto/*.proto`

#### `:sync:work`
- **役割**: WorkManager によるバックグラウンド同期の実装。`core/usecase` のポート `SyncManager` を実装する（`WorkManagerSyncManager`）。
- **層**: data（インフラ）
- **依存してよい**: `core/data`・`core/analytics`・`core/notifications`（`core/usecase` は `core/data` 経由で取得）
- **依存してはいけない**: `feature/*`・presentation（UI を知らない）
- **絶対ルール**: `SyncManager` の実装はここ。UI を持たないため「UseCase 経由」の規律は対象外（data 層の実装詳細）。
- **代表ファイル**: `status/WorkManagerSyncManager.kt`, `workers/SyncWorker.kt`

### 3.4 presentation 層

#### `:feature:<name>:api`（foryou / interests / bookmarks / topic / search）
- **役割**: feature の**公開契約**。ナビゲーションキー（`NavKey`）など、`app` や他 feature から参照される最小 API。
- **層**: presentation（契約）
- **依存してよい**: `core/model`・`core/navigation`（共通依存は convention plugin `nowinandroid.android.feature.api` が注入）
- **依存してはいけない**: 同 feature の `impl`、他 feature の `impl`、`core/data`
- **絶対ルール**: `impl` の内部（Screen / ViewModel）を公開しない。画面遷移は必ず api 経由で行う。
- **代表ファイル**: `navigation/BookmarksNavKey.kt`
- **補足**: `settings` は api を持たず `impl` のみ（他から遷移される公開契約が無いため）。

#### `:feature:<name>:impl`（foryou / interests / bookmarks / topic / search / settings）
- **役割**: 画面実装。`UiState` / `Event` / `ViewModel` / `Screen`。UDF の presentation 本体。
- **層**: presentation
- **依存してよい**: `core/usecase`・`core/model`・`core/ui`・`core/designsystem`・自身の `api`（＋遷移先 feature の `api`）。共通依存は convention plugin `nowinandroid.android.feature.impl` が注入。
- **依存してはいけない**: `core/data` 以下のデータ層（直接）、他 feature の `impl`
- **絶対ルール**:
  - ViewModel は **UseCase のみ**経由（Repository 直接参照禁止）。
  - 画面ごとに**単一 `UiState` + 単一 `onEvent`**。ViewModel の公開は `uiState: StateFlow` と `fun onEvent` の 2 つだけ。
  - Screen は `UiState` を描画し `Event` を発行するだけ（ビジネス判断を持たない）。
- **代表ファイル**: `BookmarksUiState.kt`, `BookmarksEvent.kt`, `BookmarksViewModel.kt`, `BookmarksScreen.kt`

#### `:app`
- **役割**: アプリのエントリ。全 feature の束ね、ナビゲーションホスト、Hilt DI 配線（Repository IF ↔ 実装の解決を含む合成ルート）。
- **層**: presentation（最上位・Composition Root）
- **依存してよい**: 全 feature の `api` / `impl`、`core/ui`・`core/designsystem`・`core/model`・`core/analytics`・`sync/work`、そして **`core/data`（DI 配線のため例外的に可）**
- **依存してはいけない**: （最上位の合成ルートのため特になし）
- **絶対ルール**:
  - `core/data` に依存してよい**唯一の presentation**（Repository 実装を DI で束縛するため）。
  - ビジネスロジックを持たない（配線・ナビゲーション・テーマのみ）。
- **代表ファイル**: `NiaApplication.kt`, `ui/NiaApp.kt`, `di/`

### 3.5 横断・基盤（support）

**共通ルール**: presentation と data の双方から利用してよいが、**`feature/*` / `app` / `core/usecase` / `core/data` には依存しない**（上位・隣接層を知らない）。

| モジュール | 役割 | 主な依存 |
|---|---|---|
| `:core:common` | Dispatcher・`Result`・ネットワーク監視などの共通ユーティリティ（純 JVM） | なし |
| `:core:designsystem` | テーマ・アイコン・デザインコンポーネント（Compose）。ドメインを知らない純 UI 部品 | なし |
| `:core:ui` | 複数 feature 共有の合成 UI（`newsFeed` / `NewsResourceCard` / `NewsFeedUiState`） | `core/model`・`core/designsystem`・`core/analytics` |
| `:core:analytics` | `AnalyticsHelper` と `LocalAnalyticsHelper`（Compose） | なし |
| `:core:notifications` | システム通知（`Notifier`） | `core/common`・`core/model` |
| `:core:navigation` | `Navigator` 抽象・`NavKey` 基盤（feature api が `NavKey` を提供） | なし |

- 個別ルール: `core/ui` は描画のため `core/model` に依存してよいが、`core/usecase` / `core/data` / `feature/*` には依存しない。`core/designsystem` はドメインを一切知らない（見た目の部品のみ）。

### 3.6 テスト支援（test infra）

**共通ルール**: production には含めない（`testImplementation` / `androidTestImplementation` 専用）。本番実装の差し替え用 test double を提供する。

| モジュール | 役割 | 主な依存 |
|---|---|---|
| `:core:testing` | Test リポジトリ・`MainDispatcherRule`・テストデータ・`TestSyncManager` 等。**`core/data` を `api` 公開**（feature テストが `Composite…` 実装を使えるのはこのため） | `core/data`・`core/model`・`core/common`・`core/analytics`・`core/notifications` |
| `:core:data-test` | Fake リポジトリと差し替え用 DI（`FakeNewsRepository` 等） | `core/data` |
| `:core:datastore-test` | DataStore のテスト補助（一時フォルダ等） | `core/datastore`・`core/common` |
| `:core:screenshot-testing` | Roborazzi のスクショ撮影ヘルパー（`captureMultiDevice` 等） | `core/designsystem` |
| `:sync:sync-test` | 同期のテストダブル | `core/data`・`sync/work` |
| `:ui-test-hilt-manifest` | Hilt 計装テスト用の `ComponentActivity` マニフェスト | なし |

### 3.7 アーキテクチャ対象外（ビルド維持のみ）

Clean Architecture の層ルールの対象外。ビルドが通る状態のみ維持する。

| モジュール | 役割 |
|---|---|
| `:app-nia-catalog` | デザインシステムのコンポーネント一覧を表示する独立アプリ |
| `:benchmarks` | Macrobenchmark とベースラインプロファイル生成 |
| `:lint` | プロジェクト独自の lint ルール |

