# Release Checks

## APK Sizes

| Build   | Size  | Notes                                      |
|---------|-------|--------------------------------------------|
| Debug   | 18 MB | Uncompressed, debug symbols included       |
| Release | 2.4 MB | R8 minification + resource shrinking on |

## R8 Keep Rules (`app/proguard-rules.pro`)

| Rule | Why |
|------|-----|
| `data.remote.**Dto` | `@Serializable` kotlinx-serialization DTOs must survive obfuscation; Ktor's `Json` deserialiser looks up fields by name at runtime. |
| `kotlinx.serialization.internal.**` | Serialization runtime internals used by `ignoreUnknownKeys = true` and `@SerialName`-based field mapping. |
| `kotlinx.serialization.**` constructors | KSP-generated `KSerializer` factories need default constructors. |
| `data.local.**_Impl` | Room-generated DAO implementation classes (KSP output). |
| `com.mikepenz.markdown.coil3.**` | Markdown renderer's `Coil3ImageTransformerImpl` — Coil 3 image loading at runtime. |
| `data.local.SettingsStore` | DataStore preferences wrapper; generated classes need to stay. |

## Accessibility Checklist

### Screen Titles — `Modifier.semantics { heading() }`
| Screen | File | Line |
|--------|------|------|
| Feed (app name) | `FeedScreen.kt` | 215 |
| Library | `LibraryScreen.kt` | 147 |
| Comments (Discussion) | `CommentsScreen.kt` | 84 |
| Comments (story title) | `CommentsScreen.kt` | 175 |
| Reader (domain) | `ReaderScreen.kt` | 209 |
| Settings | `SettingsScreen.kt` | 101 |

### Story Cards — `semantics(mergeDescendants = true)`
| Change | File | Line |
|--------|------|------|
| `OutlinedCard` now merges descendants with a contentDescription (`title · domain · points · comments`) | `StoryCard.kt` | 56-63 |

### Icon-Only Buttons — `contentDescription` string resources
All clickable `IconButton` / `IconToggleButton` instances already had string-resource `contentDescription` values:
- Feed overflow menu → `feed_more_options`
- Feed search close → `feed_search_close`
- Feed search open → `feed_search_open`
- Feed search clear → `feed_search_clear`
- Reader back → `icon_back`
- Reader save/unsave → `reader_save` / `reader_unsave`
- Reader share → `reader_share`
- Reader open original → `reader_open_original`
- Comments back → `icon_back`
- Comments open on HN → `comments_open_on_hn`
- Settings back → `icon_back`
- Story card bookmark → `card_read_later` / `card_remove_later`

Decorative icons (favicon, arrow icons in meta rows, byline icons) use `contentDescription = null`.

### Touch Targets
- `FilterChip` (topic chips) — Material 3 default ≥ 48 dp
- `TextButton` (comment count, discuss) — Material 3 default
- `OutlinedCard` (story cards) — full card is clickable
- `Modifier.clickable` (comment rows with replies) — uses Material 3 defaults
- `IconToggleButton` (bookmark) — 20 dp icon inside Material 3 toggle (≥ 48 dp container)

### Test Impact
No Robolectric test selectors needed updating — existing selectors match on story titles, tab labels, and content descriptions that did not change. All 35+ UI tests pass.
