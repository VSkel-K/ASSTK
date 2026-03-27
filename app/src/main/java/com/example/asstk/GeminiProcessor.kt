package com.example.asstk

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Clase encargada de procesar las consultas del usuario y el contexto de la pantalla
 * utilizando el modelo Gemini a través de Firebase AI Logic.
 */
class GeminiProcessor(
    private val apiKey: String // La API Key debe ser gestionada de forma segura (ej. Firebase Remote Config)
) {

    private val processorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentScreenContext: ScreenContext? = null
    private var processingJob: Job? = null

    // Inicializar el modelo Gemini
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", // O el modelo que se considere más adecuado
        apiKey = apiKey
    )

    init {
        observeRobotBus()
    }

    private fun observeRobotBus() {
        processorScope.launch {
            RobotBus.screenContext.collectLatest { context ->
                currentScreenContext = context
                // Opcional: Si se desea análisis proactivo sin query de usuario, se podría activar aquí
                // pero con un debounce muy agresivo y lógica para evitar spam de llamadas.
            }
        }

        processorScope.launch {
            RobotBus.userQuery
                .debounce(300) // Pequeño debounce para evitar procesar queries muy rápidas
                .collectLatest { query ->
                    if (query == "START_LISTENING") {
                        // Esto es una señal del OverlayService, no una query real para Gemini
                        // No hacemos nada aquí, la query real vendrá después de onResults del SpeechController
                        return@collectLatest
                    }
                    processUserQueryWithContext(query, currentScreenContext)
                }
        }
    }

    private fun processUserQueryWithContext(query: String, screenContext: ScreenContext?) {
        processingJob?.cancel() // Cancelar cualquier procesamiento anterior para priorizar la nueva query
        processingJob = processorScope.launch {
            Log.d("GeminiProcessor", "Procesando query: '$query' con contexto de pantalla.")
            RobotBus.postAssistantReply(AssistantReply(bubbleText = "Pensando...", shouldSpeak = false))

            val prompt = buildPrompt(query, screenContext)

            try {
                val response = generativeModel.generateContent(prompt)
                val rawAnswer = response.text ?: ""

                Log.d("GeminiProcessor", "Respuesta cruda de Gemini: $rawAnswer")

                val assistantReply = parseGeminiResponse(rawAnswer)
                RobotBus.postAssistantReply(assistantReply)

            } catch (e: Exception) {
                Log.e("GeminiProcessor", "Error al consultar Gemini: ${e.message}", e)
                RobotBus.postAssistantReply(
                    AssistantReply(
                        bubbleText = "Lo siento, hubo un error al procesar tu solicitud: ${e.message}",
                        shouldSpeak = false
                    )
                )
            }
        }
    }

    private fun buildPrompt(userQuery: String, screenContext: ScreenContext?): String {
        val screenInfo = if (screenContext != null && !screenContext.isSensitive) {
            """
            Contenido actual de la pantalla (texto extraído de la app '${screenContext.appName ?: screenContext.packageName}', título: '${screenContext.screenTitle.orEmpty()}'):
            Textos visibles: ${screenContext.visibleTexts.joinToString("; ")}
            Elemento enfocado: ${screenContext.focusedText.orEmpty()}
            Acciones disponibles: ${screenContext.actions.joinToString(", ")}
            """.trimIndent()
        } else if (screenContext?.isSensitive == true) {
            "Pantalla sensible detectada. El contenido no se ha enviado al modelo para proteger la privacidad del usuario."
        } else {
            "No hay contexto de pantalla disponible o relevante."
        }

        val systemPrompt = """
            Eres un asistente móvil que ayuda al usuario a entender y navegar la pantalla actual de su dispositivo Android.
            Debes responder de forma útil, breve, práctica y no invasiva. Tu objetivo es simplificar la interacción del usuario con la aplicación.
            Si el contexto parece sensible (banca, contraseñas, OTP, pagos, documentos privados), no analices detalles ni repitas datos sensibles.
            Si no hay suficiente contexto para responder, dilo claramente.
            Siempre debes devolver tu respuesta en formato JSON, con los siguientes campos:
            - `bubbleText`: (String) El texto principal a mostrar en la burbuja del robot. Debe ser conciso.
            - `speakText`: (String, opcional) El texto a vocalizar. Si es nulo, se usará `bubbleText`. Puede ser más detallado.
            - `confidence`: (Float) Un valor entre 0.0 y 1.0 que indica la confianza en la respuesta.
            - `shouldSpeak`: (Boolean) `true` si la respuesta debe ser vocalizada, `false` en caso contrario.
            
            Ejemplo de respuesta JSON:
            ```json
            {
              "bubbleText": "Para pegar, mantén presionado el campo de texto y selecciona 'Pegar'.",
              "speakText": "Para pegar el texto, simplemente mantén presionado el campo de texto deseado y luego selecciona la opción 'Pegar' que aparecerá en el menú contextual.",
              "confidence": 0.95,
              "shouldSpeak": true
            }
            ```
            Asegúrate de que la respuesta sea un JSON válido y completo.
        """.trimIndent()

        return """
            $systemPrompt

            $screenInfo

            El usuario pregunta: "$userQuery"
        """.trimIndent()
    }

    private fun parseGeminiResponse(rawResponse: String): AssistantReply {
        // Intentar extraer el JSON de la respuesta cruda (a veces Gemini añade texto antes o después)
        val jsonString = try {
            rawResponse.substringAfter("```json").substringBefore("```").trim()
        } catch (e: Exception) {
            Log.w("GeminiProcessor", "No se pudo extraer JSON de la respuesta de Gemini, intentando parsear la respuesta completa.")
            rawResponse.trim()
        }

        return try {
            Json.decodeFromString<AssistantReply>(jsonString)
        } catch (e: Exception) {
            Log.e("GeminiProcessor", "Error al parsear la respuesta JSON de Gemini: ${e.message}. Respuesta: '$jsonString'", e)
            AssistantReply(
                bubbleText = "Lo siento, no pude entender la respuesta del modelo. Hubo un problema interno.",
                shouldSpeak = false,
                confidence = 0.0f
            )
        }
    }

    fun destroy() {
        processorScope.cancel() // Cancelar todas las coroutines del procesador
        processingJob?.cancel()
        Log.d("GeminiProcessor", "GeminiProcessor destruido.")
    }
}
