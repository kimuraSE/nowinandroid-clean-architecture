# REFERENCE.md — アーキテクチャ要素別 参照ファイル集

> 「**X を実装・設計するとき、どのファイルを手本にすればよいか**」を引くためのカタログ。AI 駆動開発の詳細設計・実装フェーズで、既存の正しい実装を参照点にするために使う。
>
> - モジュールの役割と依存ルールは [COMPONENT.md](COMPONENT.md)。
> - なぜそうするか（規範）は [docs/architecture/clean-architecture.md](docs/architecture/clean-architecture.md) / [ddd.md](docs/architecture/ddd.md) / [components.md](docs/architecture/components.md)。
>
> パスは `モジュール … ファイル名` で表記（`…` は `src/.../com/google/samples/apps/nowinandroid/...` を省略）。複数挙げてある場合は **難易度の低い順**。

## 1. presentation（MVVM + UDF）

### 1.1 UiState（画面ごとの単一状態。sealed interface）
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| 最小形（Loading/Success/Error） | `feature/settings/impl … SettingsUiState.kt` | `Success` が編集可能設定を 1 つ保持するだけの素直な形 |
| UI 専用状態を畳み込む | `feature/bookmarks/impl … BookmarksUiState.kt` | `Success` に `shouldShowUndoBookmark`（UI 都合の状態）を内包 |
| サブ状態を入れ子にする | `feature/search/impl … SearchUiState.kt` | `Success` が `SearchResultState`（NotReady/EmptyQuery/Results）を保持 |
| 複数ストリームを統合 | `feature/foryou/impl … ForYouUiState.kt` | `Success` が onboarding / feed / isSyncing / deepLink を 1 つに集約 |

### 1.2 Event（画面ごとの単一イベント。sealed interface）
| 参照ファイル | ポイント |
|---|---|
| `feature/bookmarks/impl … BookmarksEvent.kt` | 操作・取り消し・既読をイベントで表現（`data class` / `data object`） |
| `feature/settings/impl … SettingsEvent.kt` | 設定変更イベントの最小例 |
| `feature/foryou/impl … ForYouEvent.kt` | ディープリンク等を含む複合イベント。ID は VO（`NewsResourceId` 等）で受ける |

### 1.3 ViewModel（公開は `uiState` + `onEvent` の 2 つだけ）
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| 最小形（map → UiState） | `feature/settings/impl … SettingsViewModel.kt` | `observeUserData().map{…}.catch{Error}.stateIn` の素直な形 |
| `combine` + UI 専用状態 | `feature/bookmarks/impl … BookmarksViewModel.kt` | DB 由来 Flow と `MutableStateFlow` を `combine` し単一 UiState に |
| `combine` で 2 UseCase 合成 + 画面引数 | `feature/topic/impl … TopicViewModel.kt` | `@AssistedInject` で `NavKey` 引数を受ける |
| 選択状態 + assisted injection | `feature/interests/impl … InterestsViewModel.kt` | `combine(selectedId, observeFollowableTopics())` |
| `flatMapLatest` + 複数ソース | `feature/search/impl … SearchViewModel.kt` | 入力クエリに反応するリアクティブ検索 |
| 複数 StateFlow を単一化（最難） | `feature/foryou/impl … ForYouViewModel.kt` | 入れ子 `stateIn` + `combine`、副作用（deepLink）処理 |

### 1.4 Screen（UiState を描画し Event を発行するだけ）
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| stateful/stateless 分割の基本 | `feature/bookmarks/impl … BookmarksScreen.kt` | `collectAsStateWithLifecycle` → `when(uiState)`、`onEvent(...)` 発行 |
| 単一 UiState を直接消費 | `feature/interests/impl … InterestsScreen.kt` | `uiState` + `onEvent` をそのまま受ける stateless |
| 分解 stateless を保つ（複数サブ状態） | `feature/foryou/impl … ForYouScreen.kt` | stateful 側で `uiState` を分解し、描画用 stateless はパラメータ分解のまま |
| PreviewParameterProvider | `feature/search/impl … SearchUiStatePreviewParameterProvider.kt` | プレビュー用の `UiState` 供給 |

