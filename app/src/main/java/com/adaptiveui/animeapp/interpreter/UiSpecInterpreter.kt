package com.adaptiveui.animeapp.interpreter

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adaptiveui.animeapp.design.Chip
import com.adaptiveui.animeapp.design.LocalColors
import com.adaptiveui.animeapp.design.LocalTypography
import com.adaptiveui.animeapp.design.PillButton
import com.adaptiveui.animeapp.design.Radius
import com.adaptiveui.animeapp.design.RemoteImage
import com.adaptiveui.animeapp.design.Spacing
import com.adaptiveui.animeapp.design.Text as DesignText
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

// ──────────────────────────────────────────────────────────────────────────────
// UiSpecInterpreter — renders an AI-generated [ScreenSpec] tree against a
// [ScreenData] context into a live Compose UI. This is the engine that lets the
// app re-skin itself instantly from JSON. NO Material 3 imports — only the
// custom design system + foundation.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Render-time scope. Holds named bindings (e.g. forEach aliases) plus the
 * "current item" — the [DataItem] used to resolve bare `field("title")`
 * expressions and `{{title}}` template substitutions.
 */
data class Scope(
    val bindings: Map<String, Any?> = emptyMap(),
    val currentItem: DataItem? = null
) {
    /** Add a named binding (currentItem unchanged). */
    fun child(name: String, value: Any?): Scope =
        Scope(bindings + (name to value), currentItem)

    /** Add a named item binding AND set it as the current item. */
    fun childItem(alias: String, item: DataItem): Scope =
        Scope(bindings + (alias to item), currentItem = item)

    /** Replace the current item without adding a binding. */
    fun withItem(item: DataItem?): Scope = Scope(bindings, item)

    /** Look up a named item binding. */
    fun item(alias: String): DataItem? = bindings[alias] as? DataItem
}

// ─── Public entry point ──────────────────────────────────────────────────────

/**
 * Render [spec] against [data]. Walks the [SpecNode] tree starting at [ScreenSpec.root],
 * resolving logic (if/forEach/when), data bindings, expressions and modifiers
 * into a live Compose hierarchy. Never crashes on malformed input — falls back
 * to a small placeholder Box for unknown nodes.
 */
@Composable
fun UiSpecInterpreter(
    spec: ScreenSpec,
    data: ScreenData,
    colorExtractor: ColorExtractor? = null,
    modifier: Modifier = Modifier
) {
    val rootScope = Scope(
        bindings = data.single,
        currentItem = data.single.values.firstOrNull()
    )
    RenderNode(spec.root, rootScope, data, colorExtractor, modifier = modifier)
}

// ─── Recursive node renderer ─────────────────────────────────────────────────

/**
 * Renders a single [SpecNode]. If [node.logic] is set, the logic is evaluated
 * and the result rendered instead of [node] itself. Otherwise the node's type,
 * modifier and content are dispatched to the appropriate composable.
 *
 * @param modifier Pre-applied scope modifiers from the parent (e.g. `weight`,
 *                 `align` from Row/Column/Box scope). Applied before the node's
 *                 own [ModifierSpec].
 */
