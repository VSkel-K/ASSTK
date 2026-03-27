package com.example.asstk

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Objeto singleton que actúa como un bus de eventos centralizado para la comunicación
 * entre las diferentes capas del asistente. Utiliza Kotlin Flow para una comunicación
 * reactiva y segura.
 */
object RobotBus {

    // StateFlow para el contexto de la pantalla actual. Emite el último ScreenContext conocido.
    private val _screenContext = MutableStateFlow<ScreenContext?>(null)
    val screenContext = _screenContext.asStateFlow()

    // SharedFlow para las consultas del usuario. Emite las preguntas de voz transcritas.
    private val _userQuery = MutableSharedFlow<String>()
    val userQuery = _userQuery.asSharedFlow()

    // SharedFlow para las respuestas del asistente. Emite las AssistantReply generadas por la IA.
    private val _assistantReply = MutableSharedFlow<AssistantReply>()
    val assistantReply = _assistantReply.asSharedFlow()

    /**
     * Actualiza el contexto de la pantalla. Debe ser llamado por el AccessibilityService.
     */
    suspend fun updateScreenContext(context: ScreenContext) {
        _screenContext.emit(context)
    }

    /**
     * Emite una nueva consulta de voz del usuario. Debe ser llamado por el SpeechController.
     */
    suspend fun postUserQuery(query: String) {
        _userQuery.emit(query)
    }

    /**
     * Emite una respuesta del asistente. Debe ser llamado por el procesador de IA.
     */
    suspend fun postAssistantReply(reply: AssistantReply) {
        _assistantReply.emit(reply)
    }
}