## 2. UseCase 層（`core/usecase`）

### 2.1 UseCase（公開は `operator fun invoke` のみ）
| 種別 | 参照ファイル | ポイント |
|---|---|---|
| 観察系（薄い委譲） | `core/usecase … ObserveBookmarkedNewsUseCase.kt` | Repository を 1 行呼ぶだけ（規律の一貫性のため許容） |
| 観察系（合成あり） | `core/usecase … ObserveSearchResultsUseCase.kt` | 検索結果にユーザー状態を合成して `Flow` を返す |
| 観察系（並び替え等の引数） | `core/usecase … ObserveFollowableTopicsUseCase.kt` | `TopicSortField` を引数に取る |
| 操作系（`suspend … : Result<Unit>`） | `core/usecase … FollowTopicUseCase.kt` / `BookmarkNewsResourceUseCase.kt` | `runSuspendCatching` でラップ |
| `Result` ラッパ | `core/usecase … RunSuspendCatching.kt` | 操作系の共通エラーハンドリング |

### 2.2 Repository インターフェース / ポート（依存逆転の内側）
| 参照ファイル | ポイント |
|---|---|
| `core/usecase/repository … UserDataRepository.kt` | ドメインモデルのみを引数・戻り値に使う IF |
| `core/usecase/repository … UserNewsResourceRepository.kt` | 観察系は `Flow`、操作系は `suspend` |
| `core/usecase/util … SyncManager.kt` | 実装を sync 層に置くポート（抽象）の例 |

### 2.3 UseCase 単体テスト
| 参照ファイル | ポイント |
|---|---|
| `core/usecase（test）… FollowTopicUseCaseTest.kt` | 操作系：Test リポジトリで `Result` と副作用を検証 |
| `core/usecase（test）… ObserveFollowableTopicsUseCaseTest.kt` | 観察系：合成ロジックを検証 |

## 3. Entity / Value Object（`core/model`・DDD）
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| 振る舞いを持つ Entity（貧血でない） | `core/model … UserData.kt` | `isFollowing()` / `hasBookmarked()` などの判断を Entity 自身に置く |
| Value Object（ID 取り違え防止） | `core/model … NewsResourceId.kt` / `TopicId.kt` | `@JvmInline value class` |
| 読み取りビュー / 集約 | `core/model … UserNewsResource.kt` | `NewsResource` + `UserData` を束ねたビュー |
| 付帯情報つきモデル | `core/model … FollowableTopic.kt` | `Topic` + `isFollowed` |
| 素の Entity | `core/model … Topic.kt` / `NewsResource.kt` | フィールド定義の基本 |

## 4. data 層（実装 + Mapper）
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| Repository 実装（依存逆転の受け） | `core/data/repository … OfflineFirstUserDataRepository.kt` | `core/usecase` の IF を `internal` 実装 |
| 合成 Repository | `core/data/repository … CompositeUserNewsResourceRepository.kt` | 複数ソースを `combine` して `UserNewsResource` を組み立てる |
| DI バインディング（IF ↔ 実装） | `core/data/di … DataModule.kt` / `UserNewsResourceRepositoryModule.kt` | Hilt で IF に実装を束縛 |
| Mapper（DTO/Entity → domain） | `core/data/model … NewsResource.kt` | `toDomain()` / `toEntity()` 拡張関数 |
| Mapper（Room Entity → domain） | `core/database/model … NewsResourceEntity.kt` / `PopulatedNewsResource.kt` | `toDomain()` |
| Mapper（Network DTO → domain） | `core/network/model … NetworkTopic.kt` | `toDomain()` |
| DataSource（Proto DataStore） | `core/datastore … NiaPreferencesDataSource.kt` | Proto ⇄ `UserData` の変換 |

