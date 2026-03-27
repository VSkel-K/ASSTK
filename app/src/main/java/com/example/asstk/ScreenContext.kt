package com.example.asstk

import kotlinx.serialization.Serializable

/**
 * Representa el contexto completo de la pantalla capturado por el AccessibilityService.
 * Contiene información detallada sobre la aplicación actual y los elementos visibles.
 */
@Serializable
data class ScreenContext(
    val packageName: String,
    val appName: String? = null,
    val screenTitle: String? = null,
    val focusedText: String? = null,
    val visibleTexts: List<String> = emptyList(),
    val actions: List<String> = emptyList(), // Ej. "Enviar", "Guardar"
    val isSensitive: Boolean = false,
    val fullTree: String? = null // Opcional: para depuración o análisis más profundo
)

/**
 * Representa un contexto de pantalla reducido, utilizado para generar una firma
 * y para enviar al LLM de forma más eficiente y con foco en la privacidad.
 */
@Serializable
data class ReducedContext(
    val packageName: String,
    val focusedText: String?,
    val visibleTexts: List<String>
) {
    /**
     * Genera una firma única para este contexto reducido, útil para detectar
     * si la pantalla ha cambiado significativamente sin necesidad de re-analizar.
     */
    fun signature(): String = listOf(
        packageName,
        focusedText.orEmpty(),
        visibleTexts.joinToString("|")
    ).joinToString("#").hashCode().toString()
}
