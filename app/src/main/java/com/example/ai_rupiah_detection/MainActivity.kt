package com.example.ai_rupiah_detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai_rupiah_detection.ui.theme.AI_Rupiah_DetectionTheme
import com.example.ai_rupiah_detection.ui.theme.RvDarkBg
import com.example.ai_rupiah_detection.ui.theme.RvDarkBorder
import com.example.ai_rupiah_detection.ui.theme.RvDarkCard
import com.example.ai_rupiah_detection.ui.theme.RvDarkDarker
import com.example.ai_rupiah_detection.ui.theme.RvDarkInput
import com.example.ai_rupiah_detection.ui.theme.RvDarkMuted
import com.example.ai_rupiah_detection.ui.theme.RvDarkSecondary
import com.example.ai_rupiah_detection.ui.theme.RvDarkText
import com.example.ai_rupiah_detection.ui.theme.RvEmerald
import com.example.ai_rupiah_detection.ui.theme.RvEmeraldBright
import com.example.ai_rupiah_detection.ui.theme.RvEmeraldDarkBg
import com.example.ai_rupiah_detection.ui.theme.RvEmeraldLightBg
import com.example.ai_rupiah_detection.ui.theme.RvEmeraldLightBorder
import com.example.ai_rupiah_detection.ui.theme.RvEmeraldLightText
import com.example.ai_rupiah_detection.ui.theme.RvLightBg
import com.example.ai_rupiah_detection.ui.theme.RvLightBorder
import com.example.ai_rupiah_detection.ui.theme.RvLightCard
import com.example.ai_rupiah_detection.ui.theme.RvLightInput
import com.example.ai_rupiah_detection.ui.theme.RvLightMuted
import com.example.ai_rupiah_detection.ui.theme.RvLightSecondary
import com.example.ai_rupiah_detection.ui.theme.RvLightText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Rupiah Vision — translate mockup HTML/Tailwind ke Jetpack Compose.
// Catatan translate:
// - Tailwind aspect-[16/9]            -> Modifier.aspectRatio(16f/9f)
// - rounded-2xl / rounded-xl         -> 16.dp / 12.dp
// - bg #121212/#1B1B1C/#F8F9FA       -> RvDarkBg/RvDarkCard/RvLightBg (Color.kt)
// - Material Symbols webfont         -> sengaja TIDAK dipakai (butuh WebView/
//   download font). Diganti glyph unicode sederhana agar offline & tanpa
//   dependensi icons tambahan.
// - Font Inter (Google Fonts web)     -> FontFamily.Default (Roboto sistem,
//   metrik mirip Inter). Bisa diganti downloadable font nanti.
// - Status bar iOS "09:41" di mockup  -> tidak dibuat (pakai status bar Android).
// - Logo hotlink Google               -> diganti lingkaran "Rp" lokal.
// ---------------------------------------------------------------------------

private val ClassNames = listOf(
    "1000", "10000", "100000", "2000", "20000", "5000", "50000"
)

private val TerbilangMap = mapOf(
    "1000" to "Satu Ribu Rupiah",
    "2000" to "Dua Ribu Rupiah",
    "5000" to "Lima Ribu Rupiah",
    "10000" to "Sepuluh Ribu Rupiah",
    "20000" to "Dua Puluh Ribu Rupiah",
    "50000" to "Lima Puluh Ribu Rupiah",
    "100000" to "Seratus Ribu Rupiah"
)

/** "100000" -> "Rp 100.000" (ribuan dipisah titik). */
private fun formatRupiah(rawId: String): String {
    val digits = rawId.filter { it.isDigit() }
    if (digits.isEmpty()) return rawId
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}

/** Hasil parse "100000 (99.4%)" -> nominal (raw id) + confidence. */
private data class Prediction(val nominal: String, val confidence: Float?)

