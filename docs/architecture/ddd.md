# ドメイン駆動設計（DDD）規約

本プロジェクトにおける DDD の適用方針。**戦術的パターン中心**に適用し、戦略的設計（境界づけコンテキスト分割・ドメインイベント）は適用しない（理由は §6）。層構造と依存ルールは [clean-architecture.md](./clean-architecture.md) を参照。

## 1. 適用する要素 / しない要素

| 要素 | 適用 | 備考 |
|---|---|---|
| Entity | ✅ | §3 |
| Value Object | ✅ | §4 |
| 集約（Aggregate） | ✅ | §5。UserData が代表例 |
| Repository | ✅ | clean-architecture.md §4 |
| ユビキタス言語 | ✅ | §2 |
| 境界づけコンテキスト | ❌ | §6 |
| ドメインイベント | ❌ | §6 |
| 仕様パターン・ドメインサービス | ❌ | 必要になるドメインルールが存在しない |

## 2. ユビキタス言語（用語集）

コード・ドキュメント・会話で以下の用語を一貫して使う。コード上の命名はこの表と一致させる。

| 用語 | 型名 | 意味 |
|---|---|---|
| トピック | `Topic` | Android 開発の関心領域（例: Compose, Headlines）。ユーザーがフォローする対象 |
| ニュースリソース | `NewsResource` | 配信される記事・動画などのコンテンツ |
| フォロー | - | ユーザーがトピックを購読し、フィードに反映させる行為 |
| ブックマーク | - | ニュースリソースを後で読むために保存する行為 |
| 既読 | viewed | ニュースリソースを閲覧済みにする行為 |
| ユーザーデータ | `UserData` | フォロー・ブックマーク・既読・表示設定などユーザーの状態の集約 |
| フォロー可能トピック | `FollowableTopic` | トピック + 現在のフォロー状態のビュー |
| ユーザーニュースリソース | `UserNewsResource` | ニュースリソース + ユーザー状態（ブックマーク済みか等）を合成したビュー |

## 3. Entity

**定義: 識別子（ID）によって同一性が決まり、ドメインの振る舞いをメソッドとして持つオブジェクト。**

- 属性が同じでも ID が違えば別物、属性が変わっても ID が同じなら同一とみなす
- Clean Architecture の「Entity 層」とは、この Entity と Value Object・集約を含むドメインモデル全体を指す（両概念をこの定義で統合する）
- 本プロジェクトの Entity: `Topic`（`TopicId` で同一）、`NewsResource`（`NewsResourceId` で同一）、`UserData`

### ドメインモデル貧血症の回避

ドメインの判断ロジックは data class の外（UseCase や UI）に書かず、Entity / VO のメソッドとして持たせる。

```kotlin
data class UserData(
    val followedTopics: Set<TopicId>,
    val bookmarkedNewsResources: Set<NewsResourceId>,
    ...
) {
    fun isFollowing(topicId: TopicId): Boolean = topicId in followedTopics

    fun shouldShowOnboarding(): Boolean = !shouldHideOnboarding && followedTopics.isEmpty()
}
```

- 判断（〜かどうか）・導出（〜から計算できる値）はドメインモデルのメソッドへ
- 複数 Repository をまたぐ調整・合成は UseCase へ
- UI 都合の整形（日付フォーマット等）は presentation へ

## 4. Value Object

**定義: 同一性を持たず、値そのものが意味を持つ不変オブジェクト。属性が等しければ等しい。**

- **ID は `@JvmInline value class` で型化する**: `TopicId`, `NewsResourceId`。`followTopic(id: String)` のような ID の取り違えをコンパイル時に防ぐ

```kotlin
@JvmInline
value class TopicId(val value: String)

@JvmInline
value class NewsResourceId(val value: String)
```

- 既存の enum（`DarkThemeConfig`, `ThemeBrand`, `NewsResourceType`）も VO と位置づける
- URL・日時・検索クエリ等の型化は行わない（バリデーション要件が存在せず過剰設計になるため）。バリデーションが必要になった時点で VO 化する

## 5. 集約（Aggregate）

**定義: 整合性を保つ単位としてまとめて扱うオブジェクト群。集約ルートを通じてのみ変更する。**

- `UserData` を集約ルートとする。フォロー集合・ブックマーク集合・表示設定はバラバラに更新せず、`UserDataRepository` を通じて UserData 単位の整合性で更新する
- `Topic` / `NewsResource` はそれぞれ単独の小さな集約（リモート配信データであり、アプリ側では読み取り専用）
- `FollowableTopic` / `UserNewsResource` は集約ではなく、複数集約を読み取り用に合成した**ビュー（参照モデル）**。これらを永続化してはならない

## 6. 適用しない要素とその理由

- **境界づけコンテキスト**: 本アプリのドメイン（コンテンツ閲覧・パーソナライゼーション）は小さく、全画面が UserData を共有する。分割すると共有モデルの重複定義と変換だけが増え、参照用サンプルとして誤った手本になる
- **ドメインイベント**: 集約間の結果整合が必要な業務フローが存在しない。Room / DataStore の Flow による変更通知で十分
- **仕様パターン・ドメインサービス**: 該当する複雑なルールが存在しない

> 参照用サンプルとしての指針: 実案件でこれらが必要になる規模・複雑さに達した場合に導入を検討する。「使えるから使う」ではなく「必要だから使う」を原則とする。