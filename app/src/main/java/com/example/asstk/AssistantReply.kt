package com.example.asstk

import kotlinx.serialization.Serializable

/**
 * Representa la respuesta estructurada del asistente de IA.
 * Permite un control granular sobre cómo se presenta y vocaliza la respuesta.
 */
@Serializable
data class AssistantReply(
    val bubbleText: String, // Texto a mostrar en la burbuja del robot
    val speakText: String? = null, // Texto a vocalizar (si es diferente al de la burbuja)
    val confidence: Float = 0.0f, // Nivel de confianza de la respuesta (0.0f a 1.0f)
    val shouldSpeak: Boolean = false // Indica si la respuesta debe ser vocalizada
)