## 5. モジュール構成 / ナビゲーション / DI / 規約
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| feature の api/impl 分割 | `feature/bookmarks/api` ↔ `feature/bookmarks/impl` | api は公開契約、impl は Screen/ViewModel |
| NavKey（公開契約・引数なし） | `feature/bookmarks/api … navigation/BookmarksNavKey.kt` | `@Serializable object … : NavKey` |
| NavKey（引数あり） | `feature/interests/api … navigation/InterestsNavKey.kt` | `data class InterestsNavKey(val initialTopicId: String?)` |
| EntryProvider（ナビ登録） | `feature/bookmarks/impl … navigation/BookmarksEntryProvider.kt` | `NavKey` → `Screen` の対応づけ |
| convention plugin（feature 共通依存） | `build-logic/convention … AndroidFeatureImplConventionPlugin.kt` / `AndroidFeatureApiConventionPlugin.kt` | feature 共通の依存・設定を集約 |
| DI 配線（合成ルート） | `app … NiaApplication.kt` / `app/di/` | Repository 実装の解決はここ（`core/data` 依存は app のみ可） |

## 6. テスト
| ねらい | 参照ファイル | ポイント |
|---|---|---|
| ViewModel テスト（最小） | `feature/settings/impl（test）… SettingsViewModelTest.kt` | `onEvent` で操作し `uiState` 遷移を検証 |
| ViewModel テスト（combine/UI 状態） | `feature/bookmarks/impl（test）… BookmarksViewModelTest.kt` | Test リポジトリ + UseCase を構築して検証 |
| ViewModel テスト（複数ストリーム） | `feature/foryou/impl（test）… ForYouViewModelTest.kt` | `uiState.value as Success` のサブ状態を検証 |
| Test リポジトリ（test double） | `core/testing … repository/TestUserDataRepository.kt` | `MutableSharedFlow` ベースのテスト用実装 |
| 計装テスト（ComponentActivity） | `feature/search/impl（androidTest）… SearchScreenTest.kt` / `feature/foryou/impl（androidTest）… ForYouScreenTest.kt` | `ComposeTestRule` で分解 stateless を直接検証 |

## 7. 開発シナリオ別 索引（やりたいこと → 手本）

「**〇〇したい**」から逆引きする。各シナリオは「手順の要点」と「手本ファイル」。`§n` は本書の対応する章。

### A. 新規追加

**A-1.「新しい画面（feature）をゼロから追加したい」**
- `feature/<name>/api` に `NavKey`、`impl` に `UiState` / `Event` / `ViewModel` / `Screen` / `EntryProvider` を作る → `app` に api/impl 依存と EntryProvider 登録を追加。
- 手本: `feature/bookmarks/*`（最小で完全な一式）。引数つき遷移なら `feature/interests/api … InterestsNavKey.kt`（§5）

**A-2.「既存画面に新しいユーザー操作（ボタン/トグル）を追加したい」**
- `XxxEvent` に `data class`/`data object` を追加 → `onEvent` の `when` に分岐追加 → 対応 UseCase を呼ぶ → `Screen` で `onEvent(...)` を発行。
- 手本: `feature/bookmarks/impl … BookmarksEvent.kt` + `BookmarksViewModel.kt`（onEvent）+ `BookmarksScreen.kt`（発行側）（§1.2-1.4）

**A-3.「新しい UseCase（観察系/操作系）を追加したい」**
- 観察系 `operator fun invoke(...): Flow<T>` / 操作系 `suspend operator fun invoke(...): Result<Unit>`（`runSuspendCatching`）。`core/usecase` 直下に置き Repository IF を注入。ロジックがあれば単体テスト追加。
- 手本: `core/usecase … ObserveBookmarkedNewsUseCase.kt`（薄い委譲）/ `ObserveSearchResultsUseCase.kt`（合成）/ `FollowTopicUseCase.kt`（操作系）/ `RunSuspendCatching.kt`（§2.1）

**A-4.「新しい Repository（データ取得元）を追加したい」**
- IF を `core/usecase/repository`、実装（`internal`）を `core/data/repository`、Hilt バインディングを `core/data/di`、変換が要れば Mapper を data 側に。
- 手本: `core/usecase/repository … UserDataRepository.kt` ↔ `core/data/repository … OfflineFirstUserDataRepository.kt` + `core/data/di … DataModule.kt`（§2.2 / §4）

