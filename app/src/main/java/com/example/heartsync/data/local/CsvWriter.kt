//package com.example.heartsync.data.local
//
//import android.content.ContentValues
//import android.content.Context
//import android.net.Uri
//import android.os.Build
//import android.os.Environment
//import android.provider.MediaStore
//import java.io.File
//import java.io.FileOutputStream
//import java.io.OutputStream
//
//class CsvWriter(
//    private val ctx: Context,
//    private val subDir: String
//) {
//    private var uri: Uri? = null
//    private var out: OutputStream? = null
//    private val buf = StringBuilder(16_384)
//
//    fun openNewFile(header: String) {
//        val name = "HeartSync_${System.currentTimeMillis()}.csv"
//
//        if (Build.VERSION.SDK_INT >= 29) {
//            // Android 10+ : MediaStore Downloads에 저장 (권장)
//            val values = ContentValues().apply {
//                put(MediaStore.Downloads.DISPLAY_NAME, name)
//                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
//                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$subDir")
//            }
//            uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
//            out = ctx.contentResolver.openOutputStream(requireNotNull(uri), "w")
//        } else {
//            // Android 9 이하 : 앱 전용 외부 저장소에 저장 (권한 불필요)
//            val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
//            val dir = File(base, subDir).apply { mkdirs() }
//            val file = File(dir, name)
//            uri = Uri.fromFile(file)
//            out = FileOutputStream(file, /*append=*/false)
//        }
//
//        out!!.write(header.toByteArray())
//    }
//
//    fun append(line: String) {
//        buf.append(line).append('\n')
//        if (buf.length >= 16_384) flush()
//    }
//
//    private fun flush() {
//        out?.write(buf.toString().toByteArray())
//        out?.flush()
//        buf.clear()
//    }
//
//    fun closeCurrentFile(): Uri? {
//        if (buf.isNotEmpty()) flush()
//        out?.flush()
//        out?.close()
//        val u = uri
//        uri = null
//        out = null
//        return u
//    }
//}
