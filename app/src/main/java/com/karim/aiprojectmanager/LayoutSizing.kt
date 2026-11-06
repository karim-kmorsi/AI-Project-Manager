package com.karim.aiprojectmanager

/** Pixel measurements come from the actual themed text/buttons, not font-scale breakpoints. */
internal fun toolbarActionsNeedOwnRow(width: Int, navigation: Int, title: Int, actions: Int, gap: Int): Boolean {
    require(listOf(width, navigation, title, actions, gap).all { it >= 0 })
    if (actions == 0) return false
    val gaps = if (navigation == 0) 1 else 2
    return navigation.toLong() + title + actions + gap.toLong() * gaps > width
}

/** A compact viewport scrolls the chrome instead of squeezing the input below readable text. */
internal fun documentFieldHeight(viewport: Int, chrome: Int, gap: Int, readableMinimum: Int): Int {
    require(viewport >= 0 && chrome >= 0 && gap >= 0 && readableMinimum > 0)
    return maxOf(readableMinimum.toLong(), viewport.toLong() - chrome - gap).toInt()
}