**A-5.「新しい Entity / Value Object を追加したい」**
- `core/model` に追加。ID は `@JvmInline value class`。判断ロジックは Entity 自身に置く（貧血にしない）。data モデルを import しない。
- 手本: `core/model … UserData.kt`（振る舞い）/ `NewsResourceId.kt`（VO）/ `UserNewsResource.kt`（集約ビュー）（§3）

**A-6.「新しい設定項目を追加したい」**
- `UserData` にフィールド追加 → `UserDataRepository` IF + 実装 + DataStore（Proto）→ Set/Observe UseCase → `SettingsUiState`/`Event`/`ViewModel`/`Screen` に反映。
- 手本: `feature/settings/impl …` 一式 + `core/usecase … SetDarkThemeConfigUseCase.kt` + `core/datastore … NiaPreferencesDataSource.kt`

### B. 画面パターン

**B-7.「一覧（フィード）画面を作りたい」**
- `LazyVerticalStaggeredGrid` + 共有 `newsFeed(...)`。アイテム操作は `onEvent` 経由で送る。
- 手本: `feature/bookmarks/impl … BookmarksScreen.kt` + `core/ui … NewsFeed.kt`

**B-8.「リスト＋詳細（list-detail）画面を作りたい」**
- 選択 ID を `savedStateHandle` で保持し `combine(selectedId, observe…)` で UiState に。
- 手本: `feature/interests/impl … InterestsViewModel.kt` / `InterestsScreen.kt`

**B-9.「入力に反応する検索画面を作りたい」**
- 入力クエリを `savedStateHandle` の StateFlow にし、`flatMapLatest` で結果 Flow を切り替える。
- 手本: `feature/search/impl … SearchViewModel.kt` / `SearchScreen.kt`

**B-10.「複数セクションを1画面に統合したい（オンボーディング＋フィード等）」**
- 各セクションを初期値つきの個別 `StateFlow` にし、`combine` で単一 `Success` に集約（部分ロードを保つ）。
- 手本: `feature/foryou/impl … ForYouViewModel.kt` / `ForYouUiState.kt`（§1.1 複数ストリーム）

**B-11.「画面に引数を渡したい（詳細画面に ID 等）」**
- `NavKey` を `data class` にして引数を持たせ、ViewModel は `@AssistedInject` + `@HiltViewModel(assistedFactory=…)` で受ける。
- 手本: `feature/interests/api … InterestsNavKey.kt` + `feature/topic/impl … TopicViewModel.kt`（§1.3 / §5）

**B-12.「ディープリンクを扱いたい」**
- `savedStateHandle` のキーを観察 → `flatMapLatest` で対象取得 → UiState に載せ、`LaunchedEffect` で副作用（既読化・遷移・状態クリア）。
- 手本: `feature/foryou/impl … ForYouViewModel.kt`（deepLink）+ `ForYouScreen.kt`（`DeepLinkEffect`）

**B-13.「Snackbar / 取り消し（undo）を出したい」**
- 「直前操作」を `MutableStateFlow` で保持し UiState に反映 → `Screen` の `LaunchedEffect` で Snackbar 表示 → 結果（押下/解除）を `onEvent` に変換。
- 手本: `feature/bookmarks/impl … BookmarksViewModel.kt`（`lastRemovedBookmarkId`）+ `BookmarksScreen.kt`（`onShowSnackbar`）

### C. 状態管理パターン

**C-14.「複数のデータソースを1つの UiState にまとめたい」**
- `combine(flowA, flowB, ::toUiState)`。どちらかが更新されるたび最新値で再計算される。
- 手本: `feature/bookmarks/impl … BookmarksViewModel.kt`（2ソース）/ `feature/foryou/impl … ForYouViewModel.kt`（4ソース）（§1.3）

**C-15.「ローディング/エラー状態を表現したい」**
- `sealed interface XxxUiState { Loading / Success / Error }` + `.catch { emit(Error) }` + `stateIn(initialValue = Loading)`。
- 手本: `feature/settings/impl … SettingsUiState.kt` + `SettingsViewModel.kt`（§1.1 最小形）

**C-16.「保存しない UI 専用の一時状態を持ちたい」**
- `private val MutableStateFlow` を作り `combine` で UiState に畳む（DB/DataStore には保存しない）。
- 手本: `feature/bookmarks/impl … BookmarksViewModel.kt`（`lastRemovedBookmarkId` → `shouldShowUndoBookmark`）

