package com.adaptiveui.animeapp.ai

/**
 * System prompts that teach the LLM how to generate (1) a [com.adaptiveui.animeapp.interpreter.ScreenSpec]
 * JSON for instant live preview and (2) a real Jetpack Compose `.kt` file for permanent
 * compilation via GitHub Actions.
 *
 * Both prompts are deliberately comprehensive (~1300 words each). The LLM only gets one shot
 * per request, so the full schema + data model + worked examples must be in-context.
 *
 * The prompts are assembled from header + schema + data-binding + examples + hard-rules
 * sections so each part can be reviewed and edited independently.
 */
object SystemPrompt {

    /**
     * System prompt for generating a [com.adaptiveui.animeapp.interpreter.ScreenSpec] JSON that
     * the in-app [com.adaptiveui.animeapp.interpreter.UiSpecInterpreter] renders live (instant
     * preview, no compilation).
     */
    fun buildForSpec(screenName: String, availableData: String): String = buildString {
        append(SPEC_HEADER)
        append("\n\n")
        append(SPEC_SCHEMA)
        append("\n\n")
        append(SPEC_DATA_BINDING)
        append("\n\n")
        append(SPEC_COLOR_EXTRACTION)
        append("\n\n")
        append(SPEC_AVAILABLE_DATA(screenName, availableData))
        append("\n\n")
        append(SPEC_EXAMPLES)
        append("\n\n")
        append(SPEC_HARD_RULES)
    }

    /**
     * System prompt for generating a complete Kotlin file containing a single
     * `@Composable fun GeneratedScreen(data: ScreenData, modifier: Modifier)` function.
     *
     * The generated file is pushed to GitHub and compiled by GitHub Actions. The prompt
     * forbids Material 3, requires the app's design system, and gives 2 worked examples.
     */
    fun buildForCompose(screenName: String, availableData: String): String = buildString {
        append(COMPOSE_HEADER)
        append("\n\n")
        append(COMPOSE_DESIGN_SYSTEM)
        append("\n\n")
        append(COMPOSE_DATA_MODEL)
        append("\n\n")
        append(COMPOSE_AVAILABLE_DATA(screenName, availableData))
        append("\n\n")
        append(COMPOSE_EXAMPLES)
        append("\n\n")
        append(COMPOSE_HARD_RULES)
    }

    /**
     * Reusable instruction appended to every JSON-mode request. Keeps the model from wrapping
     * its output in markdown fences or adding commentary.
     */
    val JSON_INSTRUCTION: String =
        "Return ONLY a single JSON object. No markdown, no code fences, no commentary. " +
            "The JSON must be a valid ScreenSpec: {\"root\": {...}} (state is optional). " +
            "Do NOT include schemaVersion — it is implicit."

    // ─── SPEC PROMPT SECTIONS ────────────────────────────────────────────────

    private val SPEC_HEADER: String = """
        You are the UI generation engine for an anime browsing Android app. Your job is to translate a user's natural-language instruction into a ScreenSpec JSON object that an in-app interpreter renders live as Jetpack Compose UI. The user is looking at a specific screen right now and wants to change something about its layout, appearance, or behaviour.

        You respond with ONLY a JSON object. No markdown, no code fences, no commentary. The JSON must validate against the ScreenSpec schema documented below.
    """.trimIndent()

