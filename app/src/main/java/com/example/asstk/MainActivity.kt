package com.example.asstk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.asstk.ui.theme.RobotAssistantTheme

class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_OVERLAY_PERMISSION = 101
    private val REQUEST_CODE_RECORD_AUDIO_PERMISSION = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RobotAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionScreen(::requestPermissionsAndStartService)
                }
            }
        }
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // 1. Permiso de Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
            return
        }

        // 2. Permiso de Grabación de Audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO_PERMISSION)
            return
        }

        // Si todos los permisos están concedidos, iniciar el servicio
        startRobotServices()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Log.d("MainActivity", "Permiso de overlay concedido.")
                checkAndRequestPermissions() // Re-verificar otros permisos o iniciar servicios
            } else {
                Log.e("MainActivity", "Permiso de overlay denegado.")
                Toast.makeText(this, "Se necesita el permiso de superposición para mostrar el robot.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permiso de RECORD_AUDIO concedido.")
                checkAndRequestPermissions() // Re-verificar otros permisos o iniciar servicios
            } else {
                Log.e("MainActivity", "Permiso de RECORD_AUDIO denegado.")
                Toast.makeText(this, "Se necesita el permiso de micrófono para el reconocimiento de voz.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPermissionsAndStartService() {
        checkAndRequestPermissions()
    }

    private fun startRobotServices() {
        // Iniciar OverlayService
        startService(Intent(this, OverlayService::class.java))
        Log.d("MainActivity", "OverlayService iniciado.")

        // NOTA: El AccessibilityService se activa manualmente por el usuario en Ajustes -> Accesibilidad.
        // Aquí solo podemos guiar al usuario a esa configuración.
        Toast.makeText(this, "Por favor, activa 'Robot Assistant' en Ajustes > Accesibilidad.", Toast.LENGTH_LONG).show()
        // Opcional: Abrir directamente la configuración de accesibilidad
        // val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        // startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener OverlayService cuando la actividad principal se destruye (opcional, depende del comportamiento deseado)
        // stopService(Intent(this, OverlayService::class.java))
    }
}

@Composable
fun PermissionScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Robot Assistant necesita permisos para funcionar.")
        Button(onClick = onRequestPermissions) {
            Text("Conceder Permisos")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RobotAssistantTheme {
        PermissionScreen {}
    }
}