**C-17.「書き込み後に画面を自動更新したい（手動再取得しない）」**
- 操作系 UseCase で書く → Room/DataStore の `Flow` が再発火 → 観察系 UseCase 経由で UiState が自動更新（書き込みと読み取りを分離）。
- 手本: `feature/bookmarks/impl … BookmarksViewModel.kt` + `core/usecase … BookmarkNewsResourceUseCase.kt` / `ObserveBookmarkedNewsUseCase.kt`

### D. テスト

**D-18.「ViewModel をテストしたい」**
- Test リポジトリで UseCase を構築 → `backgroundScope` で `uiState` を collect → `onEvent` で操作 → `uiState` 遷移を検証。
- 手本: `feature/settings/impl（test）… SettingsViewModelTest.kt`（最小）/ `feature/bookmarks/impl（test）… BookmarksViewModelTest.kt`（§6）

**D-19.「UseCase をテストしたい」**
- Test リポジトリを注入 → `invoke()` の結果（`Result`/`Flow`）と副作用を検証。
- 手本: `core/usecase（test）… FollowTopicUseCaseTest.kt`（操作系）/ `ObserveFollowableTopicsUseCaseTest.kt`（観察系）（§2.3）

**D-20.「Compose UI をテストしたい」**
- `createAndroidComposeRule<ComponentActivity>()` で、分解 stateless な `Screen` に状態を渡して検証（`MainActivity` を起動する大きいテストは `:app` に置く）。
- 手本: `feature/search/impl（androidTest）… SearchScreenTest.kt` / `feature/foryou/impl（androidTest）… ForYouScreenTest.kt`（§6）

### E. データ・同期・ネットワーク

**E-21.「オフラインファースト同期を実装したい」**
- リモート差分をローカル（Room）に取り込み、UI は常にローカルを観察する。同期の共通処理（change-list 等）は `Synchronizer` ヘルパーに集約。
- 手本: `core/data/repository … OfflineFirstNewsRepository.kt`（`syncWith`）+ `core/data … SyncUtilities.kt` + `sync/work`

**E-22.「同期中インジケータ（進行状況）を画面に出したい」**
- ドメインのポート `SyncManager.isSyncing` を観察して UiState に載せ、Screen でオーバーレイ表示。
- 手本: `core/usecase/util … SyncManager.kt` + `feature/foryou/impl … ForYouViewModel.kt`（`isSyncing`）/ `ForYouScreen.kt`

**E-23.「オフライン検知（接続監視）をしたい」**
- `NetworkMonitor` の `Flow<Boolean>` を観察し、アプリ状態（`NiaAppState`）でオフライン時表示を制御。
- 手本: `core/data/util … NetworkMonitor.kt` / `ConnectivityManagerNetworkMonitor.kt` + `app/ui … NiaAppState.kt`

**E-24.「WorkManager でバックグラウンドタスクを動かしたい」**
- `CoroutineWorker` を実装し、Hilt 注入は `DelegatingWorker` 経由。起動は `Initializer`。
- 手本: `sync/work … workers/SyncWorker.kt` / `workers/DelegatingWorker.kt` / `initializers/SyncInitializer.kt`

**E-25.「リモート API（Retrofit）を追加したい」**
- データソース IF を定義し、Retrofit 実装と demo 実装を用意、`NetworkModule` で provide。DTO は `core/network/model`。
- 手本: `core/network … NiaNetworkDataSource.kt` / `retrofit/RetrofitNiaNetwork.kt` / `di/NetworkModule.kt`

**E-26.「ローカル DB（Room）にテーブル/DAO を追加したい」**
- `@Entity` を定義 → `@Dao` を追加 → `NiaDatabase` に登録。ドメインへは `toDomain()` で変換。
- 手本: `core/database … NiaDatabase.kt` / `dao/NewsResourceDao.kt` / `model/NewsResourceEntity.kt`（§4）

