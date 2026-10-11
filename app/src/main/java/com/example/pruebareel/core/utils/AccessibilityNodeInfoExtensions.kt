package com.example.pruebareel.core.utils

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

fun AccessibilityNodeInfo.findAny(
    predicate: (AccessibilityNodeInfo) -> Boolean
): Boolean {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.add(this)

    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (predicate(node)) return true

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { stack.add(it) }
        }
    }
    return false
}

/**
 * Heurística: una hoja de comentarios tiene un campo de texto (EditText) para escribir
 * ("Agrega un comentario…" / "Add a comment…"). Sirve para Instagram y TikTok.
 */
fun AccessibilityNodeInfo.looksLikeCommentsSheet(): Boolean = findAny { node ->
    if (node.className?.toString()?.contains("EditText") != true) return@findAny false
    val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText else null
    listOfNotNull(node.text, hint, node.contentDescription).any {
        val s = it.toString()
        s.contains("comentario", ignoreCase = true) || s.contains("comment", ignoreCase = true)
    }
}