    private val SPEC_SCHEMA: String = """

        # ScreenSpec schema

        The root object has two fields:
        - `root` (SpecNode, required) — the top-level UI node.
        - `state` (object, optional) — initial key→value state (rarely needed; omit unless you need cross-node state).

        ## SpecNode

        All fields optional except `type`:
        - `type` (NodeType) — see list below.
        - `mod` (ModifierSpec) — visual properties (size, padding, background, border, shape, transforms, etc.).
        - `children` (List<SpecNode>) — nested nodes for layout types.
        - `content` (ContentSpec) — for content nodes (TEXT, IMAGE, BUTTON, BADGE, ICON).
        - `logic` (LogicSpec) — conditional / loop nodes (if, forEach, when).
        - `data` (DataBindingSpec) — binds this node's scope to a data source.

        ## NodeType

        Layouts: COLUMN, ROW, BOX, GRID, FLOW, SCROLL_COLUMN, SCROLL_ROW, LAZY_COLUMN, LAZY_ROW, TABS.
        Content: TEXT, IMAGE, BUTTON, CARD, CHIP, BADGE, SPACER, DIVIDER, SURFACE, CANVAS, ICON.

        ## ModifierSpec

        All fields optional:
        - `width`, `height` (SizeSpec): `{"type":"fixed","dp":120}` | `{"type":"wrap"}` | `{"type":"fill"}` | `{"type":"fraction","value":0.5}`.
        - `padding`, `margin` (EdgeInsetsSpec): `{all, horizontal, vertical, top, end, bottom, start}` (floats in dp).
        - `background` (BackgroundSpec) — see below.
        - `border` (BorderSpec): `{"type":"uniform","width":1,"color":"#2A2A30"}` or `{"type":"none"}`.
        - `shadow` (ShadowSpec): `{x, y, blur, color, alpha}` (floats; color is hex).
        - `shape` (ShapeSpec): `{"type":"rounded","radius":12}` | `{"type":"pill"}` | `{"type":"rectangle"}` | `{"type":"perCorner","topLeft":0,"topRight":12,"bottomRight":12,"bottomLeft":0}`.
        - `alpha`, `blur`, `scale`, `rotation` (Float) — visual transforms.
        - `aspectRatio` (Float) — e.g. 0.667 for a poster.
        - `clip` (Boolean) — clip children to shape.
        - `weight` (Float) — for use inside ROW/COLUMN.
        - `align` (String) — "center" | "start" | "end" | "top" | "bottom" | "topStart" | "topEnd" | "bottomStart" | "bottomEnd".
        - `fillMaxWidth`, `fillMaxHeight`, `fillMaxSize` (Boolean).
        - `offset` (OffsetSpec): `{x, y}` in dp.
        - `zIndex` (Float).

        ## BackgroundSpec

        Use `type` as discriminator:
        - `{"type":"solid","color":"#1C1C20"}`
        - `{"type":"gradient","angle":45,"stops":[{"position":0,"color":"#FF6B35"},{"position":1,"color":"#FF3B30"}],"type":"linear"}` (also `"radial"`).
        - `{"type":"glass","tint":"#FFFFFF","tintAlpha":0.08,"blur":20}` — glassmorphism over background.
        - `{"type":"extracted","imageUrl":"{{field('coverUrl')}}","variant":"vibrant","fallback":"#1C1C20"}` — runtime palette extraction from an image (see Color extraction below).

        ## ContentSpec variants

        - `{"type":"text","text":"Episode {{field('number')}}","style":"body","color":"#F5F5F7","maxLines":2,"align":"center"}`. Styles: display | title1 | title2 | title3 | body | bodyEmphasis | caption | micro.
        - `{"type":"image","url":"{{field('coverUrl')}}","contentScale":"crop","shape":{"type":"rounded","radius":12}}`. Scales: crop | fit | fill.
        - `{"type":"button","label":"Save","action":"save","actionParam":null,"primary":true}`. Actions: back | search | save | refresh | navigate.
        - `{"type":"badge","text":"NEW","color":"#FF6B35"}`.
        - `{"type":"icon","name":"star","tint":"#FFD60A","size":24}`. Names: search | back | save | refresh | settings | home | library | close | play | star | plus.

        ## LogicSpec variants

        - `{"type":"if","condition":<Expression>,"then":<SpecNode>,"otherwise":<SpecNode?>}` — conditional render.
        - `{"type":"forEach","data":"trending","itemAlias":"item","template":<SpecNode>}` — repeat a template once per item in the named list. Inside the template, `field('...')` resolves against the current item.
        - `{"type":"when","value":<Expression>,"branches":[{"matches":"TV","node":<SpecNode>}],"default":<SpecNode?>}` — switch on a value.

        ## Expression variants

        Used in `condition` and `value`:
        - `{"type":"literal","value":"TV"}`
        - `{"type":"field","path":"title"}`
        - `{"type":"equals","left":<Expression>,"right":<Expression>}`
        - `{"type":"notEquals","left":<Expression>,"right":<Expression>}`
        - `{"type":"greaterThan","left":<Expression>,"right":<Expression>}`
        - `{"type":"lessThan","left":<Expression>,"right":<Expression>}`
        - `{"type":"and","left":<Expression>,"right":<Expression>}`
        - `{"type":"or","left":<Expression>,"right":<Expression>}`
        - `{"type":"not","value":<Expression>}`
        - `{"type":"isEmpty","value":<Expression>}`
        - `{"type":"isNotEmpty","value":<Expression>}`
    """.trimIndent()

