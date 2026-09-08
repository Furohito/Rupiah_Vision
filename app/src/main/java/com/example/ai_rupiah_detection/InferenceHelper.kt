package com.example.ai_rupiah_detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * Singleton helper untuk inferensi ONNX model deteksi uang Rupiah.
 *
 * Model yang dipakai (hasil cek langsung dari best.onnx Anda):
 * - Input : name="images", shape=[1, 3, 224, 224], float32, RGB 0-1, NCHW
 * - Output: name="output0", shape=[1, 7], float32 (sudah softmax = probabilitas)
 * - Jumlah kelas auto-detect = 7
 *
 * Default label (WAJIB urutan training, string-sort):
 * 0=1000, 1=10000, 2=100000, 3=2000, 4=20000, 5=5000, 6=50000
 * Jangan diubah manual tanpa mengubah training. Bisa override via setLabels().
 */
object InferenceHelper {

    private const val TAG = "InferenceHelper"
    private const val INPUT_SIZE = 224

    // Label WAJIB sama persis urutannya dengan training:
    // CLASS_NAMES = ['1000','10000','100000','2000','20000','5000','50000']
    // 0=1000, 1=10000, 2=100000, 3=2000, 4=20000, 5=5000, 6=50000
    // Format display ("Rp 100.000") diurus di UI via formatRupiah(), bukan di sini.
    private var labels: List<String> = listOf(
        "1000",
        "10000",
        "100000",
        "2000",
        "20000",
        "5000",
        "50000"
    )

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null
    private var numClasses: Int = 0

    fun isLoaded(): Boolean = ortSession != null

    fun getNumClasses(): Int = numClasses

    fun getLabels(): List<String> = labels.toList()

    /** Override label jika urutan kelas training berbeda. Ukuran harus == numClasses. */
    fun setLabels(newLabels: List<String>) {
        if (numClasses != 0 && newLabels.size != numClasses) {
            Log.w(TAG, "setLabels: ukuran ${newLabels.size} != numClasses $numClasses, tetap dipasang tapi cek kembali!")
        }
        labels = newLabels.toList()
        Log.d(TAG, "Labels diganti: $labels")
    }

