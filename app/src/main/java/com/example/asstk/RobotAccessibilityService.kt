package com.example.asstk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio de Accesibilidad que captura el contexto de la pantalla, filtra información sensible
 * y lo publica en el RobotBus para que otros componentes lo utilicen.
 */
class RobotAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastScreenSignature: String? = null

    // Lista de paquetes de aplicaciones sensibles que no deben ser analizadas
    private val sensitiveAppDenylist = listOf(
        "com.google.android.apps.authenticator2", // Google Authenticator
        "com.android.bank", // Ejemplo de paquete de banca
        "com.example.passwordmanager" // Ejemplo de gestor de contraseñas
        // TODO: Añadir más aplicaciones sensibles aquí
    )

    // Palabras clave o tipos de nodos que indican información sensible
    private val sensitiveNodeKeywords = listOf(
        "password", "contraseña", "otp", "cvv", "iban", "tarjeta", "card", "pin"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Filtrar aplicaciones sensibles
        if (sensitiveAppDenylist.contains(packageName)) {
            Log.w("RobotAccessibilityService", "Aplicación en denylist detectada: $packageName. No se procesará el evento.")
            serviceScope.launch { RobotBus.updateScreenContext(ScreenContext(packageName = packageName, isSensitive = true)) }
            return
        }

        // Escuchar solo eventos relevantes para evitar ruido excesivo
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                serviceScope.launch {
                    processAccessibilityEvent(event, packageName)
                }
            }
            else -> { /* Ignorar otros eventos */ }
        }
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent, packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        val visibleTexts = mutableListOf<String>()
        val actions = mutableListOf<String>()
        var focusedText: String? = null
        var screenTitle: String? = null
        var isSensitiveNodeDetected = false

        // Recorrer el árbol de nodos para extraer información
        traverseNode(rootNode, visibleTexts, actions, { node ->
            // Detectar texto enfocado
            if (node.isFocused) {
                focusedText = node.text?.toString() ?: node.contentDescription?.toString()
            }
            // Detectar nodos sensibles
            if (isNodeSensitive(node)) {
                isSensitiveNodeDetected = true
            }
        })

        // Obtener el título de la pantalla si está disponible
        screenTitle = event.windowTitle?.toString() ?: rootNode.text?.toString()

        // Obtener el nombre de la aplicación
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val screenContext = ScreenContext(
            packageName = packageName,
            appName = appName,
            screenTitle = screenTitle,
            focusedText = focusedText,
            visibleTexts = visibleTexts.distinct().take(20), // Limitar a 20 textos relevantes
            actions = actions.distinct(),
            isSensitive = isSensitiveNodeDetected // Marcar como sensible si se detectó un nodo sensible
        )

        val reducedContext = ReducedContext(
            packageName = screenContext.packageName,
            focusedText = screenContext.focusedText,
            visibleTexts = screenContext.visibleTexts
        )

        val currentSignature = reducedContext.signature()

        // Publicar solo si el contexto ha cambiado o si es la primera vez
        if (currentSignature != lastScreenSignature) {
            serviceScope.launch { RobotBus.updateScreenContext(screenContext) }
            lastScreenSignature = currentSignature
            Log.d("RobotAccessibilityService", "ScreenContext actualizado: $screenContext")
        } else {
            Log.d("RobotAccessibilityService", "Contexto de pantalla sin cambios significativos. No se publica.")
        }
    }

    /**
     * Recorre recursivamente el árbol de nodos de accesibilidad para extraer información.
     */
    private fun traverseNode(node: AccessibilityNodeInfo, visibleTexts: MutableList<String>, actions: MutableList<String>, onNodeProcessed: (AccessibilityNodeInfo) -> Unit) {
        onNodeProcessed(node)

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { visibleTexts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { visibleTexts.add(it) }
        node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { visibleTexts.add(it) }
        node.paneTitle?.toString()?.takeIf { it.isNotBlank() }?.let { visibleTexts.add(it) }

        // Extraer acciones clickables
        if (node.isClickable && node.text != null) {
            actions.add(node.text.toString())
        } else if (node.isClickable && node.contentDescription != null) {
            actions.add(node.contentDescription.toString())
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, visibleTexts, actions, onNodeProcessed)
                child.recycle() // Importante para evitar fugas de memoria
            }
        }
    }

    /**
     * Verifica si un nodo de accesibilidad contiene información sensible.
     */
    private fun isNodeSensitive(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        val hintText = node.hintText?.toString()?.lowercase() ?: ""

        // Verificar si el nodo es un campo de contraseña
        if (node.isPassword) return true

        // Verificar palabras clave sensibles en el texto visible o descripciones
        return sensitiveNodeKeywords.any { keyword ->
            text.contains(keyword) || contentDescription.contains(keyword) || hintText.contains(keyword)
        }
    }

    override fun onInterrupt() {
        Log.d("RobotAccessibilityService", "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_FOCUSED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // Solo para depuración, en producción se debe limitar más
            // packageNames = arrayOf("com.example.app1", "com.example.app2")
        }
        this.serviceInfo = info
        Log.d("RobotAccessibilityService", "Servicio de Accesibilidad conectado")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancelar todas las coroutines del servicio
        Log.d("RobotAccessibilityService", "Servicio de Accesibilidad destruido")
    }
}