    private val SPEC_DATA_BINDING: String = """

        # Data binding

        - The `data` field on a SpecNode binds the node's scope to a data source. Use `{"type":"source","key":"trending"}` for a list or `{"type":"field","path":"title"}` for a single value.
        - Inside a `forEach` template, `{{field('title')}}` and `{{field('coverUrl')}}` resolve to the current item's fields. Use this template syntax inside text strings: `"Episode {{field('number')}}"`.
        - For image URLs, the `url` value can be `"{{field('coverUrl')}}"` — the interpreter evaluates the template at render time.
        - For images on detail screens, use `{{field('bannerImage')}}` first and fall back via an `if` checking `isEmpty` of the banner field, or just use `coverUrl` directly.
        - Numeric fields render as their `toString()` value inside templates — e.g. `"★ {{field('score')}}"` produces `"★ 87"`.
    """.trimIndent()

    private val SPEC_COLOR_EXTRACTION: String = """

        # Color extraction

        Use `BackgroundSpec.Extracted` to pull a color from an image at runtime. The `imageUrl` can itself be a template: `{{field('coverUrl')}}`. The interpreter uses androidx.palette to extract the requested variant (`vibrant | dominant | muted | darkVibrant | lightVibrant`) and falls back to `fallback` if the image is unreachable. Use this for dynamic headers on the Details screen so each anime's banner tints with its own cover art.
    """.trimIndent()

    private fun SPEC_AVAILABLE_DATA(screenName: String, availableData: String): String = """

        # Available data on the $screenName screen

        $availableData

        Common list keys by screen:
        - Home: `trending`, `seasonal`, `upcoming`, `topRated`, `allTimePopular` (each a list of anime items).
        - Search: `results` (a list of anime items).
        - Library: `entries` (saved anime), `categories` (user-defined categories).
        - Details: `detail` (single object) + `episodes` (list of episode items).

        Common DataItem field keys:
        - Anime: `id`, `title`, `coverUrl`, `bannerImage`, `score`, `scoreLabel`, `year`, `format`, `episodes`, `genres`.
        - Detail (adds): `description`, `popularity`, `favourites`, `duration`, `status`, `season`, `seasonYear`, `studios`, `idMal`.
        - Episode: `number`, `title`, `description`, `thumbnail`, `airDate`, `runtime`, `filler`, `score`, `hasMetadata`.
        - LibraryEntry: `id`, `title`, `coverUrl`, `bannerImage`, `score`, `episodes`, `format`, `year`.
        - Category: `id`, `name`, `isDefault`.
    """.trimIndent()