private fun parsePrediction(raw: String): Prediction {
    // Format dari InferenceHelper.getPrediction(): "$label ($conf%)"
    // label sekarang raw CLASS_NAMES ("100000"), bukan "Rp 100.000".
    val regex = Regex("""(.+?)\s*\(([0-9.]+)%\)""")
    val m = regex.find(raw.trim())
    return if (m != null) {
        Prediction(m.groupValues[1].trim(), m.groupValues[2].toFloatOrNull())
    } else {
        Prediction(raw.trim(), null)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AI_Rupiah_DetectionTheme(dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onDestroy() {
        InferenceHelper.close()
        super.onDestroy()
    }
}

// ---------------------------------------------------------------------------
// Root: toggle dark/light + gating model load
// ---------------------------------------------------------------------------
private enum class AppPhase { Loading, Ready, Error }

@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isDark by rememberSaveable { mutableStateOf(true) } // default sesuai mockup dark
    var phase by remember { mutableStateOf(AppPhase.Loading) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableStateOf(0) }

    val bg = if (isDark) RvDarkBg else RvLightBg

    LaunchedEffect(loadAttempt) {
        phase = AppPhase.Loading
        errorMsg = null
        val ok = withContext(Dispatchers.IO) {
            try {
                val loaded = InferenceHelper.loadModel(context, "best.onnx")
                if (loaded) InferenceHelper.setLabels(ClassNames)
                loaded
            } catch (e: Exception) {
                errorMsg = e.message
                false
            }
        }
        phase = if (ok) AppPhase.Ready else {
            if (errorMsg == null) errorMsg = "Gagal load best.onnx. Cek Logcat tag InferenceHelper."
            AppPhase.Error
        }
    }

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Crossfade(targetState = phase, label = "app_phase") { p ->
            when (p) {
                AppPhase.Loading -> SplashScreen(isDark = isDark)
                AppPhase.Ready -> DetectionScreen(isDark = isDark, onToggleTheme = { isDark = !isDark })
                AppPhase.Error -> SplashScreen(
                    isDark = isDark,
                    isError = true,
                    errorText = errorMsg ?: "Gagal memuat model.",
                    onRetry = { loadAttempt++ }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Splash — gaya disamakan Rupiah Vision (bukan shadcn lagi)
// ---------------------------------------------------------------------------
@Composable
private fun SplashScreen(
    isDark: Boolean,
    isError: Boolean = false,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val bg = if (isDark) RvDarkBg else RvLightBg
    val card = if (isDark) RvDarkCard else RvLightCard
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val text = if (isDark) RvDarkText else RvLightText
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary
    val input = if (isDark) RvDarkInput else RvLightInput

    Box(
        modifier = Modifier.fillMaxSize().background(bg).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LogoMark(isDark = isDark, size = 56.dp)
            Text("Rupiah Vision", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = text)
            Text(
                "Deteksi nominal uang Rupiah offline di perangkat.",
                fontSize = 14.sp, color = secondary, textAlign = TextAlign.Center
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isError) {
                        Text(errorText ?: "Gagal memuat model.", fontSize = 14.sp, color = Color(0xFFDC2626), textAlign = TextAlign.Center)
                        Button(
                            onClick = { onRetry?.invoke() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color.White else Color.Black,
                                contentColor = if (isDark) Color.Black else Color.White
                            )
                        ) { Text("Coba Lagi") }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = RvEmerald, trackColor = input
                        )
                        Text("Memuat model best.onnx…", fontSize = 13.sp, color = secondary)
                        Text("7 kelas • 224×224 • pertama kali agak lama", fontSize = 12.sp, color = secondary)
                    }
                }
            }
            Text("v2.4 • Offline On-Device AI", fontSize = 11.sp, color = secondary)
        }
    }
}

// ---------------------------------------------------------------------------
// Layar utama Rupiah Vision
// ---------------------------------------------------------------------------
@Composable
fun DetectionScreen(
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bg = if (isDark) RvDarkBg else RvLightBg
    val card = if (isDark) RvDarkCard else RvLightCard
    val cardDarker = if (isDark) RvDarkDarker else RvLightBg
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val text = if (isDark) RvDarkText else RvLightText
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary
    val inputBg = if (isDark) RvDarkInput else RvLightInput

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawResult by remember { mutableStateOf<String?>(null) }
    var latencyMs by remember { mutableLongStateOf(0L) }
    var isPredicting by remember { mutableStateOf(false) }

    val prediction = rawResult?.let { parsePrediction(it) }
    val terbilang = prediction?.nominal?.let { TerbilangMap[it] } ?: ""

    fun runClassify(bmp: Bitmap) {
        scope.launch(Dispatchers.IO) {
            isPredicting = true
            try {
                val (hasil, latency) = InferenceHelper.predictImageWithLatency(context, bmp)
                withContext(Dispatchers.Main) {
                    rawResult = hasil
                    latencyMs = latency
                }
            } finally {
                withContext(Dispatchers.Main) { isPredicting = false }
            }
        }
    }

    fun resetAll() {
        bitmap?.recycle()
        bitmap = null
        rawResult = null
        latencyMs = 0L
    }

    // Pilih gambar -> tampilkan preview, BELUM auto-klasifikasi (sesuai mockup:
    // user menekan "Klasifikasi Lembar Uang" secara eksplisit).
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bmp = decodeSampledBitmapFromUri(context, uri)
            withContext(Dispatchers.Main) {
                if (bmp == null) {
                    Toast.makeText(context, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
                } else {
                    bitmap?.recycle()
                    bitmap = bmp
                    rawResult = null
                    latencyMs = 0L
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp == null) {
            Toast.makeText(context, "Kamera dibatalkan", Toast.LENGTH_SHORT).show()
        } else {
            bitmap?.recycle()
            bitmap = bmp
            rawResult = null
            latencyMs = 0L
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else Toast.makeText(context, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize().background(bg)) {
        // ---- Header (pengganti TopAppBar fixed di HTML) ----
        RvHeader(
            isDark = isDark,
            onToggleTheme = onToggleTheme
        )
        // ---- Body scroll ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Kartu input gambar ----
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Preview 16:9 + overlay khas mockup
                    PreviewBox(
                        bitmap = bitmap,
                        isDark = isDark,
                        onClear = { resetAll() }
                    )
                    // Dua tombol aksi masukan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RvInputButton(
                            label = "Ambil Kamera",
                            glyph = "◉",
                            isDark = isDark,
                            enabled = !isPredicting,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RvInputButton(
                            label = "Pilih Galeri",
                            glyph = "▦",
                            isDark = isDark,
                            enabled = !isPredicting,
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ---- Tombol klasifikasi utama (putih di dark, hitam di light) ----
            Button(
                onClick = { bitmap?.let { runClassify(it) } },
                enabled = bitmap != null && !isPredicting,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color.Black,
                    contentColor = if (isDark) Color(0xFF121212) else Color.White,
                    disabledContainerColor = inputBg,
                    disabledContentColor = secondary
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (isPredicting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = if (isDark) Color(0xFF121212) else Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Mengklasifikasi…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("⌖", fontSize = 20.sp, color = RvEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Klasifikasi Lembar Uang", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ---- Kartu hasil ----
            ResultCard(
                isDark = isDark,
                prediction = prediction,
                terbilang = terbilang,
                latencyMs = latencyMs,
                isPredicting = isPredicting,
                hasImage = bitmap != null,
                onReset = { resetAll() }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Komponen: Header
// ---------------------------------------------------------------------------
@Composable
private fun RvHeader(isDark: Boolean, onToggleTheme: () -> Unit) {
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val text = if (isDark) RvDarkText else RvLightText
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary
    val card = if (isDark) RvDarkCard else RvLightCard
    val badgeBg = if (isDark) RvDarkInput else RvLightInput

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) RvDarkBg else RvLightCard)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(isDark = isDark, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rupiah Vision", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("v2.4", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (isDark) RvEmeraldBright else RvEmerald)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Offline On-Device AI • Model ONNX",
                        fontSize = 11.sp, color = secondary, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        // Toggle dark/light
        OutlinedButton(
            onClick = onToggleTheme,
            shape = CircleShape,
            border = BorderStroke(1.dp, border),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = card, contentColor = text),
            modifier = Modifier.size(40.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(if (isDark) "☾" else "☀", fontSize = 17.sp)
        }
    }
}

@Composable
private fun LogoMark(isDark: Boolean, size: androidx.compose.ui.unit.Dp) {
    val border = if (isDark) RvDarkBorder else RvLightBorder
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .padding(1.dp)
                .clip(CircleShape)
                .background(Color(0xFF047857)),
            contentAlignment = Alignment.Center
        ) {
            Text("Rp", fontSize = (size.value * 0.32).sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size((size.value * 0.3).dp)
                .clip(CircleShape)
                .background(RvEmerald)
                .padding(2.dp)
        ) { }
    }
}

// ---------------------------------------------------------------------------
// Komponen: Preview 16:9 + overlay
// ---------------------------------------------------------------------------
@Composable
private fun PreviewBox(
    bitmap: Bitmap?,
    isDark: Boolean,
    onClear: () -> Unit
) {
    val innerBg = if (isDark) RvDarkDarker else RvLightInput
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary
    val text = if (isDark) RvDarkText else RvLightText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(innerBg),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview uang",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
            // Badge kiri atas: 1 Gambar Terpilih
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RvEmeraldBright))
                Spacer(Modifier.width(6.dp))
                Text("1 Gambar Terpilih", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            // Badge kanan atas: resolusi
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("${bitmap.width} × ${bitmap.height} px", fontSize = 10.sp, color = Color.White)
            }
            // Tombol Ganti kanan bawah
            TextButton(
                onClick = onClear,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color(0xCC000000),
                    contentColor = Color.White
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).height(32.dp)
            ) {
                Text("✕  Ganti", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Placeholder frame uang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) RvDarkCard else Color.White)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text("▭", fontSize = 28.sp, color = secondary)
                }
                Text("Belum ada gambar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = text)
                Text(
                    "Ambil dari kamera atau galeri — 1 lembar utuh.",
                    fontSize = 12.sp, color = secondary, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RvInputButton(
    label: String,
    glyph: String,
    isDark: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputBg = if (isDark) RvDarkInput else RvLightInput
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val text = if (isDark) RvDarkText else RvLightText
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = inputBg,
            contentColor = text,
            disabledContainerColor = inputBg,
            disabledContentColor = secondary
        ),
        modifier = modifier.height(48.dp)
    ) {
        Text(glyph, fontSize = 16.sp, color = secondary)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Komponen: Kartu hasil
// ---------------------------------------------------------------------------
@Composable
private fun ResultCard(
    isDark: Boolean,
    prediction: Prediction?,
    terbilang: String,
    latencyMs: Long,
    isPredicting: Boolean,
    hasImage: Boolean,
    onReset: () -> Unit
) {
    val card = if (isDark) RvDarkCard else RvLightCard
    val border = if (isDark) RvDarkBorder else RvLightBorder
    val text = if (isDark) RvDarkText else RvLightText
    val secondary = if (isDark) RvDarkSecondary else RvLightSecondary
    val innerBg = if (isDark) RvDarkDarker else RvLightBg
    val inputBg = if (isDark) RvDarkInput else RvLightInput

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header kartu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓  HASIL KLASIFIKASI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondary)
                VerificationBadge(isDark = isDark)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(border))

            when {
                isPredicting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = RvEmerald)
                        Spacer(Modifier.width(12.dp))
                        Text("Menganalisis…", fontSize = 15.sp, color = secondary)
                    }
                }
                prediction != null -> {
                    // Nominal + badge Asli
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatRupiah(prediction.nominal),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = text
                        )
                        AuthenticBadge(isDark = isDark)
                    }
                    if (terbilang.isNotEmpty()) {
                        Text(terbilang, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                    }
                    // Kotak confidence
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerBg)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TINGKAT KEYAKINAN",
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondary
                            )
                            Text(
                                "Sangat Tinggi • Edge ONNX" +
                                    if (latencyMs > 0) " • ${latencyMs} ms" else "",
                                fontSize = 12.sp, color = secondary
                            )
                        }
                        val conf = prediction.confidence
                        Text(
                            if (conf != null) "${"%.1f".format(conf)}%" else "—",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) RvEmeraldBright else RvEmerald
                        )
                    }
                }
                else -> {
                    Text(
                        if (hasImage) "Siap diklasifikasi.\nTekan tombol di atas untuk mulai."
                        else "Belum ada hasil.\nPilih gambar terlebih dahulu.",
                        fontSize = 14.sp, color = secondary, lineHeight = 20.sp
                    )
                }
            }

            // Tombol reset
            OutlinedButton(
                onClick = onReset,
                enabled = (hasImage || prediction != null) && !isPredicting,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = inputBg, contentColor = text,
                    disabledContainerColor = inputBg, disabledContentColor = secondary
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("↻  Pindai Gambar Baru", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VerificationBadge(isDark: Boolean) {
    if (isDark) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RvEmeraldDarkBg.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("● Offline On-Device AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RvEmeraldBright)
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RvEmeraldLightBg)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("● Terverifikasi (Offline)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RvEmeraldLightText)
        }
    }
}

@Composable
private fun AuthenticBadge(isDark: Boolean) {
    if (isDark) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RvEmeraldDarkBg.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("Asli", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RvEmeraldBright)
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RvEmeraldLightBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "Asli", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = RvEmeraldLightText
            )
        }
    }
    // Border tipis via wrapper tidak didukung Box tunggal — cukup tanpa border
    // agar tetap 1 composable (detail minor dari mockup).
}

/** Decode Uri dengan downsampling agar gambar >8MB tidak bikin OOM. Target sisi panjang <= maxSize. */
private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxSize: Int = 1024): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
fun DetectionScreenDarkPreview() {
    AI_Rupiah_DetectionTheme(darkTheme = true, dynamicColor = false) {
        DetectionScreen(isDark = true)
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
fun DetectionScreenLightPreview() {
    AI_Rupiah_DetectionTheme(darkTheme = false, dynamicColor = false) {
        DetectionScreen(isDark = false)
    }
}
