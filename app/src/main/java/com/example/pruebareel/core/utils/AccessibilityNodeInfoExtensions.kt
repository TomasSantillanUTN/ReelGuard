package com.example.pruebareel.core.utils

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