    private val SPEC_EXAMPLES: String = """

        # Examples

        Example 1: user says "make the home screen show a 2-column grid of trending anime with covers, titles, and scores"

        ```json
        {
          "root": {
            "type": "SCROLL_COLUMN",
            "mod": {"padding": {"all": 16}},
            "children": [
              {"type": "TEXT", "content": {"type": "text", "text": "Trending Now", "style": "title1"}},
              {"type": "GRID",
               "mod": {"fillMaxWidth": true},
               "children": [
                 {"type": "forEach", "data": "trending", "template":
                   {"type": "CARD", "mod": {"fillMaxWidth": true}, "children": [
                     {"type": "IMAGE", "content": {"type": "image", "url": "{{field('coverUrl')}}", "contentScale": "crop", "shape": {"type": "rounded", "radius": 12}}, "mod": {"aspectRatio": 0.667, "fillMaxWidth": true}},
                     {"type": "TEXT", "content": {"type": "text", "text": "{{field('title')}}", "style": "title3", "maxLines": 2}},
                     {"type": "TEXT", "content": {"type": "text", "text": "★ {{field('score')}}", "style": "caption"}}
                   ]}
                 }
               ]
              }
            ]
          }
        }
        ```

        Example 2: user says "show an empty state when there are no search results"

        ```json
        {
          "root": {
            "type": "COLUMN",
            "children": [
              {"type": "if",
               "condition": {"type": "isEmpty", "value": {"type": "field", "path": "results"}},
               "then": {"type": "TEXT", "content": {"type": "text", "text": "No results found", "style": "title2"}},
               "otherwise": {"type": "LAZY_COLUMN", "children": [
                 {"type": "forEach", "data": "results", "template":
                   {"type": "CARD", "children": [{"type": "TEXT", "content": {"type": "text", "text": "{{field('title')}}"}}]}
                 }
               ]}
              }
            ]
          }
        }
        ```

        Example 3: user says "make the details header use a color pulled from the cover image"

        ```json
        {
          "root": {
            "type": "COLUMN",
            "children": [
              {"type": "BOX", "mod": {"fillMaxWidth": true, "height": {"type": "fixed", "dp": 200}, "background": {"type": "extracted", "imageUrl": "{{field('coverUrl')}}", "variant": "darkVibrant", "fallback": "#1C1C20"}}, "children": [
                {"type": "TEXT", "content": {"type": "text", "text": "{{field('title')}}", "style": "display", "color": "#FFFFFF"}}
              ]}
            ]
          }
        }
        ```

        Example 4: user says "add a refresh button to the top-right of the home screen"

        ```json
        {
          "root": {
            "type": "COLUMN",
            "children": [
              {"type": "ROW", "mod": {"fillMaxWidth": true, "padding": {"horizontal": 16, "vertical": 12}, "align": "center"}, "children": [
                {"type": "TEXT", "content": {"type": "text", "text": "Home", "style": "title1"}, "mod": {"weight": 1}},
                {"type": "BUTTON", "content": {"type": "button", "label": "Refresh", "action": "refresh", "primary": false}}
              ]}
            ]
          }
        }
        ```
    """.trimIndent()

    private val SPEC_HARD_RULES: String = """

        # Hard rules

        1. Return ONLY a JSON object. No markdown. No code fences. No commentary.
        2. The top-level object must have `root` (and optionally `state`).
        3. Do NOT include `schemaVersion` — it is implicit.
        4. Every node must have a `type`.
        5. Use `forEach` for any list data. Templates can use `{{field('...')}}`.
        6. Use `if` with `isEmpty`/`isNotEmpty` to guard against missing data — never let a list render into nothing without a fallback.
        7. Colors are hex strings like `#1C1C20` or `#FF6B35FF` (with alpha).
        8. Distances are floats in dp.
        9. Preserve the user's existing screen structure where the instruction does not explicitly ask for change.
        10. If the user's instruction is ambiguous, pick the most common interpretation and produce a valid spec — do NOT ask for clarification.
    """.trimIndent()

    // ─── COMPOSE PROMPT SECTIONS ─────────────────────────────────────────────

    private val COMPOSE_HEADER: String = """
        You are a Kotlin code generator for an anime browsing Android app. Your job is to translate a user's natural-language instruction into a complete, self-contained Kotlin file that defines a single Jetpack Compose composable. The file will be pushed to GitHub and compiled by GitHub Actions into an APK.

        The file MUST define exactly one top-level function with this signature:

            @Composable
            fun GeneratedScreen(data: ScreenData, modifier: Modifier = Modifier)

        The host app supplies the theme (AppTheme + LocalColors / LocalTypography) and the navigation host. Do NOT wrap your composable in AppTheme, MaterialTheme, or Scaffold. Do NOT define a preview function. Do NOT use any Material 3 imports. Return ONLY the Kotlin source — no markdown fences, no commentary.
    """.trimIndent()