@Composable
private fun RenderNode(
    node: SpecNode,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?,
    modifier: Modifier = Modifier
) {
    // Update scope if node has a data binding (Source/Field).
    val effectiveScope = scope.withDataBinding(node.data, data)

    // Logic takes precedence over rendering the node itself.
    val logic = node.logic
    if (logic != null) {
        RenderLogic(logic, effectiveScope, data, colorExtractor, modifier)
        return
    }

    // No logic — render the node itself.
    val shape = node.mod.shape.toShape(defaultFor(node.type))
    val resolvedMod = modifier.then(node.mod.toModifier(shape, effectiveScope, colorExtractor))

    when (node.type) {
        // Containers
        NodeType.COLUMN -> renderColumn(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.ROW -> renderRow(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.BOX -> renderBox(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.GRID -> renderGrid(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.FLOW -> renderFlow(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.SCROLL_COLUMN -> renderScrollColumn(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.SCROLL_ROW -> renderScrollRow(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.LAZY_COLUMN -> renderLazyColumn(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.LAZY_ROW -> renderLazyRow(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.TABS -> renderTabs(node, effectiveScope, data, colorExtractor, resolvedMod)
        // Content
        NodeType.TEXT -> renderText(node, effectiveScope, resolvedMod)
        NodeType.IMAGE -> renderImage(node, effectiveScope, resolvedMod)
        NodeType.BUTTON -> renderButton(node, effectiveScope, data, resolvedMod)
        NodeType.CARD -> renderCard(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.CHIP -> renderChip(node, effectiveScope, data, resolvedMod)
        NodeType.BADGE -> renderBadge(node, effectiveScope, resolvedMod)
        NodeType.SPACER -> renderSpacer(node, resolvedMod)
        NodeType.DIVIDER -> renderDivider(node, resolvedMod)
        NodeType.SURFACE -> renderSurface(node, effectiveScope, data, colorExtractor, resolvedMod)
        NodeType.CANVAS -> renderCanvas(node, resolvedMod)
        NodeType.ICON -> renderIcon(node, effectiveScope, resolvedMod)
    }
}

/** Pick a sensible default shape per node type (used when mod.shape == null). */
private fun defaultFor(type: NodeType): Shape = when (type) {
    NodeType.CARD, NodeType.SURFACE -> RoundedCornerShape(Radius.lg)
    NodeType.BUTTON, NodeType.CHIP, NodeType.BADGE -> RoundedCornerShape(Radius.pill)
    else -> RectangleShape
}

// ─── Logic handling ───────────────────────────────────────────────────────────

@Composable
private fun RenderLogic(
    logic: LogicSpec,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?,
    modifier: Modifier
) {
    when (logic) {
        is LogicSpec.If -> {
            val cond = evalExpression(logic.condition, scope)
            val target = if (toBool(cond)) logic.then else logic.otherwise
            if (target != null) {
                RenderNode(target, scope, data, colorExtractor, modifier)
            }
        }
        is LogicSpec.ForEach -> {
            val list = data.lists[logic.data] ?: emptyList()
            list.forEach { item ->
                val childScope = scope.childItem(logic.itemAlias, item)
                // ForEach iterations don't inherit the parent's scope modifier
                // (weight/align) — each instance uses its own template.mod.
                RenderNode(logic.template, childScope, data, colorExtractor, Modifier)
            }
        }
        is LogicSpec.When -> {
            val v = evalExpression(logic.value, scope)
            val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                ?: logic.default
            if (target != null) {
                RenderNode(target, scope, data, colorExtractor, modifier)
            }
        }
    }
}

// ─── Scope-aware child rendering ──────────────────────────────────────────────
// In Row/Column/Box scopes, children may have weight/align modifiers that
// require the parent's ReceiverScope. We resolve the logic of each child
// inline so weight/align can be applied to the actual rendered node(s).

@Composable
private fun RowScope.renderNodes(
    children: List<SpecNode>,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?
) {
    children.forEach { child ->
        when (val logic = child.logic) {
            null -> {
                val wMod = child.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                RenderNode(child, scope, data, colorExtractor, wMod)
            }
            is LogicSpec.If -> {
                val cond = evalExpression(logic.condition, scope)
                val target = if (toBool(cond)) logic.then else logic.otherwise
                if (target != null) {
                    val wMod = target.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, wMod)
                }
            }
            is LogicSpec.ForEach -> {
                val list = data.lists[logic.data] ?: emptyList()
                list.forEach { item ->
                    val cs = scope.childItem(logic.itemAlias, item)
                    val wMod = logic.template.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(logic.template, cs, data, colorExtractor, wMod)
                }
            }
            is LogicSpec.When -> {
                val v = evalExpression(logic.value, scope)
                val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                    ?: logic.default
                if (target != null) {
                    val wMod = target.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, wMod)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.renderNodes(
    children: List<SpecNode>,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?
) {
    children.forEach { child ->
        when (val logic = child.logic) {
            null -> {
                val wMod = child.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                RenderNode(child, scope, data, colorExtractor, wMod)
            }
            is LogicSpec.If -> {
                val cond = evalExpression(logic.condition, scope)
                val target = if (toBool(cond)) logic.then else logic.otherwise
                if (target != null) {
                    val wMod = target.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, wMod)
                }
            }
            is LogicSpec.ForEach -> {
                val list = data.lists[logic.data] ?: emptyList()
                list.forEach { item ->
                    val cs = scope.childItem(logic.itemAlias, item)
                    val wMod = logic.template.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(logic.template, cs, data, colorExtractor, wMod)
                }
            }
            is LogicSpec.When -> {
                val v = evalExpression(logic.value, scope)
                val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                    ?: logic.default
                if (target != null) {
                    val wMod = target.mod.weight?.let { Modifier.weight(it) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, wMod)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.renderNodes(
    children: List<SpecNode>,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?
) {
    children.forEach { child ->
        when (val logic = child.logic) {
            null -> {
                val aMod = child.mod.align?.let { Modifier.align(parseBoxAlign(it)) } ?: Modifier
                RenderNode(child, scope, data, colorExtractor, aMod)
            }
            is LogicSpec.If -> {
                val cond = evalExpression(logic.condition, scope)
                val target = if (toBool(cond)) logic.then else logic.otherwise
                if (target != null) {
                    val aMod = target.mod.align?.let { Modifier.align(parseBoxAlign(it)) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, aMod)
                }
            }
            is LogicSpec.ForEach -> {
                val list = data.lists[logic.data] ?: emptyList()
                list.forEach { item ->
                    val cs = scope.childItem(logic.itemAlias, item)
                    val aMod = logic.template.mod.align?.let { Modifier.align(parseBoxAlign(it)) } ?: Modifier
                    RenderNode(logic.template, cs, data, colorExtractor, aMod)
                }
            }
            is LogicSpec.When -> {
                val v = evalExpression(logic.value, scope)
                val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                    ?: logic.default
                if (target != null) {
                    val aMod = target.mod.align?.let { Modifier.align(parseBoxAlign(it)) } ?: Modifier
                    RenderNode(target, scope, data, colorExtractor, aMod)
                }
            }
        }
    }
}

/** Non-scoped child iteration (used inside TABS, GRID rows, etc.). */
@Composable
private fun renderNodes(
    children: List<SpecNode>,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?
) {
    children.forEach { child -> RenderNode(child, scope, data, colorExtractor, Modifier) }
}

/** Lazy list iteration — wraps each child in `item {}` (or `items(list) {}` for ForEach). */
private fun LazyListScope.renderLazyNodes(
    children: List<SpecNode>,
    scope: Scope,
    data: ScreenData,
    colorExtractor: ColorExtractor?
) {
    children.forEach { child ->
        when (val logic = child.logic) {
            null -> item { RenderNode(child, scope, data, colorExtractor, Modifier) }
            is LogicSpec.If -> {
                val cond = evalExpression(logic.condition, scope)
                val target = if (toBool(cond)) logic.then else logic.otherwise
                if (target != null) {
                    item { RenderNode(target, scope, data, colorExtractor, Modifier) }
                }
            }
            is LogicSpec.ForEach -> {
                val list = data.lists[logic.data] ?: emptyList()
                items(list) { item ->
                    val cs = scope.childItem(logic.itemAlias, item)
                    RenderNode(logic.template, cs, data, colorExtractor, Modifier)
                }
            }
            is LogicSpec.When -> {
                val v = evalExpression(logic.value, scope)
                val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                    ?: logic.default
                if (target != null) {
                    item { RenderNode(target, scope, data, colorExtractor, Modifier) }
                }
            }
        }
    }
}

// ─── Container renderers ─────────────────────────────────────────────────────

@Composable
private fun renderColumn(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    Column(modifier = m) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderRow(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    Row(modifier = m) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderBox(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    val align = node.mod.align?.let { parseBoxAlign(it) }
    Box(modifier = m, contentAlignment = align ?: Alignment.TopStart) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderGrid(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    val columns = 3 // SpecNode has no props field; default to 3.
    val flat = collectRenderItems(node.children, scope, data)
    val rows = flat.chunked(columns)
    Column(modifier = m) {
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        RenderNode(item.node, item.scope, data, colorExtractor, Modifier)
                    }
                }
                // Pad incomplete row so cells keep equal width.
                if (rowItems.size < columns) {
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun renderFlow(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    FlowRow(modifier = m) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderScrollColumn(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    val state = rememberScrollState()
    Column(modifier = m.verticalScroll(state)) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderScrollRow(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    val state = rememberScrollState()
    Row(modifier = m.horizontalScroll(state)) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderLazyColumn(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    LazyColumn(modifier = m) {
        renderLazyNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderLazyRow(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    LazyRow(modifier = m) {
        renderLazyNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderTabs(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    var selected by remember { mutableStateOf(0) }
    val tabs = node.children
    Column(modifier = m) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            tabs.forEachIndexed { i, tab ->
                val label = (tab.content as? ContentSpec.TextContent)?.text
                    ?.let { resolveTemplate(it, scope) }
                    ?: "Tab ${i + 1}"
                Chip(
                    text = label,
                    selected = i == selected,
                    onClick = { selected = i }
                )
            }
        }
        if (selected in tabs.indices) {
            RenderNode(tabs[selected], scope, data, colorExtractor, Modifier)
        }
    }
}

// ─── Content renderers ───────────────────────────────────────────────────────

@Composable
private fun renderText(node: SpecNode, scope: Scope, m: Modifier) {
    val content = node.content as? ContentSpec.TextContent
    if (content == null) { renderPlaceholder(node, m); return }
    val text = resolveTemplate(content.text, scope)
    val style = resolveTextStyle(content.style).copy(
        textAlign = content.align?.let(::parseTextAlign)
    )
    val color = content.color?.let { parseHex(it) } ?: LocalColors.current.text
    val maxLines = content.maxLines ?: Int.MAX_VALUE
    DesignText(
        text = text,
        modifier = m,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = if (content.maxLines != null) TextOverflow.Ellipsis else TextOverflow.Clip
    )
}

@Composable
private fun renderImage(node: SpecNode, scope: Scope, m: Modifier) {
    val content = node.content as? ContentSpec.ImageContent
    if (content == null) { renderPlaceholder(node, m); return }
    val url = resolveBindingString(content.url, scope)
    val shape = content.shape.toShape(RectangleShape)
    RemoteImage(
        url = url.takeIf { it.isNotBlank() },
        modifier = m,
        shape = shape
    )
}

@Composable
private fun renderButton(
    node: SpecNode, scope: Scope, data: ScreenData, m: Modifier
) {
    val content = node.content as? ContentSpec.ButtonContent
    if (content == null) { renderPlaceholder(node, m); return }
    val label = resolveTemplate(content.label, scope)
    PillButton(
        text = label,
        onClick = { resolveAction(content.action, content.actionParam, data.callbacks, scope) },
        modifier = m,
        primary = content.primary
    )
}

@Composable
private fun renderCard(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    // Auto-wire onAnimeClick when rendered inside a forEach item with an Int id.
    val onClick: (() -> Unit)? = (scope.currentItem?.get("id") as? Int)?.let { id ->
        { data.callbacks.onAnimeClick(id) }
    }
    val finalMod = if (onClick != null) {
        m.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else m
    Box(modifier = finalMod) {
        Column(modifier = Modifier) {
            renderNodes(node.children, scope, data, colorExtractor)
        }
    }
}

@Composable
private fun renderChip(node: SpecNode, scope: Scope, data: ScreenData, m: Modifier) {
    val textContent = node.content as? ContentSpec.TextContent
    val label = textContent?.let { resolveTemplate(it.text, scope) } ?: ""
    // Wire onAnimeClick (Int id) or onCategorySelect (Long id) when available.
    val onClick: (() -> Unit)? = scope.currentItem?.get("id")?.let { id ->
        when (id) {
            is Int -> { { data.callbacks.onAnimeClick(id) } }
            is Long -> { { data.callbacks.onCategorySelect(id) } }
            else -> null
        }
    }
    Chip(text = label, selected = false, onClick = onClick, modifier = m)
}

@Composable
private fun renderBadge(node: SpecNode, scope: Scope, m: Modifier) {
    val content = node.content as? ContentSpec.BadgeContent
    if (content == null) { renderPlaceholder(node, m); return }
    val text = resolveTemplate(content.text, scope)
    val bg = parseHex(content.color)
    Box(
        modifier = m.background(bg, RoundedCornerShape(Radius.pill)),
        contentAlignment = Alignment.Center
    ) {
        DesignText(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = LocalTypography.current.micro,
            color = Color.White
        )
    }
}

@Composable
private fun renderSpacer(node: SpecNode, m: Modifier) {
    Spacer(modifier = m)
}

@Composable
private fun renderDivider(node: SpecNode, m: Modifier) {
    val hasHeight = node.mod.height != null
    val finalMod = if (hasHeight) m else m.height(1.dp).fillMaxWidth()
    Box(modifier = finalMod.background(LocalColors.current.outline))
}

@Composable
private fun renderSurface(
    node: SpecNode, scope: Scope, data: ScreenData, colorExtractor: ColorExtractor?, m: Modifier
) {
    val shape = node.mod.shape.toShape(RectangleShape)
    val finalMod = if (node.mod.background == null) m.background(LocalColors.current.surface, shape)
                   else m
    Box(modifier = finalMod) {
        renderNodes(node.children, scope, data, colorExtractor)
    }
}

@Composable
private fun renderCanvas(node: SpecNode, m: Modifier) {
    // Optional — render an empty Box for now.
    Box(modifier = m)
}

@Composable
private fun renderIcon(node: SpecNode, scope: Scope, m: Modifier) {
    val content = node.content as? ContentSpec.IconContent
    if (content == null) { renderPlaceholder(node, m); return }
    val tint = content.tint?.let { parseHex(it) } ?: LocalColors.current.text
    val sizePx = content.size
    Box(modifier = m.size(sizePx.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(sizePx.dp)) {
            drawIcon(content.name, tint, sizePx)
        }
    }
}

@Composable
private fun renderPlaceholder(node: SpecNode, m: Modifier) {
    Box(
        modifier = m
            .background(LocalColors.current.surfaceHi, RoundedCornerShape(Radius.sm))
            .padding(Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        DesignText(
            text = "?${node.type.name}",
            style = LocalTypography.current.micro,
            color = LocalColors.current.textMuted
        )
    }
}

// ─── Container helper: flatten children for GRID ─────────────────────────────

private data class RenderItem(val node: SpecNode, val scope: Scope)

private fun collectRenderItems(
    children: List<SpecNode>, scope: Scope, data: ScreenData
): List<RenderItem> {
    val out = mutableListOf<RenderItem>()
    children.forEach { child ->
        when (val logic = child.logic) {
            null -> out.add(RenderItem(child, scope))
            is LogicSpec.If -> {
                val target = if (toBool(evalExpression(logic.condition, scope))) logic.then
                              else logic.otherwise
                if (target != null) out.add(RenderItem(target, scope))
            }
            is LogicSpec.ForEach -> {
                val list = data.lists[logic.data] ?: emptyList()
                list.forEach { item ->
                    out.add(RenderItem(logic.template, scope.childItem(logic.itemAlias, item)))
                }
            }
            is LogicSpec.When -> {
                val v = evalExpression(logic.value, scope)
                val target = logic.branches.firstOrNull { it.matches == v?.toString() }?.node
                    ?: logic.default
                if (target != null) out.add(RenderItem(target, scope))
            }
        }
    }
    return out
}

// ─── Expression evaluation ───────────────────────────────────────────────────

/**
 * Evaluate [expr] against [scope]. Returns String/Boolean/Number/DataItem or null.
 * Never throws — returns null/false on resolution failure.
 */
fun evalExpression(expr: Expression, scope: Scope): Any? = when (expr) {
    is Expression.Literal -> expr.value
    is Expression.Field -> resolveField(expr.path, scope)
    is Expression.Equals -> {
        val l = evalExpression(expr.left, scope)
        val r = evalExpression(expr.right, scope)
        l?.toString() == r?.toString()
    }
    is Expression.NotEquals -> {
        val l = evalExpression(expr.left, scope)
        val r = evalExpression(expr.right, scope)
        l?.toString() != r?.toString()
    }
    is Expression.GreaterThan -> {
        val l = evalExpression(expr.left, scope)?.toString()?.toFloatOrNull()
        val r = evalExpression(expr.right, scope)?.toString()?.toFloatOrNull()
        if (l != null && r != null) l > r else false
    }
    is Expression.LessThan -> {
        val l = evalExpression(expr.left, scope)?.toString()?.toFloatOrNull()
        val r = evalExpression(expr.right, scope)?.toString()?.toFloatOrNull()
        if (l != null && r != null) l < r else false
    }
    is Expression.And ->
        toBool(evalExpression(expr.left, scope)) && toBool(evalExpression(expr.right, scope))
    is Expression.Or ->
        toBool(evalExpression(expr.left, scope)) || toBool(evalExpression(expr.right, scope))
    is Expression.Not -> !toBool(evalExpression(expr.value, scope))
    is Expression.IsEmpty -> isEmpty(evalExpression(expr.value, scope))
    is Expression.IsNotEmpty -> !isEmpty(evalExpression(expr.value, scope))
}

private fun toBool(v: Any?): Boolean = when (v) {
    null -> false
    is Boolean -> v
    is Number -> v.toDouble() != 0.0
    is String -> v.equals("true", ignoreCase = true) ||
                 (v.isNotEmpty() && v != "0" && !v.equals("false", ignoreCase = true))
    is Collection<*> -> v.isNotEmpty()
    is DataItem -> true
    else -> true
}

private fun isEmpty(v: Any?): Boolean = when (v) {
    null -> true
    is Collection<*> -> v.isEmpty()
    is Array<*> -> v.isEmpty()
    is String -> v.isEmpty()
    is Number -> v.toDouble() == 0.0
    is Boolean -> !v
    else -> false
}

/** Resolve a `field("path")` reference against the current scope. Supports
 *  bare names (looked up on the current item, then bindings) and dotted paths
 *  `alias.field` (looked up on the named binding). */
fun resolveField(path: String, scope: Scope): Any? {
    return if (path.contains(".")) {
        val parts = path.split(".", limit = 2)
        val rootName = parts[0]
        val fieldPath = parts[1]
        (scope.bindings[rootName] as? DataItem)?.get(fieldPath)
    } else {
        scope.currentItem?.get(path) ?: scope.bindings[path]
    }
}

// ─── Data binding on SpecNode ─────────────────────────────────────────────────

private fun Scope.withDataBinding(spec: DataBindingSpec?, data: ScreenData): Scope {
    if (spec == null) return this
    return when (spec) {
        is DataBindingSpec.Source -> {
            val item = data.single[spec.key]
            if (item != null) childItem(spec.key, item) else this
        }
        is DataBindingSpec.Field -> {
            val value = resolveField(spec.path, this)
            if (value is DataItem) withItem(value) else this
        }
    }
}

// ─── Template & data-binding string resolution ───────────────────────────────

private val TEMPLATE_REGEX = Regex("\\{\\{\\s*(.*?)\\s*}}")
private val FIELD_CALL_REGEX = Regex("""field\(\s*['"]([^'"]+)['"]\s*\)""")

/**
 * Replace `{{...}}` substitutions in [template] using [scope]. Supports both
 * bare field names (`{{title}}`) and explicit field calls (`{{field('title')}}`).
 */
fun resolveTemplate(template: String, scope: Scope): String {
    if (!template.contains("{{")) return template
    return TEMPLATE_REGEX.replace(template) { mr ->
        val inner = mr.groupValues[1].trim()
        val fieldName = FIELD_CALL_REGEX.find(inner)?.groupValues?.get(1) ?: inner
        val value = resolveField(fieldName, scope)
        value?.toString() ?: ""
    }
}

/**
 * Resolve a data-bound string. Supports:
 *   - `@field:path`        → resolveField(path, scope)?.toString()
 *   - `{{...}}` templates  → resolveTemplate(string, scope)
 *   - plain strings        → returned as-is
 */
fun resolveBindingString(s: String, scope: Scope): String {
    return when {
        s.startsWith("@field:") -> {
            val path = s.removePrefix("@field:")
            resolveField(path, scope)?.toString() ?: ""
        }
        s.contains("{{") -> resolveTemplate(s, scope)
        else -> s
    }
}

// ─── Color parsing ───────────────────────────────────────────────────────────

/**
 * Parse a hex color string. Supports #RGB, #RRGGBB, #AARRGGBB. Also resolves
 * theme color names: accent/text/textMuted/surface/surfaceHi/bg/outline/danger/
 * success/scrim/accentText. Blank/null/unparseable → theme text color.
 */
@Composable
fun parseHex(hex: String?): Color {
    val fallback = LocalColors.current.text
    if (hex.isNullOrBlank()) return fallback
    val themed = resolveThemeColorOrNull(hex)
    if (themed != null) return themed
    val cleaned = hex.removePrefix("#")
    return try {
        when (cleaned.length) {
            3 -> Color(
                r = (cleaned[0].digitToInt(16) * 17) / 255f,
                g = (cleaned[1].digitToInt(16) * 17) / 255f,
                b = (cleaned[2].digitToInt(16) * 17) / 255f
            )
            6 -> Color(cleaned.toLong(16).toInt() or 0xFF000000.toInt())
            8 -> Color(cleaned.toLong(16).toInt())
            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}

/** Theme-color-name resolver. Returns null if [name] is not a known theme key. */
@Composable
private fun resolveThemeColorOrNull(name: String): Color? {
    if (name.startsWith("#")) return null
    val c = LocalColors.current
    return when (name.lowercase()) {
        "accent", "accentcolor" -> c.accent
        "accenttext" -> c.accentText
        "text" -> c.text
        "textmuted", "muted" -> c.textMuted
        "surface" -> c.surface
        "surfacehi" -> c.surfaceHi
        "bg", "background" -> c.bg
        "outline" -> c.outline
        "danger" -> c.danger
        "success" -> c.success
        "scrim" -> c.scrim
        else -> null
    }
}

// ─── Shape resolution ────────────────────────────────────────────────────────

fun ShapeSpec?.toShape(default: Shape = RectangleShape): Shape = when (this) {
    null -> default
    is ShapeSpec.Rounded -> RoundedCornerShape(this.radius.dp)
    is ShapeSpec.Pill -> CircleShape
    is ShapeSpec.Rectangle -> RectangleShape
    is ShapeSpec.PerCorner -> RoundedCornerShape(
        topStart = this.topLeft.dp,
        topEnd = this.topRight.dp,
        bottomEnd = this.bottomRight.dp,
        bottomStart = this.bottomLeft.dp
    )
}

// ─── ModifierSpec → Modifier ─────────────────────────────────────────────────

/**
 * Convert this [ModifierSpec] to a Compose [Modifier]. Resolves ALL fields:
 *   - Size: width/height (Fixed/Wrap/Fill/Fraction), fillMaxWidth/Height/Size
 *   - Spacing: padding (inner), margin (outer)
 *   - Decoration: background (solid/gradient/glass/extracted), border, shadow
 *   - Shape: clip
 *   - Transform: alpha/blur/scale/rotation/aspectRatio/offset/zIndex
 *
 * Shape is resolved first (used by background/border/shadow/clip). The
 * [Extracted] background uses [produceState] to load a palette color
 * asynchronously via [colorExtractor].
 */
@Composable
fun ModifierSpec.toModifier(
    defaultShape: Shape = RectangleShape,
    scope: Scope = Scope(),
    colorExtractor: ColorExtractor? = null
): Modifier {
    val shape = this.shape.toShape(defaultShape)
    val m = buildBaseModifier(shape, scope, colorExtractor)
    return m
}

@Composable
private fun ModifierSpec.buildBaseModifier(
    shape: Shape,
    scope: Scope,
    colorExtractor: ColorExtractor?
): Modifier {
    var m: Modifier = Modifier

    // 1) Margin (outer spacing — applied first so it sits outside bg/border)
    margin?.let { m = m.padding(it.toPaddingValues()) }

    // 2) Size
    width?.let { m = m.then(it.toWidthModifier()) }
    height?.let { m = m.then(it.toHeightModifier()) }
    if (fillMaxWidth == true) m = m.fillMaxWidth()
    if (fillMaxHeight == true) m = m.fillMaxHeight()
    if (fillMaxSize == true) m = m.fillMaxSize()

    // 3) Shadow (drawn behind the bg)
    shadow?.let { s ->
        m = m.shadow(
            elevation = (s.blur / 4f).coerceAtLeast(1f).dp,
            shape = shape,
            clip = false
        )
    }

    // 4) Background (solid / gradient / glass / extracted)
    background?.let { bg ->
        m = m.then(bg.toBackgroundModifier(shape, scope, colorExtractor))
    }

    // 5) Border
    border?.let { b ->
        if (b is BorderSpec.Uniform) {
            m = m.border(b.width.dp, parseHex(b.color), shape)
        }
        // BorderSpec.None → no-op
    }

    // 6) Clip to shape (only if shape isn't already a Rectangle or clip==true)
    if (clip == true) m = m.clip(shape)

    // 7) Padding (inner spacing — between bg and content)
    padding?.let { m = m.padding(it.toPaddingValues()) }

    // 8) Transforms
    alpha?.let { m = m.alpha(it) }
    blur?.let { b ->
        // Modifier.blur handles the API 31+ check internally and no-ops below.
        m = m.blur(b.dp)
    }
    scale?.let { m = m.scale(it, it) }
    rotation?.let { m = m.rotate(it) }
    aspectRatio?.let { m = m.aspectRatio(it) }
    offset?.let { m = m.offset(it.x.dp, it.y.dp) }
    zIndex?.let { m = m.zIndex(it) }

    return m
}

private fun SizeSpec.toWidthModifier(): Modifier = when (this) {
    is SizeSpec.Fixed -> Modifier.width(dp.dp)
    is SizeSpec.Wrap -> Modifier
    is SizeSpec.Fill -> Modifier.fillMaxWidth()
    is SizeSpec.Fraction -> Modifier.fillMaxWidth(value.coerceIn(0f, 1f))
}

private fun SizeSpec.toHeightModifier(): Modifier = when (this) {
    is SizeSpec.Fixed -> Modifier.height(dp.dp)
    is SizeSpec.Wrap -> Modifier
    is SizeSpec.Fill -> Modifier.fillMaxHeight()
    is SizeSpec.Fraction -> Modifier.fillMaxHeight(value.coerceIn(0f, 1f))
}

/** Resolve EdgeInsetsSpec to a Compose [PaddingValues] (additive: all > h/v > per-side). */
fun EdgeInsetsSpec.toPaddingValues(): PaddingValues {
    val a = all
    if (a != null) return PaddingValues(a.dp)
    val h = horizontal ?: 0f
    val v = vertical ?: 0f
    return PaddingValues(
        start = (start + h).dp,
        top = (top + v).dp,
        end = (end + h).dp,
        bottom = (bottom + v).dp
    )
}

@Composable
private fun BackgroundSpec.toBackgroundModifier(
    shape: Shape,
    scope: Scope,
    colorExtractor: ColorExtractor?
): Modifier = when (this) {
    is BackgroundSpec.Solid -> Modifier.background(parseHex(color), shape)

    is BackgroundSpec.Gradient -> {
        // Pre-compute color stops outside drawBehind (parseHex is @Composable).
        val parsedStops: List<Pair<Float, Color>> = stops.map { it.position to parseHex(it.color) }
        val parsedColors: List<Color> = parsedStops.map { it.second }
        Modifier.drawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@drawBehind
            when (type) {
                "radial" -> {
                    val radius = min(w, h) / 2f
                    val center = Offset(w / 2f, h / 2f)
                    val brush = if (parsedStops.size >= 2) {
                        Brush.radialGradient(
                            *parsedStops.toTypedArray(),
                            center = center,
                            radius = radius
                        )
                    } else {
                        Brush.radialGradient(
                            colors = parsedColors,
                            center = center,
                            radius = radius
                        )
                    }
                    drawRect(brush)
                }
                else -> { // linear
                    val rad = angle * PI.toFloat() / 180f
                    val cosA = cos(rad)
                    val sinA = sin(rad)
                    val cx = w / 2f
                    val cy = h / 2f
                    val start = Offset(cx - (w / 2f) * cosA, cy - (h / 2f) * sinA)
                    val end = Offset(cx + (w / 2f) * cosA, cy + (h / 2f) * sinA)
                    val brush = if (parsedStops.isNotEmpty()) {
                        Brush.linearGradient(
                            *parsedStops.toTypedArray(),
                            start = start,
                            end = end
                        )
                    } else {
                        Brush.linearGradient(
                            colors = parsedColors,
                            start = start,
                            end = end
                        )
                    }
                    drawRect(brush)
                }
            }
        }
    }

    is BackgroundSpec.Glass -> {
        val tintColor = parseHex(tint).copy(alpha = tintAlpha.coerceIn(0f, 1f))
        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(
                    blur, blur, Shader.TileMode.CLAMP
                )
            }
        } else Modifier
        blurMod.background(tintColor, shape)
    }

    is BackgroundSpec.Extracted -> {
        val url = resolveBindingString(imageUrl, scope)
        val fallback = parseHex(fallback)
        val state = produceState(initialValue = fallback, url, variant) {
            if (colorExtractor != null && url.isNotBlank()) {
                value = colorExtractor.extract(url, variant, fallback)
            }
        }
        Modifier.background(state.value, shape)
    }
}

// ─── Action resolution ───────────────────────────────────────────────────────

fun resolveAction(
    action: String?,
    param: String?,
    callbacks: SpecCallbacks,
    @Suppress("UNUSED_PARAMETER") scope: Scope
) {
    when (action) {
        "back" -> callbacks.onBack()
        "search" -> callbacks.onSearch()
        "save" -> callbacks.onSave()
        "refresh" -> callbacks.onRefresh()
        "navigate" -> callbacks.onNavigate(param ?: "")
        "categorySelect" -> {
            // Optional: param may carry a category id.
            param?.toLongOrNull()?.let(callbacks.onCategorySelect)
        }
        null, "" -> { /* no-op */ }
        else -> { /* unknown — no-op, never crash */ }
    }
}

// ─── Text style & alignment helpers ──────────────────────────────────────────

@Composable
private fun resolveTextStyle(style: String): androidx.compose.ui.text.TextStyle {
    val t = LocalTypography.current
    return when (style) {
        "display" -> t.display
        "title1" -> t.title1
        "title2" -> t.title2
        "title3" -> t.title3
        "body" -> t.body
        "bodyEmphasis" -> t.bodyEmphasis
        "caption" -> t.caption
        "micro" -> t.micro
        else -> t.body
    }
}

private fun parseTextAlign(s: String): TextAlign = when (s.lowercase()) {
    "center" -> TextAlign.Center
    "end", "right" -> TextAlign.End
    "justify" -> TextAlign.Justify
    else -> TextAlign.Start
}

private fun parseBoxAlign(s: String): Alignment = when (s.lowercase()) {
    "center" -> Alignment.Center
    "topstart", "topleft" -> Alignment.TopStart
    "topend", "topright" -> Alignment.TopEnd
    "bottomstart", "bottomleft" -> Alignment.BottomStart
    "bottomend", "bottomright" -> Alignment.BottomEnd
    "centerstart", "centerleft", "start", "left" -> Alignment.CenterStart
    "centerend", "centerright", "end", "right" -> Alignment.CenterEnd
    "top", "topcenter" -> Alignment.TopCenter
    "bottom", "bottomcenter" -> Alignment.BottomCenter
    else -> Alignment.TopStart
}

// ─── Icon Canvas drawings ────────────────────────────────────────────────────

private fun DrawScope.drawIcon(name: String, color: Color, size: Float) {
    val stroke = size / 12f
    when (name.lowercase()) {
        "search" -> {
            drawCircle(
                color = color,
                style = Stroke(width = stroke),
                radius = size * 0.32f,
                center = Offset(size * 0.4f, size * 0.4f)
            )
            drawLine(
                color = color,
                start = Offset(size * 0.62f, size * 0.62f),
                end = Offset(size * 0.88f, size * 0.88f),
                strokeWidth = stroke
            )
        }
        "back" -> {
            drawLine(color, Offset(size * 0.7f, size * 0.2f), Offset(size * 0.3f, size * 0.5f), stroke)
            drawLine(color, Offset(size * 0.3f, size * 0.5f), Offset(size * 0.7f, size * 0.8f), stroke)
        }
        "close" -> {
            drawLine(color, Offset(size * 0.25f, size * 0.25f), Offset(size * 0.75f, size * 0.75f), stroke)
            drawLine(color, Offset(size * 0.75f, size * 0.25f), Offset(size * 0.25f, size * 0.75f), stroke)
        }
        "plus" -> {
            drawLine(color, Offset(size * 0.5f, size * 0.2f), Offset(size * 0.5f, size * 0.8f), stroke)
            drawLine(color, Offset(size * 0.2f, size * 0.5f), Offset(size * 0.8f, size * 0.5f), stroke)
        }
        "play" -> {
            drawPath(
                color = color,
                path = Path().apply {
                    moveTo(size * 0.3f, size * 0.2f)
                    lineTo(size * 0.8f, size * 0.5f)
                    lineTo(size * 0.3f, size * 0.8f)
                    close()
                }
            )
        }
        "star" -> {
            drawPath(
                color = color,
                path = starPath(size)
            )
        }
        "refresh" -> {
            // Circular arrow — approximated by an arc + a small arrowhead.
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke),
                topLeft = Offset(size * 0.2f, size * 0.2f),
                size = androidx.compose.ui.geometry.Size(size * 0.6f, size * 0.6f)
            )
            drawLine(color, Offset(size * 0.8f, size * 0.2f), Offset(size * 0.8f, size * 0.4f), stroke)
            drawLine(color, Offset(size * 0.8f, size * 0.2f), Offset(size * 0.6f, size * 0.2f), stroke)
        }
        "save" -> {
            // Bookmark outline.
            drawPath(
                color = color,
                style = Stroke(width = stroke),
                path = Path().apply {
                    moveTo(size * 0.25f, size * 0.2f)
                    lineTo(size * 0.75f, size * 0.2f)
                    lineTo(size * 0.75f, size * 0.8f)
                    lineTo(size * 0.5f, size * 0.6f)
                    lineTo(size * 0.25f, size * 0.8f)
                    close()
                }
            )
        }
        "settings" -> {
            // Gear: outer circle + inner circle.
            drawCircle(color, style = Stroke(width = stroke), radius = size * 0.36f,
                center = Offset(size * 0.5f, size * 0.5f))
            drawCircle(color, radius = size * 0.12f, center = Offset(size * 0.5f, size * 0.5f))
        }
        "home" -> {
            drawPath(
                color = color,
                style = Stroke(width = stroke),
                path = Path().apply {
                    moveTo(size * 0.5f, size * 0.15f)
                    lineTo(size * 0.85f, size * 0.45f)
                    lineTo(size * 0.85f, size * 0.85f)
                    lineTo(size * 0.6f, size * 0.85f)
                    lineTo(size * 0.6f, size * 0.6f)
                    lineTo(size * 0.4f, size * 0.6f)
                    lineTo(size * 0.4f, size * 0.85f)
                    lineTo(size * 0.15f, size * 0.85f)
                    lineTo(size * 0.15f, size * 0.45f)
                    close()
                }
            )
        }
        "library" -> {
            // Three vertical bars.
            drawLine(color, Offset(size * 0.25f, size * 0.2f), Offset(size * 0.25f, size * 0.8f), stroke)
            drawLine(color, Offset(size * 0.5f, size * 0.2f), Offset(size * 0.5f, size * 0.8f), stroke)
            drawLine(color, Offset(size * 0.75f, size * 0.2f), Offset(size * 0.75f, size * 0.8f), stroke)
        }
        else -> {
            // Unknown — draw a small filled circle as a fallback glyph.
            drawCircle(color, radius = size * 0.25f, center = Offset(size * 0.5f, size * 0.5f))
        }
    }
}

private fun starPath(size: Float): Path = Path().apply {
    val cx = size * 0.5f
    val cy = size * 0.5f
    val outer = size * 0.4f
    val inner = outer * 0.45f
    var angle = -PI.toFloat() / 2f
    val step = PI.toFloat() / 5f
    moveTo(cx + outer * cos(angle), cy + outer * sin(angle))
    for (i in 1 until 10) {
        angle += step
        val r = if (i % 2 == 1) inner else outer
        lineTo(cx + r * cos(angle), cy + r * sin(angle))
    }
    close()
}
