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

/**
 * Como [findAny], pero solo mira lo que está VISIBLE en pantalla. Instagram y TikTok dejan
 * cargadas pantallas que no se ven (otras pestañas, el feed detrás del perfil, etc.); esas
 * ramas se saltean enteras.
 */
fun AccessibilityNodeInfo.findAnyVisible(
    predicate: (AccessibilityNodeInfo) -> Boolean
): Boolean {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.add(this)

    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (!node.isVisibleToUser) continue
        if (predicate(node)) return true

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { stack.add(it) }
        }
    }
    return false
}

/** Primer nodo que cumple [predicate] (o null). */
fun AccessibilityNodeInfo.findFirst(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    findAny { if (predicate(it)) { found = it; true } else false }
    return found
}

/**
 * Busca un nodo cuyo texto o descripción sea uno de [labels] (sin importar mayúsculas) y lo
 * toca. Si el nodo no es "clickeable", sube hasta el primer padre que sí lo sea.
 * Devuelve `true` si pudo tocar algo.
 */
fun AccessibilityNodeInfo.clickByLabel(labels: Set<String>): Boolean {
    val target = findFirst { n ->
        val t = n.text?.toString()?.trim()?.lowercase()
        val d = n.contentDescription?.toString()?.trim()?.lowercase()
        n.isVisibleToUser && (t in labels || d in labels)
    } ?: return false
    var node: AccessibilityNodeInfo? = target
    while (node != null && !node.isClickable) node = node.parent
    return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
}

/** Hay un contenedor desplazable (ViewPager / RecyclerView) que ocupa casi toda la pantalla. */
fun AccessibilityNodeInfo.hasFullScreenPager(
    screenHeight: Int,
    classFilter: (String) -> Boolean = { it.contains("ViewPager") || it.contains("RecyclerView") },
    ratio: Float = 0.8f
): Boolean = findAnyVisible { n ->
    val cls = n.className?.toString() ?: return@findAnyVisible false
    if (!classFilter(cls)) return@findAnyVisible false
    val r = android.graphics.Rect()
    n.getBoundsInScreen(r)
    screenHeight > 0 && r.height() >= screenHeight * ratio
}