**E-27.「全文検索（FTS）を使いたい」**
- FTS 用エンティティ/DAO を作り本体テーブルから投入。検索 UseCase が件数と結果を観察。
- 手本: `core/database … dao/NewsResourceFtsDao.kt` / `model/NewsResourceFtsEntity.kt` + `feature/search/impl …`（§B-9）

### F. UI / Compose 詳細

**F-28.「外部リンクを Chrome Custom Tab で開きたい」**
- `launchCustomChromeTab(context, uri, color)` を呼ぶ（カードのクリック等から）。
- 手本: `core/ui … NewsFeed.kt`（`launchCustomChromeTab`）

**F-29.「通知権限（POST_NOTIFICATIONS）をリクエストしたい」**
- `rememberPermissionState` + `LaunchedEffect`。プレビュー（`LocalInspectionMode`）と API レベルでガードする。
- 手本: `feature/foryou/impl … ForYouScreen.kt`（`NotificationPermissionEffect`）

**F-30.「画面表示の分析イベント（screen view）を送りたい」**
- Screen のルートで `TrackScreenViewEvent(screenName = "…")` を呼ぶ。
- 手本: `core/ui … AnalyticsExtensions.kt`（`TrackScreenViewEvent`）+ 各 `*Screen.kt`

**F-31.「スクロールバー/スクロール追従を付けたい」**
- `rememberLazyStaggeredGridState` + `scrollbarState` + `DraggableScrollbar`。
- 手本: `feature/bookmarks/impl … BookmarksScreen.kt`（`DraggableScrollbar` / `rememberDraggableScroller`）

**F-32.「テーマ（ダーク/ダイナミックカラー）を適用したい」**
- `NiaTheme(darkTheme, disableDynamicTheming)` でラップ。設定値は `UserData` 由来で settings 画面から変更。
- 手本: `core/designsystem … theme/NiaTheme.kt` + `feature/settings/impl …`（`UserEditableSettings`）

**F-33.「プレビュー（@Preview / @DevicePreviews）を整備したい」**
- `PreviewParameterProvider` でサンプルデータを供給し、`@DevicePreviews` で複数サイズ確認。
- 手本: `core/ui … UserNewsResourcePreviewParameterProvider.kt` + `core/ui … DevicePreviews`（§1.4）

### G. 横断的関心事

**G-34.「分析（Analytics）イベントを記録したい」**
- `AnalyticsHelper.logEvent(AnalyticsEvent(...))`。横断的関心事として data / presentation の境界で呼ぶ。
- 手本: `core/analytics … AnalyticsHelper` + 例 `core/data … OfflineFirstUserDataRepository.kt` / `core/ui … AnalyticsExtensions.kt`

**G-35.「DI モジュール（bind / provide）を追加したい」**
- IF ↔ 実装は `@Binds`、生成が要るものは `@Provides`。モジュールは各層の `di/` に置く。
- 手本: `core/data/di … DataModule.kt`（`@Binds`）/ `core/network/di … NetworkModule.kt`（`@Provides`）

**G-36.「ディスパッチャ（IO 等）を注入したい」**
- `@Dispatcher` 修飾子で `CoroutineDispatcher` を注入（直書きの `Dispatchers.IO` を避ける）。
- 手本: `core/common … network/NiaDispatchers.kt` + `network/di/DispatchersModule.kt`

### H. テスト（種別追加）

**H-37.「スクリーンショットテストを追加したい」**
- Roborazzi の `captureMultiDevice` 等で撮影・比較。画像は CI 生成（ワークステーションからコミットしない）。
- 手本: `feature/foryou/impl（test）… ForYouScreenScreenshotTests.kt`

**H-38.「Flow / コルーチンをテストしたい」**
- `MainDispatcherRule` で Main を差し替え、`backgroundScope` で collect。複雑な発行列は turbine の `.test {}`。
- 手本: `core/testing … util/MainDispatcherRule.kt` + 各 ViewModel テスト；turbine 例 `core/common（test）… result/ResultKtTest.kt`

**H-39.「Test double（Fake / Test 実装）を用意したい」**
- 本番 IF を実装し、テスト専用フックを足す（モック不使用）。
- 手本: `core/testing … repository/TestUserDataRepository.kt` / `core/data-test … repository/FakeNewsRepository.kt`
