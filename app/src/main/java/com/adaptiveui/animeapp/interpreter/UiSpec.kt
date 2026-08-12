package com.adaptiveui.animeapp.interpreter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * UI SPECIFICATION — a JSON DSL that the AI generates and the [UiSpecInterpreter] renders live.
 *
 * This is NOT a config system with preset enums. It's a Turing-complete-ish declarative language:
 *   - Arbitrary nesting of layout + content nodes
 *   - Per-node modifiers (size, padding, background, border, shadow, shape, gradient, blur, etc.)
 *   - Logic: `if`/`when` conditionals, `forEach` loops over data, `let` bindings
 *   - Data binding: `data("trending")`, `field("title")`, `field("coverUrl")`
 *   - Color extraction: `extractColor("url", "vibrant")` — pulls color from an image at runtime
 *   - Expressions: arithmetic, string concatenation, comparisons
 *
 * The AI also generates real Compose .kt files for permanent compilation (see GitHub Actions).
 * This spec is for INSTANT live preview.
 */

// ─── Root ───────────────────────────────────────────────────────────────────

@Serializable
data class ScreenSpec(
    val root: SpecNode,
    val state: Map<String, JsonElement> = emptyMap()
)

// ─── Node ───────────────────────────────────────────────────────────────────

@Serializable
data class SpecNode(
    val type: NodeType,
    val mod: ModifierSpec = ModifierSpec(),
    val children: List<SpecNode> = emptyList(),
    val content: ContentSpec? = null,
    val logic: LogicSpec? = null,
    val data: DataBindingSpec? = null
)

@Serializable
enum class NodeType {
    // Layouts
    COLUMN, ROW, BOX, GRID, FLOW, SCROLL_COLUMN, SCROLL_ROW, LAZY_COLUMN, LAZY_ROW, TABS,
    // Content
    TEXT, IMAGE, BUTTON, CARD, CHIP, BADGE, SPACER, DIVIDER, SURFACE, CANVAS, ICON
}

// ─── Modifier ───────────────────────────────────────────────────────────────

@Serializable
data class ModifierSpec(
    val width: SizeSpec? = null,
    val height: SizeSpec? = null,
    val padding: EdgeInsetsSpec? = null,
    val margin: EdgeInsetsSpec? = null,
    val background: BackgroundSpec? = null,
    val border: BorderSpec? = null,
    val shadow: ShadowSpec? = null,
    val shape: ShapeSpec? = null,
    val alpha: Float? = null,
    val blur: Float? = null,
    val scale: Float? = null,
    val rotation: Float? = null,
    val aspectRatio: Float? = null,
    val clip: Boolean? = null,
    val weight: Float? = null,
    val align: String? = null,       // "center" | "start" | "end" | "top" | "bottom" | "topStart"...
    val fillMaxWidth: Boolean? = null,
    val fillMaxHeight: Boolean? = null,
    val fillMaxSize: Boolean? = null,
    val offset: OffsetSpec? = null,
    val zIndex: Float? = null
)

@Serializable
sealed class SizeSpec {
    @Serializable
    @SerialName("fixed")
    data class Fixed(val dp: Float) : SizeSpec()
    @Serializable
    @SerialName("wrap")
    data object Wrap : SizeSpec()
    @Serializable
    @SerialName("fill")
    data object Fill : SizeSpec()
    @Serializable
    @SerialName("fraction")
    data class Fraction(val value: Float) : SizeSpec()
}

@Serializable
data class EdgeInsetsSpec(
    val all: Float? = null,
    val horizontal: Float? = null,
    val vertical: Float? = null,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
    val start: Float = 0f
)

@Serializable
sealed class BackgroundSpec {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: String) : BackgroundSpec()
    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val angle: Float = 0f,
        val stops: List<GradientStop>,
        val type: String = "linear"   // "linear" | "radial"
    ) : BackgroundSpec()
    @Serializable
    @SerialName("glass")
    data class Glass(
        val tint: String = "#FFFFFF",
        val tintAlpha: Float = 0.08f,
        val blur: Float = 20f
    ) : BackgroundSpec()
    @Serializable
    @SerialName("extracted")
    data class Extracted(
        val imageUrl: String,         // can be a data binding expression
        val variant: String = "vibrant", // vibrant | dominant | muted | darkVibrant | lightVibrant
        val fallback: String = "#1C1C20"
    ) : BackgroundSpec()
}

@Serializable
data class GradientStop(val position: Float = 0f, val color: String)

@Serializable
sealed class BorderSpec {
    @Serializable
    @SerialName("uniform")
    data class Uniform(val width: Float = 1f, val color: String = "#2A2A30")
    : BorderSpec()
    @Serializable
    @SerialName("none")
    data object None : BorderSpec()
}

