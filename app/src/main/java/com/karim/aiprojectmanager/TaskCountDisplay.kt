package com.karim.aiprojectmanager

/** Display-only LTR isolate: keep numerator / denominator together inside either UI direction. */
internal fun taskCountExpression(verified: Int, total: Int): String = "\u2066$verified / $total\u2069"
