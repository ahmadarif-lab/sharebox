package com.ahmadarif.sharebox.net

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object Qr {

    fun bitmap(text: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * QR kredensial hotspot (skema standar `WIFI:`) — dipindai kamera HP/iPhone, device
     * langsung ditawari menyambung tanpa mengetik SSID & sandi.
     */
    fun wifiQr(ssid: String, pass: String): String {
        fun esc(s: String): String = s
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
        val type = if (pass.isBlank()) "nopass" else "WPA"
        return "WIFI:T:$type;S:${esc(ssid)};P:${esc(pass)};H:false;;"
    }

    fun wifiBitmap(ssid: String, pass: String, size: Int = 512): Bitmap =
        bitmap(wifiQr(ssid, pass), size)
}
