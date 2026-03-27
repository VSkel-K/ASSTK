package com.example.asstk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controlador para el reconocimiento de voz, gestionando el ciclo de vida del SpeechRecognizer
 * y publicando los resultados en el RobotBus.
 */
class SpeechController(
    private val context: Context
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechController", "onReadyForSpeech")
            isListening = true
            controllerScope.launch { RobotBus.postAssistantReply(AssistantReply(bubbleText = "Escuchando...", shouldSpeak = false)) }
        }

        override fun onBeginningOfSpeech() {
            Log.d("SpeechController", "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Opcional: Usar rmsdB para feedback visual de volumen
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // No se usa para este caso
        }

        override fun onEndOfSpeech() {
            Log.d("SpeechController", "onEndOfSpeech")
            isListening = false
            // El OverlayService ya mostrará "Pensando..." cuando reciba la query
        }

        override fun onError(error: Int) {
            Log.e("SpeechController", "Error en reconocimiento de voz: $error")
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
                SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                else -> "Error desconocido"
            }
            controllerScope.launch { RobotBus.postAssistantReply(AssistantReply(bubbleText = "Error de voz: $errorMessage", shouldSpeak = false)) }
            destroyRecognizer()
        }

        override fun onResults(results: Bundle?) {
            Log.d("SpeechController", "onResults")
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull() ?: ""

            if (spokenText.isNotBlank()) {
                controllerScope.launch { RobotBus.postUserQuery(spokenText) }
            } else {
                controllerScope.launch { RobotBus.postAssistantReply(AssistantReply(bubbleText = "No te entendí. Intenta de nuevo.", shouldSpeak = false)) }
            }
            destroyRecognizer()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Opcional: Mostrar resultados parciales en la burbuja
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull() ?: ""
            if (partialText.isNotBlank()) {
                // Actualizar la burbuja con el texto parcial, pero sin cambiar el estado del robot
                controllerScope.launch { RobotBus.postAssistantReply(AssistantReply(bubbleText = partialText, shouldSpeak = false)) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // No se usa para este caso
        }
    }

    /**
     * Inicia el proceso de escucha de voz.
     */
    fun startListening() {
        if (isListening) {
            Log.d("SpeechController", "Ya está escuchando, ignorando startListening.")
            return
        }

        destroyRecognizer() // Asegurarse de que no haya un reconocedor anterior activo

        // Priorizar reconocimiento on-device si está disponible
        speechRecognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            Log.d("SpeechController", "Usando reconocimiento de voz on-device.")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d("SpeechController", "Usando reconocimiento de voz en la nube.")
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            Log.e("SpeechController", "Reconocimiento de voz no disponible en este dispositivo.")
            controllerScope.launch { RobotBus.postAssistantReply(AssistantReply(bubbleText = "Reconocimiento de voz no disponible.", shouldSpeak = false)) }
            return
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Habilitar resultados parciales
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") // Forzar español
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L) // Duración mínima de habla
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L) // Silencio para finalizar
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L) // Silencio completo para finalizar
        }

        speechRecognizer?.startListening(intent)
        isListening = true
    }

    /**
     * Detiene el proceso de escucha de voz.
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            Log.d("SpeechController", "stopListening llamado.")
        }
    }

    /**
     * Libera los recursos del SpeechRecognizer.
     */
    fun destroy() {
        controllerScope.launch { // Asegurarse de que se ejecuta en el hilo principal si es necesario
            destroyRecognizer()
            Log.d("SpeechController", "SpeechController destruido.")
        }
    }

    private fun destroyRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
