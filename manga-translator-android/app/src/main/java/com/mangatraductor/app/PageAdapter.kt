package com.mangatraductor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mangatraductor.app.databinding.ItemPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Lista vertical de páginas, como un lector de manga. */
class PageAdapter(
    private val scope: CoroutineScope,
    private val onTap: (PageItem) -> Unit,
    private val onLongPress: (PageItem) -> Unit,
) : ListAdapter<PageItem, PageAdapter.Holder>(DIFF) {

    /** Ancho en píxeles con el que se muestran las páginas (el de la lista). */
    var targetWidth = 1080

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    class Holder(val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var key: String? = null
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        // Reservar el alto de la página antes de cargarla, para que la lista no salte.
        b.image.layoutParams = b.image.layoutParams.apply { height = targetWidth * item.height / item.width }

        val file = if (item.showOriginal || item.result == null) item.source else item.result
        val key = "${file.path}#${item.version}"
        if (holder.key != key) {
            holder.key = key
            holder.job?.cancel()
            val cached = cache.get(key)
            if (cached != null) {
                b.image.setImageBitmap(cached)
            } else {
                b.image.setImageDrawable(null)
                holder.job = scope.launch {
                    val bitmap = withContext(Dispatchers.IO) { decodeForScreen(file, targetWidth) } ?: return@launch
                    cache.put(key, bitmap)
                    if (holder.key == key) b.image.setImageBitmap(bitmap)
                }
            }
        }

        when (item.status) {
            PageStatus.WAITING, PageStatus.WORKING -> {
                b.statusBox.visibility = View.VISIBLE
                b.progress.visibility = View.VISIBLE
                b.status.text = item.progress
            }
            PageStatus.ERROR -> {
                b.statusBox.visibility = View.VISIBLE
                b.progress.visibility = View.GONE
                b.status.text = item.progress + "\n\nMantén pulsado para reintentar."
            }
            PageStatus.DONE -> b.statusBox.visibility = View.GONE
        }
        b.badge.visibility = if (item.showOriginal && item.result != null) View.VISIBLE else View.GONE

        b.root.setOnClickListener { onTap(item) }
        b.root.setOnLongClickListener { onLongPress(item); true }
    }

    private fun decodeForScreen(file: File, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        // imágenes muy largas: que el bitmap no pase de ~4 Mpx (límite de dibujo de Android)
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 4_000_000) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PageItem>() {
            override fun areItemsTheSame(a: PageItem, b: PageItem) = a.id == b.id
            override fun areContentsTheSame(a: PageItem, b: PageItem) = a == b
        }
    }
}
