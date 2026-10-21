
package com.example.explorago

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Naranja = Color(0xFFFF8500)
private val Negro = Color(0xFF202020)
private val FondoClaro = Color(0xFFF2F2F2)
private val FondoOscuro = Color(0xFF121212)
private val TarjetaOscura = Color(0xFF292929)

data class PuntoInteres(
    val foto: Bitmap,
    val fecha: String,
    val ubicacion: String,
    val archivo: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExploraGo()
        }
    }
}

private fun coordenadasDe(ubicacion: String): LatLng? {
    return try {
        val patron = Regex(
            """Lat:\s*(-?\d+(?:\.\d+)?),\s*Lon:\s*(-?\d+(?:\.\d+)?)"""
        )
        val resultado = patron.find(ubicacion) ?: return null
        val lat = resultado.groupValues[1].toDouble()
        val lon = resultado.groupValues[2].toDouble()

        if (lat in -90.0..90.0 && lon in -180.0..180.0) {
            LatLng(lat, lon)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ExploraGo() {
    val context = LocalContext.current

    var recorridoActivo by remember { mutableStateOf(false) }
    var verPuntos by remember { mutableStateOf(false) }
    var puntos by remember { mutableStateOf(listOf<PuntoInteres>()) }
    var pasos by remember { mutableIntStateOf(0) }
    var luz by remember { mutableFloatStateOf(100f) }
    var modoOscuro by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("¡Bienvenido a ExploraGo!") }
    var sensorDisponible by remember { mutableStateOf(false) }
    var permisoActividad by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    context.checkSelfPermission(
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var basePasos by remember { mutableFloatStateOf(-1f) }

    val distanciaKm = pasos * 0.0007

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val sensorPasos = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }

    val sensorLuz = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    val ubicacionInicial = LatLng(4.7110, -74.0721)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ubicacionInicial, 12f)
    }

    val permisosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val ubicacionPermitida =
            permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    context.checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        val camaraPermitida =
            permisos[Manifest.permission.CAMERA] == true ||
                    context.checkSelfPermission(
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

        permisoActividad =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    permisos[Manifest.permission.ACTIVITY_RECOGNITION] == true ||
                    context.checkSelfPermission(
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED

        if (ubicacionPermitida && camaraPermitida) {
            pasos = 0
            basePasos = -1f
            recorridoActivo = true
            mensaje = if (permisoActividad) {
                "Recorrido iniciado. Camina para contar pasos."
            } else {
                "Recorrido iniciado. Permite Actividad física para contar pasos."
            }
        } else {
            mensaje = "Debes permitir la cámara y la ubicación."
        }
    }

    val camaraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val fecha = SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val ubicacion = obtenerUbicacion(context)
            val archivo = File(
                context.cacheDir,
                "punto_${System.currentTimeMillis()}.jpg"
            )

            try {
                FileOutputStream(archivo).use { salida ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, salida)
                }

                puntos = puntos + PuntoInteres(
                    foto = bitmap,
                    fecha = fecha,
                    ubicacion = ubicacion,
                    archivo = archivo.absolutePath
                )

                verPuntos = true
                mensaje = "Fotografía guardada en puntos de interés."

                coordenadasDe(ubicacion)?.let { coordenadas ->
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(coordenadas, 16f)
                }
            } catch (_: Exception) {
                mensaje = "No se pudo guardar la fotografía en caché."
            }
        }
    }

    DisposableEffect(
        recorridoActivo,
        permisoActividad,
        sensorManager,
        sensorLuz,
        sensorPasos
    ) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        luz = event.values[0]
                        modoOscuro = luz < 30f
                    }

                    Sensor.TYPE_STEP_COUNTER -> {
                        if (!recorridoActivo || !permisoActividad) return

                        val total = event.values[0]
                        if (basePasos < 0f) {
                            basePasos = total
                            pasos = 0
                        } else {
                            pasos = (total - basePasos)
                                .toInt()
                                .coerceAtLeast(0)
                        }
                    }

                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (recorridoActivo && permisoActividad) {
                            pasos += 1
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorLuz != null) {
            sensorManager.registerListener(
                listener,
                sensorLuz,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        if (recorridoActivo && permisoActividad && sensorPasos != null) {
            sensorDisponible = sensorManager.registerListener(
                listener,
                sensorPasos,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } else {
            sensorDisponible = false
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val texto = if (modoOscuro) Color.White else Negro
    val fondo = if (modoOscuro) FondoOscuro else FondoClaro
    val tarjeta = if (modoOscuro) TarjetaOscura else Color.White

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = fondo
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "ExploraGo",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Naranja
                )

                Text(
                    "Actividad física y exploración",
                    color = texto,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { verPuntos = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!verPuntos) Naranja else Negro
                        )
                    ) {
                        Text("Recorrido")
                    }

                    Button(
                        onClick = { verPuntos = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (verPuntos) Naranja else Negro
                        )
                    ) {
                        Text("Mis puntos")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (!verPuntos) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = tarjeta
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "RESUMEN DE ACTIVIDAD",
                                        color = Naranja,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text("Pasos: $pasos", color = texto, fontSize = 22.sp)

                                    Text(
                                        "Distancia estimada: ${
                                            String.format(
                                                Locale.getDefault(),
                                                "%.2f",
                                                distanciaKm
                                            )
                                        } km",
                                        color = texto,
                                        fontSize = 18.sp
                                    )

                                    Text(
                                        if (recorridoActivo) "Estado: recorrido en curso"
                                        else "Estado: sin iniciar",
                                        color = texto
                                    )

                                    Text(
                                        when {
                                            sensorPasos == null -> "Sensor de pasos no detectado."
                                            !permisoActividad -> "Falta el permiso de Actividad física."
                                            sensorDisponible -> "Sensor de pasos activado."
                                            else -> "Sensor de pasos esperando activación."
                                        },
                                        color = texto,
                                        fontSize = 12.sp
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (recorridoActivo) {
                                                recorridoActivo = false
                                                mensaje = "Recorrido finalizado: $pasos pasos."
                                            } else {
                                                val lista = mutableListOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.CAMERA
                                                )

                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    lista.add(
                                                        Manifest.permission.ACTIVITY_RECOGNITION
                                                    )
                                                }

                                                permisosLauncher.launch(lista.toTypedArray())
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Naranja
                                        )
                                    ) {
                                        Text(
                                            if (recorridoActivo) "Finalizar recorrido"
                                            else "Iniciar recorrido",
                                            color = Negro
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "Mapa del recorrido",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = texto
                            )

                            Spacer(Modifier.height(6.dp))

                            GoogleMap(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp),
                                cameraPositionState = cameraPositionState,
                                properties = MapProperties(
                                    isMyLocationEnabled = false
                                ),
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = true,
                                    zoomGesturesEnabled = true,
                                    scrollGesturesEnabled = true
                                )
                            ) {
                                puntos.forEachIndexed { index, punto ->
                                    val coordenadas = coordenadasDe(punto.ubicacion)

                                    if (coordenadas != null) {
                                        Marker(
                                            state = MarkerState(position = coordenadas),
                                            title = "Punto de interés ${index + 1}",
                                            snippet = punto.fecha
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                "Puntos con coordenadas guardadas: ${
                                    puntos.count { coordenadasDe(it.ubicacion) != null }
                                }",
                                color = texto,
                                fontSize = 13.sp
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = tarjeta
                                )
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "Sensor de luz",
                                        color = Naranja,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        if (sensorLuz == null) "Sensor de luz no disponible."
                                        else "Nivel detectado: ${
                                            String.format(Locale.getDefault(), "%.1f", luz)
                                        } lux",
                                        color = texto
                                    )

                                    Text(
                                        "Tema actual: ${if (modoOscuro) "Oscuro" else "Claro"}",
                                        color = texto
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    if (recorridoActivo) {
                                        camaraLauncher.launch(null)
                                    } else {
                                        mensaje = "Primero debes iniciar el recorrido."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Negro
                                )
                            ) {
                                Text("Tomar fotografía", color = Color.White)
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(mensaje, color = Naranja, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text(
                        "Puntos de interés",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = texto
                    )

                    Spacer(Modifier.height(8.dp))

                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = false),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true)
                    ) {
                        puntos.forEachIndexed { index, punto ->
                            val coordenadas = coordenadasDe(punto.ubicacion)
                            if (coordenadas != null) {
                                Marker(
                                    state = MarkerState(position = coordenadas),
                                    title = "Punto de interés ${index + 1}",
                                    snippet = punto.fecha
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (puntos.isEmpty()) {
                        Text("Todavía no hay puntos de interés.", color = texto)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(puntos) { indice, punto ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = tarjeta
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Image(
                                            bitmap = punto.foto.asImageBitmap(),
                                            contentDescription = "Foto ${indice + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(Modifier.height(6.dp))

                                        Text(
                                            "Punto de interés ${indice + 1}",
                                            color = Naranja,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text("Fecha y hora: ${punto.fecha}", color = texto)
                                        Text(
                                            "Ubicación: ${punto.ubicacion}",
                                            color = texto,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { verPuntos = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Naranja
                        )
                    ) {
                        Text("Volver al recorrido", color = Negro)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text("ExploraGo", color = texto, fontSize = 12.sp)
            }
        }
    }
}

fun obtenerUbicacion(context: Context): String {
    val precisa = context.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val aproximada = context.checkSelfPermission(
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!precisa && !aproximada) {
        return "Permiso de ubicación no concedido"
    }

    return try {
        val manager = context.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        val ubicacion: Location? =
            try {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) {
                null
            } ?: try {
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) {
                null
            }

        if (ubicacion != null) {
            "Lat: ${ubicacion.latitude}, Lon: ${ubicacion.longitude}"
        } else {
            "Ubicación no disponible todavía"
        }
    } catch (_: Exception) {
        "No fue posible obtener la ubicación"
    }
}