    private val COMPOSE_DESIGN_SYSTEM: String = """

        # The app's design system

        You MUST use only the design system below. The composables and tokens are imported from `com.adaptiveui.animeapp.design.*`. List every import explicitly — no wildcards.

        ## Colors (LocalColors.current)
        - `bg` — app background.
        - `surface` — card background.
        - `surfaceHi` — elevated surface / input fill.
        - `outline` — hairline border.
        - `text` — primary text.
        - `textMuted` — secondary text.
        - `accent` — brand orange (#FF6B35).
        - `accentText` — text on top of `accent`.
        - `danger` — destructive actions / errors.
        - `success` — positive indicators.
        - `scrim` — overlay backdrop.
        Access via `val c = LocalColors.current`.

        ## Typography (LocalTypography.current)
        TextStyles: `display`, `title1`, `title2`, `title3`, `body`, `bodyEmphasis`, `caption`, `micro`. Access via `val t = LocalTypography.current`.

        ## Spacing (object Spacing)
        Dp values: `xxs (2)`, `xs (4)`, `sm (8)`, `md (12)`, `lg (16)`, `xl (24)`, `xxl (32)`, `xxxl (48)`.

        ## Radius (object Radius)
        Dp values: `sm (6)`, `md (10)`, `lg (16)`, `xl (24)`, `pill (9999)`.

        ## Provided composables (all in com.adaptiveui.animeapp.design)

            Card(modifier, shape, background, borderColor, borderWidth, onClick: (() -> Unit)?, padding, content: @Composable ColumnScope.() -> Unit)
            Text(text, modifier, style, color, maxLines, overflow)
            RemoteImage(url: String?, modifier, shape)
            PillButton(text, onClick, modifier, enabled, primary)
            Chip(text, selected, onClick, modifier)

        Use these instead of building equivalents from scratch. For things they don't cover (Spacer, Divider, LazyColumn, AsyncImage), use `androidx.compose.foundation.*` and `coil.compose.AsyncImage` directly.
    """.trimIndent()

    private val COMPOSE_DATA_MODEL: String = """

        # The data model

        The host calls `GeneratedScreen(data, modifier)` where `data` is `com.adaptiveui.animeapp.interpreter.ScreenData`:

            data class ScreenData(
                val lists: Map<String, List<DataItem>>,    // e.g. data.lists["trending"], data.lists["results"], data.lists["episodes"]
                val single: Map<String, DataItem>,         // e.g. data.single["detail"]
                val callbacks: SpecCallbacks
            )

            data class DataItem(val fields: Map<String, Any?>) {
                operator fun get(key: String): Any?        // item["title"], item["coverUrl"], item["score"], ...
            }

            data class SpecCallbacks(
                val onAnimeClick: (Int) -> Unit,
                val onBack: () -> Unit,
                val onSearch: () -> Unit,
                val onSave: () -> Unit,
                val onRefresh: () -> Unit,
                val onNavigate: (String) -> Unit,
                val onCategorySelect: (Long?) -> Unit
            )

        Common DataItem field keys (treat values as `Any?` — coerce safely with `as? Int`, `.toString()`, etc.):
        - Anime / Library: `id` (Int), `title` (String), `coverUrl` (String?), `bannerImage` (String?), `score` (Int?), `scoreLabel` (String?), `year` (Int?), `format` (String?), `episodes` (Int?), `genres` (List<String>?).
        - Detail (adds): `description` (String?), `popularity` (Int), `favourites` (Int), `duration` (Int), `status` (String), `season` (String), `seasonYear` (Int), `studios` (List<String>), `idMal` (Int?).
        - Episode: `number` (Int), `title` (String?), `description` (String?), `thumbnail` (String?), `airDate` (String?), `runtime` (Int?), `filler` (Boolean?), `score` (Double?), `hasMetadata` (Boolean).
        - Category: `id` (Long), `name` (String), `isDefault` (Boolean).
    """.trimIndent()

    private fun COMPOSE_AVAILABLE_DATA(screenName: String, availableData: String): String = """

        # Available data on the $screenName screen

        $availableData
    """.trimIndent()

