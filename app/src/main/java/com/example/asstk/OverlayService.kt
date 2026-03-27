package com.example.asstk

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.example.asstk.SpeechController
import com.example.asstk.GeminiProcessor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var robotImage: ImageView
    private lateinit var speechBubble: TextView

    private var tts: TextToSpeech? = null
    private var ttsInitialized = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var assistantReplyJob: Job?
    private lateinit var speechController: SpeechController
    private lateinit var geminiProcessor: GeminiProcessor = null

    // Estados visuales del robot
    enum class RobotState { IDLE, LISTENING, THINKING, SPEAKING }
    private var currentState: RobotState = RobotState.IDLE

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = layoutInflater.inflate(R.layout.layout_floating_robot, null)

        robotImage = floatingView.findViewById(R.id.robot_image)
        speechBubble = floatingView.findViewById(R.id.speech_bubble)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(floatingView, layoutParams)

        initTTS()
        initSpeechController()
        initGeminiProcessor()
        setupRobotInteractions()
        observeAssistantReplies()
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            val result = tts?.setLanguage(java.util.Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("OverlayService", "TTS: Idioma no soportado o faltan datos.")
            }
        } else {
            Log.e("OverlayService", "TTS: Fallo en la inicialización.")
        }
    }

    private fun setupRobotInteractions() {
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0.toFloat()
        var initialTouchY: Float = 0.toFloat()
        var isMoving = false

        robotImage.setOnTouchListener(object : View.OnTouchListener {
            private val CLICK_THRESHOLD = 5
            private val LONG_PRESS_DURATION = 500L
            private var lastDownTime: Long = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = (floatingView.layoutParams as WindowManager.LayoutParams).x
                        initialY = (floatingView.layoutParams as WindowManager.LayoutParams).y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastDownTime = System.currentTimeMillis()
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val upTime = System.currentTimeMillis()
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        if (Math.abs(dx) < CLICK_THRESHOLD && Math.abs(dy) < CLICK_THRESHOLD) {
                            // Es un click
                            if (upTime - lastDownTime < LONG_PRESS_DURATION) {
                                // Toque corto
                                onRobotShortClick()
                            } else {
                                // Pulsación larga (si no se movió mucho)
                                onRobotLongPress()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isMoving = true
                            val layoutParams = floatingView.layoutParams as WindowManager.LayoutParams
                            layoutParams.x = initialX + dx.toInt()
                            layoutParams.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(floatingView, layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onRobotShortClick() {
        Log.d("OverlayService", "Robot: Toque corto")
        // Iniciar el reconocimiento de voz
        speechController.startListening()
        updateRobotState(RobotState.LISTENING)
        // La burbuja de "Escuchando..." se actualizará vía RobotBus.postAssistantReply desde SpeechController
    }

    private fun onRobotLongPress() {
        Log.d("OverlayService", "Robot: Pulsación larga")
        // Mostrar menú de opciones (pausar, ocultar, modo privado, salir)
        // TODO: Implementar un menú flotante o un diálogo para estas opciones
        speechBubble.text = "Menú de opciones (próximamente)"
        speechBubble.visibility = View.VISIBLE
        floatingView.postDelayed({ speechBubble.visibility = View.GONE }, 3000)
    }

    private fun updateRobotState(newState: RobotState) {
        if (currentState == newState) return
        currentState = newState
        // TODO: Actualizar la imagen del robot según el estado (ej. R.drawable.robot_listening)
        Log.d("OverlayService", "Robot State: $newState")
        when (newState) {
            RobotState.IDLE -> robotImage.setImageResource(R.drawable.ic_robot_idle)
            RobotState.LISTENING -> robotImage.setImageResource(R.drawable.ic_robot_listening)
            RobotState.THINKING -> robotImage.setImageResource(R.drawable.ic_robot_thinking)
            RobotState.SPEAKING -> robotImage.setImageResource(R.drawable.ic_robot_speaking)
        }
    }

    private fun initSpeechController() {
        speechController = SpeechController(this)
    }

    private fun initGeminiProcessor() {
        // TODO: Reemplazar "TU_API_KEY" con una API Key real y gestionarla de forma segura.
        // En un entorno de producción, esto debería venir de Firebase Remote Config o un backend.
        geminiProcessor = GeminiProcessor("TU_API_KEY")
    }

    private fun observeAssistantReplies() {
        assistantReplyJob?.cancel()
        assistantReplyJob = serviceScope.launch {
            RobotBus.assistantReply.collectLatest { reply ->
                Log.d("OverlayService", "Respuesta del asistente recibida: ${reply.bubbleText}")
                // Actualizar el estado del robot basado en si va a hablar o si es un mensaje de estado
                if (reply.shouldSpeak) {
                    updateRobotState(RobotState.SPEAKING)
                } else if (reply.bubbleText == "Escuchando...") {
                    updateRobotState(RobotState.LISTENING)
                } else if (reply.bubbleText == "Pensando...") {
                    updateRobotState(RobotState.THINKING)
                } else {
                    updateRobotState(RobotState.IDLE)
                }
                speechBubble.text = reply.bubbleText
                speechBubble.visibility = View.VISIBLE

                if (reply.shouldSpeak && ttsInitialized) {
                    val textToSpeak = reply.speakText ?: reply.bubbleText
                    tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                    // Opcional: escuchar el fin del habla para volver a IDLE
                    // tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    //     override fun onStart(utteranceId: String?) { /* No-op */ }
                    //     override fun onDone(utteranceId: String?) { updateRobotState(RobotState.IDLE) }
                    //     override fun onError(utteranceId: String?) { updateRobotState(RobotState.IDLE) }
                    // })
                } else {
                    // Si no habla, volver a IDLE después de un tiempo
                    floatingView.postDelayed({ updateRobotState(RobotState.IDLE) }, 8000)
                }
                // Ocultar la burbuja después de un tiempo
                floatingView.postDelayed({ speechBubble.visibility = View.GONE }, 8000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancelar todas las coroutines del servicio
        assistantReplyJob?.cancel()
        speechController.destroy()
        geminiProcessor.destroy()
        if (floatingView.windowToken != null) {
            windowManager.removeView(floatingView)
        }
        tts?.stop()
        tts?.shutdown()
        Log.d("OverlayService", "OverlayService destruido")
    }
}
