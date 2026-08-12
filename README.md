# Aniverse — Adaptive Anime UI

A demo Android application exploring **fully user-configurable user interfaces**. Users can redesign the entire app UI by uploading a JSON config file or by describing changes to an AI that generates the config and applies it live.

> This is a **demo / feasibility study**: is a fully-configurable, AI-redesignable mobile UI practical? The app fetches real anime data from AniList and shows it across Home, Library, Search, Details, and Settings screens — every one of which is driven by a JSON config.

## Screens

| Screen | Description |
|---|---|
| **Home** | Trending / Seasonal / Upcoming / Top Rated / All-Time-Popular sections from AniList. Fully cached. |
| **Library** | User's saved anime organized by categories. Default category cannot be deleted. Long-press a category to rename/delete; long-press the Save button on Details to pick categories (multi-select). |
| **Search** | Live search with 500ms debounce + filters (genre, year, season, format, status, sort). Shows popular results by default. Infinite-scroll pagination. |
| **Details** | Full AniList data (banner, cover, score, synopsis, info, genres, episodes, characters, relations, recommendations, trailer). Episode list merges metadata from **ani.zip** (primary) + **Jikan** (fallback) + placeholder for gaps. Save button (tap = Default, long-press = category picker). Refresh reloads everything. |
| **Settings** | Dark/Light/Auto theme, full color palette editor, AI configuration, config upload (full or per-screen), per-screen live editors, reset. |

## Customizable UI

The entire UI is driven by a `UiConfig` JSON schema:

```json
{
  "schemaVersion": 1,
  "theme": { "mode": "AUTO", "colors": { "primary": "#FF8906", ... }, "typography": {...}, "shapes": {...} },
  "home":    { "visibleSections": [...], "sectionLayout": {...}, "itemsPerRow": 3, "heroBanner": true, ... },
  "library": { "layout": "GRID", "itemsPerRow": 3, "sortBy": "SAVED_DESC", ... },
  "search":  { "defaultSort": "POPULARITY_DESC", "resultsLayout": "GRID", "enabledFilters": [...], ... },
  "details": { "visibleSections": [...], "episodeListLayout": "LIST_WITH_THUMBNAIL", ... },
  "global":  { "cardStyle": "ELEVATED", "bottomNavStyle": "PILL_FLOATING", ... }
}
```

Customization is **not just colors** — it drives layout (grid/list/carousel), visible sections, items-per-row, sort orders, enabled filters, component styles, shapes, typography, motion, and bottom-nav style.

## AI Quick Edit

A floating bubble on every screen lets the user describe UI changes in natural language. The request goes to an OpenAI-compatible LLM with a strict system prompt that returns a valid `UiConfig` JSON. The user previews and taps **Apply** to apply the change live.

**Default free model presets** (user pastes their own API key):
- Groq · gpt-oss-20b
- Google Gemini 2.5 Flash (1M ctx)
- Cerebras · Llama 3.1 8B (fastest)
- SambaNova · Llama 3.3 70B
- OpenRouter · gpt-oss-20b:free
- Mistral · Small
- Together AI · Llama 3.3 70B Free

Custom base URL + model ID entry is also supported.

## Data Sources

| Source | Role |
|---|---|
| **AniList GraphQL** (`graphql.anilist.co`) | Primary anime data — home, details, search. Full response cached verbatim in Room. |
| **ani.zip** (`api.ani.zip/mappings?anilist_id=`) | Primary episode metadata — thumbnails, titles, descriptions, air dates. Accepts AniList ID directly. |
| **Jikan v4** (`api.jikan.moe/v4/anime/{mal_id}/episodes`) | Fallback episode titles/airdates/filler flags via AniList `idMal`. |

Episode merge priority: ani.zip → Jikan → placeholder "Episode N".

## Tech Stack

- **Kotlin 2.0.21** + **Jetpack Compose** (BOM 2024.12)
- **Custom theme system** — NO Material 3. All UI built on a `LocalColors` / `LocalTypography` / `LocalShapes` CompositionLocal system driven by the config.
- **Hilt** for DI, **Room** for cache + library, **Retrofit + OkHttp + kotlinx.serialization** for networking, **Coil** for images, **DataStore** for preferences, **Navigation Compose** for routing.

## Build

APKs are built via GitHub Actions (see `.github/workflows/build.yml`). Artifacts are uploaded on every push; tagged releases publish a GitHub Release with the APKs.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
```

## Project Structure

```
app/src/main/java/com/adaptiveui/animeapp/
├── AnimeApp.kt              # @HiltAndroidApp
├── MainActivity.kt          # single-activity host
├── core/
│   ├── database/            # Room entities, DAOs, AnimeDatabase
│   ├── datastore/           # SettingsDataStore (UiConfig + AiSettings persistence)
│   ├── di/                  # Hilt NetworkModule, DatabaseModule
│   └── network/             # Retrofit clients: anilist, anizip, jikan, openai
├── data/
│   ├── mappers/             # DTO → domain → entity
│   └── repository/          # AnimeRepository, EpisodeRepository, LibraryRepository, AiRepository
├── domain/
│   ├── config/              # UiConfig, ThemeConfig, ScreenConfig schema
│   └── model/               # AnimeCard, AnimeDetail, Episode, Category, AiSettings, Search
└── ui/
    ├── theme/               # AdaptiveTheme, AdaptiveColors, AdaptiveTypography, AdaptiveShapes
    ├── components/          # AdaptiveCard, Button, Chip, Switch, TextField, AnimeCard, EpisodeCard, ...
    ├── config/              # ConfigViewModel, LocalUiConfig
    ├── ai/                  # AiBubbleHost, AiEditPanel, SystemPrompt, FreeModelPresets
    ├── navigation/          # Routes, AppRoot, AppNavGraph
    └── screens/             # home, library, search, details, settings
```

## License

Demo project. All anime data © respective rights holders (AniList, ani.zip, MyAnimeList/Jikan).
