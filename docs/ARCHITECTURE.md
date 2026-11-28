# Architecture & conventions

Native Kotlin/Compose port of the Code7 Tech Updates web app. The web app is
the behavioural spec: `docs/reference/web-index.html` (single file, JS at the
bottom). When in doubt, match what it does.

## Stack
- Kotlin, Jetpack Compose + Material 3, Navigation Compose
- Coroutines + Flow; ViewModels expose `StateFlow<UiState>`
- Networking: Ktor client (OkHttp engine) + kotlinx-serialization (`ignoreUnknownKeys = true`)
- Persistence: Room (KSP) for library/learner data, DataStore (preferences) for settings
- Images: Coil 3
- DI: **manual** — `AppContainer` created in `TechUpdatesApp : Application`; no Hilt/Koin
- Tests: JUnit4, kotlinx-coroutines-test, Turbine, Ktor `MockEngine`, Room in-memory DB via Robolectric

## Packages (`com.code7.techupdates`)
```
data/remote/     HnApi (Algolia), ReaderApi (Jina), DTOs
data/local/      Room DB, entities, DAOs; DataStore settings
data/            repositories (StoryRepository, LibraryRepository, ArticleRepository)
domain/          pure Kotlin models + logic (Story, Comment, Topic, FeedQuery, timeAgo, domainOf)
recommender/     "For you" model — pure Kotlin, no Android deps (port of the JS Learner)
ui/theme/        theme
ui/feed/  ui/library/  ui/reader/  ui/comments/  ui/foryou/  ui/common/
```

## Testing Strategy

**Unit Tests**
- Framework: JUnit 5 + Kotlin Test
- Coverage target: ≥70% line coverage (enforced by Kover)
- Coverage tool: [Kover](https://github.com/Kotlin/kover) (v0.9.11)
- Scope: `data`, `domain`, `recommender` packages only
- Exclusions: Room-generated DAOs (`data.local`), `ui` package, `*_Impl` classes
- Command: `./gradlew koverHtmlReportDebug` (HTML report at `app/build/reports/kover/htmlDebug/index.html`)
- Verification: `./gradlew koverVerify` (fails CI if coverage < 70%)

**CI/CD**
- GitHub Actions workflow: `.github/workflows/ci-coverage.yml`
- Runs on: push to `main`, `develop`, `feat/**`; pull requests to `main`
- Steps: build → unit tests with Kover instrumentation → HTML report upload → XML report for validation

## Rules
- `domain/` and `recommender/` must not import Android or data-layer classes (pure JVM, fast tests).
- Every public function with logic gets a unit test. Fixtures live in `app/src/test/resources/`.
- No API keys in the app. All endpoints are public: `https://hn.algolia.com/api/v1/`,
  `https://r.jina.ai/<url>`, favicons `https://icons.duckduckgo.com/ip3/<domain>.ico`.
- Strings in `res/values/strings.xml`; no hard-coded user-visible text in composables.
- Acceptance command before every commit (must pass):
  `./gradlew assembleDebug lintDebug ktlintCheck detekt testDebugUnitTest`
- Commit messages: Conventional Commits with issue number, e.g. `feat: feed screen (#11)`.
