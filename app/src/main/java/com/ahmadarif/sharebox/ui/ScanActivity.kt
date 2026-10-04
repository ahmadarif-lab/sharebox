package com.ahmadarif.sharebox.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmadarif.sharebox.R
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Pemindai QR layar penuh untuk pengirim. Terbuka otomatis saat user menekan Send tanpa
 * penerima terpilih; berakhir dengan teks QR (sharebox://join?...) atau [RESULT_NEARBY].
 * Murni Camera2 + zxing core — tanpa library kamera tambahan.
 */
class ScanActivity : Activity() {

    private lateinit var texture: TextureView
    private lateinit var hint: TextView
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var lastDecode = 0L
    @Volatile private var done = false

    // QRCodeReader langsung (bukan MultiFormatReader): yang terakhir menarik semua pembaca
    // barcode zxing ke APK, padahal yang dibutuhkan hanya QR.
    private val qr = QRCodeReader()
    private val hints = mapOf<DecodeHintType, Any>(DecodeHintType.TRY_HARDER to true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        texture = TextureView(this)
        root.addView(texture, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ScanActivity, 24f), Ui.dp(this@ScanActivity, 56f), Ui.dp(this@ScanActivity, 24f), 0)
        }
        top.addView(label(getString(R.string.scan_title), 22f, true))
        hint = label(getString(R.string.scan_hint), 14f, false)
        top.addView(hint)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Bingkai bidik di tengah.
        val frame = View(this).apply { setBackgroundResource(R.drawable.bg_scan_frame) }
        val size = Ui.dp(this, 240f)
        root.addView(frame, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ScanActivity, 24f), 0, Ui.dp(this@ScanActivity, 24f), Ui.dp(this@ScanActivity, 32f))
        }
        bottom.addView(button(getString(R.string.scan_nearby), R.drawable.bg_btn_surface, getColor(R.color.fg)) {
            finishWith(RESULT_NEARBY)
        })
        bottom.addView(button(getString(R.string.cancel), R.drawable.bg_btn_ghost, Color.WHITE, topMargin = 10f) {
            setResult(RESULT_CANCELED)
            finish()
        })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun label(text: String, sp: Float, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(Color.WHITE)
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, Ui.dp(this@ScanActivity, 4f))
    }

    private fun button(text: String, bg: Int, color: Int, topMargin: Float = 0f, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(color)
            setBackgroundResource(bg)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(0, Ui.dp(this@ScanActivity, 14f), 0, Ui.dp(this@ScanActivity, 14f))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { this.topMargin = Ui.dp(this@ScanActivity, topMargin) }
        }

    override fun onResume() {
        super.onResume()
        done = false
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            return
        }
        startWhenReady()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startWhenReady()
        else hint.setText(R.string.scan_no_camera)
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    private fun startWhenReady() {
        if (texture.isAvailable) {
            openCamera()
        } else {
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = openCamera()
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        if (camera != null) return
        val cm = getSystemService(CameraManager::class.java)
        try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull() ?: run {
                hint.setText(R.string.scan_no_camera)
                return
            }
            val map = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
            // Cukup 720p untuk membaca QR; lebih besar hanya memperlambat decode.
            val size = sizes.filter { it.width <= 1280 && it.height <= 960 }
                .maxByOrNull { it.width * it.height } ?: sizes.firstOrNull() ?: Size(640, 480)

            // Layar portrait: rasio pratinjau dibalik, dan lebarnya dipenuhi layar.
            val w = resources.displayMetrics.widthPixels
            texture.layoutParams = FrameLayout.LayoutParams(w, w * size.width / size.height, Gravity.CENTER)
            texture.surfaceTexture?.setDefaultBufferSize(size.width, size.height)

            val t = HandlerThread("sharebox-scan").also { it.start() }
            thread = t
            handler = Handler(t.looper)
            val rd = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            rd.setOnImageAvailableListener({ onFrame(it) }, handler)
            reader = rd

            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    startSession(device, rd)
                }

                override fun onDisconnected(device: CameraDevice) = device.close()
                override fun onError(device: CameraDevice, error: Int) = device.close()
            }, handler)
        } catch (e: Exception) {
            hint.setText(R.string.scan_no_camera)
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(device: CameraDevice, rd: ImageReader) {
        try {
            val previewSurface = Surface(texture.surfaceTexture)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(rd.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            device.createCaptureSession(listOf(previewSurface, rd.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    runCatching { s.setRepeatingRequest(request.build(), null, handler) }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {}
            }, handler)
        } catch (_: Exception) {
        }
    }

    /** Decode frame luminance (plane Y) paling baru; maksimal ~6 kali per detik. */
    private fun onFrame(rd: ImageReader) {
        val image = rd.acquireLatestImage() ?: return
        try {
            val now = System.currentTimeMillis()
            if (done || now - lastDecode < 160) return
            lastDecode = now
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val buf = plane.buffer
            val row = plane.rowStride
            val data = ByteArray(w * h)
            for (y in 0 until h) {
                buf.position(y * row)
                buf.get(data, y * w, w)
            }
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            // Hybrid dulu (tahan bayangan); kalau gagal coba histogram global, yang kadang lebih baik
            // untuk QR di layar terang dengan area putih besar.
            val text = decode(BinaryBitmap(HybridBinarizer(source)))
                ?: decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
            if (text != null && text.startsWith("sharebox://join")) {
                done = true
                runOnUiThread { finishWith(text) }
            }
        } finally {
            image.close()
        }
    }

    private fun decode(bitmap: BinaryBitmap): String? = try {
        qr.decode(bitmap, hints).text
    } catch (_: Exception) {
        null
    } finally {
        qr.reset()
    }

    private fun finishWith(text: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
        finish()
    }

    private fun closeCamera() {
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { reader?.close() }
        session = null
        camera = null
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val RESULT_NEARBY = "nearby"
        private const val REQ_CAMERA = 301
    }
}