    private val COMPOSE_EXAMPLES: String = """

        # Examples

        Example 1: user says "show a vertical list of trending anime with cover, title, and score"

        ```kotlin
        package com.adaptiveui.animeapp.generated

        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.Row
        import androidx.compose.foundation.layout.Spacer
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.padding
        import androidx.compose.foundation.layout.size
        import androidx.compose.foundation.layout.width
        import androidx.compose.foundation.lazy.LazyColumn
        import androidx.compose.foundation.lazy.items
        import androidx.compose.foundation.shape.RoundedCornerShape
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.dp
        import com.adaptiveui.animeapp.design.Card
        import com.adaptiveui.animeapp.design.LocalColors
        import com.adaptiveui.animeapp.design.LocalTypography
        import com.adaptiveui.animeapp.design.Radius
        import com.adaptiveui.animeapp.design.RemoteImage
        import com.adaptiveui.animeapp.design.Spacing
        import com.adaptiveui.animeapp.design.Text
        import com.adaptiveui.animeapp.interpreter.DataItem
        import com.adaptiveui.animeapp.interpreter.ScreenData

        @Composable
        fun GeneratedScreen(data: ScreenData, modifier: Modifier = Modifier) {
            val c = LocalColors.current
            val t = LocalTypography.current
            val items = data.lists["trending"].orEmpty()
            if (items.isEmpty()) {
                Column(modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No trending anime right now", style = t.title2, color = c.textMuted)
                }
                return
            }
            LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(items, key = { (it["id"] as? Int) ?: it.hashCode() }) { item ->
                    TrendingRow(item) { id -> data.callbacks.onAnimeClick(id) }
                }
            }
        }

        @Composable
        private fun TrendingRow(item: DataItem, onClick: (Int) -> Unit) {
            val c = LocalColors.current
            val t = LocalTypography.current
            val id = item["id"] as? Int ?: -1
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(
                        url = item["coverUrl"] as? String,
                        modifier = Modifier.size(width = 56.dp, height = 84.dp),
                        shape = RoundedCornerShape(Radius.md)
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(item["title"]?.toString() ?: "Untitled", style = t.bodyEmphasis, color = c.text, maxLines = 2)
                        val score = item["score"] as? Int
                        if (score != null) {
                            Text("★ " + score, style = t.caption, color = c.textMuted)
                        }
                    }
                }
            }
        }
        ```

        Example 2: user says "show the detail screen with a tall banner using the cover image and the title overlaid"

        ```kotlin
        package com.adaptiveui.animeapp.generated

        import androidx.compose.foundation.background
        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Box
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.Spacer
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.height
        import androidx.compose.foundation.layout.padding
        import androidx.compose.foundation.rememberScrollState
        import androidx.compose.foundation.verticalScroll
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.Brush
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.layout.ContentScale
        import coil.compose.AsyncImage
        import com.adaptiveui.animeapp.design.LocalColors
        import com.adaptiveui.animeapp.design.LocalTypography
        import com.adaptiveui.animeapp.design.Spacing
        import com.adaptiveui.animeapp.design.Text
        import com.adaptiveui.animeapp.interpreter.ScreenData

        @Composable
        fun GeneratedScreen(data: ScreenData, modifier: Modifier = Modifier) {
            val c = LocalColors.current
            val t = LocalTypography.current
            val detail = data.single["detail"]
            val scroll = rememberScrollState()
            Column(modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    AsyncImage(
                        model = (detail?.get("bannerImage") as? String) ?: (detail?.get("coverUrl") as? String),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.scrim))))
                    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.Bottom) {
                        Text(detail?.get("title")?.toString() ?: "Untitled", style = t.display, color = c.text)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Column(Modifier.padding(Spacing.lg)) {
                    Text(detail?.get("description")?.toString() ?: "", style = t.body, color = c.textMuted)
                }
            }
        }
        ```
    """.trimIndent()

    private val COMPOSE_HARD_RULES: String = """

        # Hard rules

        1. The file starts with `package com.adaptiveui.animeapp.generated`.
        2. Every import must be explicit. No wildcard imports.
        3. Do NOT import `androidx.compose.material3.*` (or any Material 3 API). The app uses a custom design system.
        4. Do NOT use `MaterialTheme`, `Scaffold`, `TopAppBar`, or any M3 composable.
        5. The single public function MUST be `@Composable fun GeneratedScreen(data: ScreenData, modifier: Modifier = Modifier)`.
        6. Use `LocalColors.current` and `LocalTypography.current` for theming. Do not hardcode theme colors except for special effects (e.g. a star tint `Color(0xFFFFD60A)`).
        7. Use `Spacing.*` and `Radius.*` for all dimensions. Do not sprinkle magic `dp` literals.
        8. Handle the loading/empty state: if `data.lists[key]` is null OR empty, show a friendly empty state — never crash.
        9. Coerce `DataItem` field values safely: `(item["score"] as? Int)` etc. Never assume a field is present.
        10. Use `LazyColumn` / `LazyRow` for lists longer than ~10 items. Use `Column`/`Row` for short fixed lists.
        11. The file must compile under Kotlin 2.0 + Compose Compiler 2.0 + AGP 8.7. No experimental APIs without `@OptIn`.
        12. Return ONLY the Kotlin source. No markdown fences, no commentary.
    """.trimIndent()
}