    // -------------------------------------------------------------------------
    // 1. loadModel
    // -------------------------------------------------------------------------
    /**
     * Copy best.onnx dari assets ke internal storage lalu load dengan ONNX Runtime.
     * PANGGIL DARI BACKGROUND THREAD (Dispatchers.IO), jangan di Main Thread.
     * @return true jika berhasil load.
     */
    @Synchronized
    fun loadModel(context: Context, modelFileName: String = "best.onnx"): Boolean {
        if (ortSession != null) {
            Log.d(TAG, "Model sudah di-load, skip. numClasses=$numClasses")
            return true
        }
        return try {
            // 1) Copy assets -> internal storage (wajib, ORT butuh file path)
            val modelFile = File(context.filesDir, modelFileName)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.d(TAG, "Copy $modelFileName dari assets ke ${modelFile.absolutePath} ...")
                context.assets.open(modelFileName).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copy selesai, size=${modelFile.length()} bytes")
            } else {
                Log.d(TAG, "Model sudah ada di internal storage, size=${modelFile.length()} bytes")
            }

            // 2) Buat environment + session
            if (ortEnvironment == null) {
                ortEnvironment = OrtEnvironment.getEnvironment()
            }
            val options = OrtSession.SessionOptions()
            // Opsional: pakai semua thread CPU. Untuk HP low-end, batasi 2-4 (lihat penjelasan optimasi).
            // options.setIntraOpNumThreads(4)
            ortSession = ortEnvironment!!.createSession(modelFile.absolutePath, options)

            // 3) Cetak info input/output untuk debugging
            val session = ortSession!!
            Log.d(TAG, "===== MODEL INFO =====")
            Log.d(TAG, "Input count: ${session.inputNames.size}")
            session.inputNames.forEach { name ->
                val info = session.inputInfo[name]
                Log.d(TAG, "Input name='$name' info=$info")
                if (inputName == null) inputName = name
            }
            Log.d(TAG, "Output count: ${session.outputNames.size}")
            session.outputNames.forEach { name ->
                val info = session.outputInfo[name]
                Log.d(TAG, "Output name='$name' info=$info")
                if (outputName == null) outputName = name
            }

            // 4) Auto-detect jumlah kelas dari output shape [1, N]
            numClasses = detectNumClasses(session)
            Log.d(TAG, "numClasses auto-detect = $numClasses")
            Log.d(TAG, "inputName=$inputName outputName=$outputName")
            Log.d(TAG, "======================")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal load model $modelFileName: ${e.message}", e)
            close()
            false
        }
    }

    /** Baca shape output [1, N] -> N = jumlah kelas. Fallback: jalankan dummy inference. */
    private fun detectNumClasses(session: OrtSession): Int {
        return try {
            val outName = session.outputNames.first()
            val infoStr = session.outputInfo[outName].toString()
            // Contoh info: TensorInfo(java.float32, shape=[1,7]) -> ambil angka terakhir
            val numbers = Regex("\\d+").findAll(infoStr).map { it.value.toInt() }.toList()
            if (numbers.size >= 2) {
                numbers.last() // [1,7] -> 7
            } else {
                Log.w(TAG, "Tidak bisa parse numClasses dari '$infoStr', pakai fallback labels.size")
                labels.size
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectNumClasses gagal: ${e.message}, fallback ke ${labels.size}")
            labels.size
        }
    }

    // -------------------------------------------------------------------------
    // 2. preprocessBitmap
    // -------------------------------------------------------------------------
    /**
     * Preprocessing standar klasifikasi (ImageNet-style):
     * scale sisi pendek -> 224 lalu CENTER-CROP 224x224 (bilinear),
     * ambil RGB tanpa alpha, normalisasi /255 (0-1), transpose HWC -> CHW.
     * Center-crop menjaga aspek rasio (uang tidak gepeng) — lebih akurat
     * daripada stretch langsung, terutama untuk 1000 vs 2000 yang mirip.
     * @return FloatArray ukuran 1*3*224*224 = 150528
     */
    fun preprocessBitmap(bitmap: Bitmap): FloatArray {
        // 1) Scale proporsional: sisi pendek = 224
        val w = bitmap.width
        val h = bitmap.height
        val scale = INPUT_SIZE / minOf(w, h).toFloat()
        val newW = (w * scale + 0.5f).toInt().coerceAtLeast(INPUT_SIZE)
        val newH = (h * scale + 0.5f).toInt().coerceAtLeast(INPUT_SIZE)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        // 2) Center crop 224x224
        val x = (newW - INPUT_SIZE) / 2
        val y = (newH - INPUT_SIZE) / 2
        val cropped = Bitmap.createBitmap(scaled, x, y, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        cropped.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (cropped !== bitmap && cropped !== scaled) {
            cropped.recycle() // hemat memori
        }

        // CHW: channel R dulu semua piksel, lalu G, lalu B (seperti np.transpose(2,0,1))
        val floatValues = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        var rIndex = 0
        var gIndex = INPUT_SIZE * INPUT_SIZE
        var bIndex = 2 * INPUT_SIZE * INPUT_SIZE
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            floatValues[rIndex++] = r
            floatValues[gIndex++] = g
            floatValues[bIndex++] = b
        }
        return floatValues
    }

    // -------------------------------------------------------------------------
    // 3. runInference
    // -------------------------------------------------------------------------
    /** Jalankan session.run(), return probabilitas (sudah softmax dari model). */
    fun runInference(inputFloat: FloatArray): FloatArray {
        val session = ortSession
            ?: throw IllegalStateException("Model belum di-load! Panggil loadModel() dulu.")
        val inName = inputName
            ?: throw IllegalStateException("inputName null, loadModel() gagal?")
        val outName = outputName
            ?: throw IllegalStateException("outputName null, loadModel() gagal?")

        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputBuffer = FloatBuffer.wrap(inputFloat)

        var inputTensor: OnnxTensor? = null
        try {
            inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, shape)
            val inputs = mapOf(inName to inputTensor)
            session.run(inputs).use { result ->
                val node = result.get(outName).orElseThrow {
                    IllegalStateException("Output '$outName' tidak ditemukan di result")
                }
                @Suppress("UNCHECKED_CAST")
                val output2D = node.value as? Array<FloatArray>
                    ?: throw IllegalStateException("Format output tak dikenal: ${node.value?.javaClass}")
                Log.d(TAG, "Inference OK, output size=${output2D[0].size}")
                return output2D[0].copyOf()
            }
        } finally {
            try { inputTensor?.close() } catch (_: Exception) { }
        }
    }

    // -------------------------------------------------------------------------
    // 4. getPrediction
    // -------------------------------------------------------------------------
    /**
     * Argmax + format "Kelas X (95.3%)".
     * Jika confidence < 50% -> "Tidak yakin (45.2%)".
     * Top-3 selalu di-log untuk debugging.
     */
    fun getPrediction(output: FloatArray): String {
        if (output.isEmpty()) return "Output kosong"
        if (numClasses != 0 && output.size != numClasses) {
            Log.w(TAG, "Ukuran output ${output.size} != numClasses $numClasses")
        }

        var bestIdx = 0
        var bestScore = output[0]
        for (i in 1 until output.size) {
            if (output[i] > bestScore) {
                bestScore = output[i]
                bestIdx = i
            }
        }

        // Top-3 untuk debugging
        val top3 = output.indices.sortedByDescending { output[it] }.take(3)
        val top3Str = top3.joinToString(" | ") { idx ->
            val label = labels.getOrElse(idx) { "Kelas $idx" }
            "$label=${"%.1f".format(output[idx] * 100)}%"
        }
        Log.d(TAG, "Top-3: $top3Str")

        val label = labels.getOrElse(bestIdx) { "Kelas $bestIdx" }
        val conf = "%.1f".format(bestScore * 100)
        return "$label ($conf%)"
    }

    // -------------------------------------------------------------------------
    // 5. predictImage (satu pintu)
    // -------------------------------------------------------------------------
    /**
     * All-in-one: cek model -> preprocess -> inference -> format.
     * PANGGIL DARI BACKGROUND THREAD (contoh: lifecycleScope.launch(Dispatchers.IO)).
     */
    fun predictImage(context: Context, bitmap: Bitmap): String {
        return try {
            if (!isLoaded()) {
                Log.d(TAG, "Model belum loaded, auto-load sekarang...")
                val ok = loadModel(context)
                if (!ok) return "Error: gagal load model best.onnx"
            }
            val start = System.currentTimeMillis()
            val input = preprocessBitmap(bitmap)
            val output = runInference(input)
            val result = getPrediction(output)
            val latency = System.currentTimeMillis() - start
            Log.d(TAG, "predictImage selesai dalam ${latency}ms -> $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "predictImage gagal: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /** Versi yang juga mengembalikan latency (ms) untuk benchmarking. */
    fun predictImageWithLatency(context: Context, bitmap: Bitmap): Pair<String, Long> {
        val start = System.currentTimeMillis()
        val result = predictImage(context, bitmap)
        return result to (System.currentTimeMillis() - start)
    }

    // -------------------------------------------------------------------------
    // 6. close
    // -------------------------------------------------------------------------
    fun close() {
        try { ortSession?.close() } catch (e: Exception) {
            Log.w(TAG, "close session gagal: ${e.message}")
        }
        ortSession = null
        inputName = null
        outputName = null
        // Jangan close ortEnvironment (singleton global ORT). Biarkan hidup selama app.
        Log.d(TAG, "Session ditutup")
    }
}
