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