@Serializable
data class ShadowSpec(
    val x: Float = 0f,
    val y: Float = 4f,
    val blur: Float = 12f,
    val color: String = "#000000",
    val alpha: Float = 0.25f
)

@Serializable
sealed class ShapeSpec {
    @Serializable
    @SerialName("rounded")
    data class Rounded(val radius: Float = 12f) : ShapeSpec()
    @Serializable
    @SerialName("pill")
    data object Pill : ShapeSpec()
    @Serializable
    @SerialName("rectangle")
    data object Rectangle : ShapeSpec()
    @Serializable
    @SerialName("perCorner")
    data class PerCorner(
        val topLeft: Float = 0f,
        val topRight: Float = 0f,
        val bottomRight: Float = 0f,
        val bottomLeft: Float = 0f
    ) : ShapeSpec()
}

@Serializable
data class OffsetSpec(val x: Float = 0f, val y: Float = 0f)

// ─── Content ────────────────────────────────────────────────────────────────

@Serializable
sealed class ContentSpec {
    @Serializable
    @SerialName("text")
    data class TextContent(
        val text: String,                     // can contain data bindings like "Episode {{field('number')}}"
        val style: String = "body",           // display | title1 | title2 | title3 | body | bodyEmphasis | caption | micro
        val color: String? = null,
        val maxLines: Int? = null,
        val align: String? = null             // start | center | end
    ) : ContentSpec()

    @Serializable
    @SerialName("image")
    data class ImageContent(
        val url: String,                      // can be a data binding
        val contentScale: String = "crop",    // crop | fit | fill
        val shape: ShapeSpec? = null
    ) : ContentSpec()

    @Serializable
    @SerialName("button")
    data class ButtonContent(
        val label: String,
        val action: String? = null,           // back | search | save | refresh | navigate
        val actionParam: String? = null,      // e.g. navigate target
        val primary: Boolean = true
    ) : ContentSpec()

    @Serializable
    @SerialName("badge")
    data class BadgeContent(
        val text: String,
        val color: String = "#FF6B35"
    ) : ContentSpec()

    @Serializable
    @SerialName("icon")
    data class IconContent(
        val name: String,                     // search | back | save | refresh | settings | home | library | close | play | star | plus
        val tint: String? = null,
        val size: Float = 24f
    ) : ContentSpec()
}

// ─── Logic (conditionals, loops, bindings) ──────────────────────────────────

@Serializable
sealed class LogicSpec {
    @Serializable
    @SerialName("if")
    data class If(
        val condition: Expression,
        val then: SpecNode,
        val otherwise: SpecNode? = null
    ) : LogicSpec()

    @Serializable
    @SerialName("forEach")
    data class ForEach(
        val data: String,                     // data key, e.g. "trending", "episodes"
        val itemAlias: String = "item",       // bound name in the child scope
        val template: SpecNode
    ) : LogicSpec()

    @Serializable
    @SerialName("when")
    data class When(
        val value: Expression,
        val branches: List<WhenBranch>,
        val default: SpecNode? = null
    ) : LogicSpec()
}

@Serializable
data class WhenBranch(
    val matches: String,                      // literal or expression
    val node: SpecNode
)

// ─── Data binding ───────────────────────────────────────────────────────────

@Serializable
sealed class DataBindingSpec {
    @Serializable
    @SerialName("source")
    data class Source(val key: String) : DataBindingSpec()  // e.g. "trending", "detail", "episodes"

    @Serializable
    @SerialName("field")
    data class Field(val path: String) : DataBindingSpec()  // e.g. "title", "coverUrl", "averageScore"
}

// ─── Expressions ────────────────────────────────────────────────────────────

@Serializable
sealed class Expression {
    @Serializable
    @SerialName("literal")
    data class Literal(val value: String) : Expression()       // string/int/float literal

    @Serializable
    @SerialName("field")
    data class Field(val path: String) : Expression()          // data field path

    @Serializable
    @SerialName("equals")
    data class Equals(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("notEquals")
    data class NotEquals(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("greaterThan")
    data class GreaterThan(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("lessThan")
    data class LessThan(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("and")
    data class And(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("or")
    data class Or(val left: Expression, val right: Expression) : Expression()

    @Serializable
    @SerialName("not")
    data class Not(val value: Expression) : Expression()

    @Serializable
    @SerialName("isEmpty")
    data class IsEmpty(val value: Expression) : Expression()

    @Serializable
    @SerialName("isNotEmpty")
    data class IsNotEmpty(val value: Expression) : Expression()
}
