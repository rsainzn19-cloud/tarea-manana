package com.mangatraductor.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Guarda imágenes en Imágenes/MangaTraductor de la galería. */
object Gallery {
    private const val FOLDER = "MangaTraductor"

    /** En Android 8-9 hace falta el permiso WRITE_EXTERNAL_STORAGE. */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun save(context: Context, file: File, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear la imagen en la galería.")
            resolver.openOutputStream(uri).use { out -> file.inputStream().use { it.copyTo(out!!) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            dir.mkdirs()
            val target = File(dir, name)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("image/jpeg"), null)
        }
    }
}
