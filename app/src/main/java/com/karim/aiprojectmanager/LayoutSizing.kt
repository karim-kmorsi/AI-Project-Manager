package com.karim.aiprojectmanager

/** Pixel measurements come from the actual themed text/buttons, not font-scale breakpoints. */
internal fun toolbarActionsNeedOwnRow(width: Int, navigation: Int, title: Int, actions: Int, gap: Int): Boolean {
    require(listOf(width, navigation, title, actions, gap).all { it >= 0 })
    if (actions == 0) return false
    val gaps = if (navigation == 0) 1 else 2
    return navigation.toLong() + title + actions + gap.toLong() * gaps > width
}

/** Never give the field a larger viewport than the scroll area that can actually display it. */
internal fun documentFieldHeight(viewport: Int, chrome: Int, gap: Int, readableMinimum: Int): Int {
    require(viewport >= 0 && chrome >= 0 && gap >= 0 && readableMinimum > 0)
    return minOf(viewport.toLong(), maxOf(readableMinimum.toLong(), viewport.toLong() - chrome - gap)).toInt()
}

internal data class DocumentBodyAllocation(
    val sideAction: Boolean,
    val verticalPadding: Int,
    val bodyWidth: Int,
    val bodyHeight: Int
)

/** Preserve normal spacing; reclaim padding, then share a row only when height demands it. */
internal fun documentBodyAllocation(
    width: Int, height: Int, actionWidth: Int, actionHeight: Int,
    gap: Int, padding: Int, minimumLine: Int, minimumBodyHeight: Int = minimumLine
): DocumentBodyAllocation {
    require(listOf(width, height, actionWidth, actionHeight, gap, padding).all { it >= 0 })
    require(minimumLine > 0 && minimumBodyHeight >= minimumLine)
    val remaining = height.toLong() - actionHeight
    // A compact editor may need its complete height for both guidance and an editable line.
    val side = remaining < minimumBodyHeight && width.toLong() - actionWidth - gap > 0
    val vertical = if (!side && remaining - 2L * padding >= minimumLine) padding else 0
    return DocumentBodyAllocation(side, vertical,
        if (side) width - actionWidth - gap else width,
        if (side) height else (remaining - 2L * vertical).coerceAtLeast(0).toInt())
}
