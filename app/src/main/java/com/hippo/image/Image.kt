/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.image

import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ALLOCATOR_HARDWARE
import android.graphics.ImageDecoder.DecodeException
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.hippo.ehviewer.EhApplication
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class Image private constructor(source: Source?, val release: () -> Unit = {}) {
    var mObtainedDrawable: Drawable?

    init {
        mObtainedDrawable = null
        source?.let {
            mObtainedDrawable =
                ImageDecoder.decodeDrawable(source) { decoder: ImageDecoder, info: ImageInfo, src: Source ->
                    decoder.allocator = ALLOCATOR_HARDWARE
                    decoder.setTargetColorSpace(colorSpace)
                    decoder.setTargetSampleSize(
                        max(
                            min(
                                info.size.width / (2 * screenWidth),
                                info.size.height / (2 * screenHeight)
                            ), 1
                        )
                    )
                } // Should we lazy decode it?
        }
        if (mObtainedDrawable is BitmapDrawable)
            release()
    }

    val width = mObtainedDrawable!!.intrinsicWidth
    val height = mObtainedDrawable!!.intrinsicHeight
    var started = false

    @Synchronized
    fun recycle() {
        if (mObtainedDrawable is AnimatedImageDrawable) {
            (mObtainedDrawable as AnimatedImageDrawable?)?.stop()
        }
        if (mObtainedDrawable is BitmapDrawable) {
            (mObtainedDrawable as BitmapDrawable?)?.bitmap?.recycle()
        }
        mObtainedDrawable?.callback = null
        if (mObtainedDrawable is AnimatedImageDrawable)
            release()
        mObtainedDrawable = null
    }

    fun start() {
        if (!started) {
            (mObtainedDrawable as AnimatedImageDrawable?)?.start()
        }
    }

    val isRecycled: Boolean
        get() = (mObtainedDrawable as? BitmapDrawable)?.bitmap?.isRecycled ?: (mObtainedDrawable as? AnimatedImageDrawable)?.isRunning?.not() ?: true

    companion object {
        val screenWidth = EhApplication.application.resources.displayMetrics.widthPixels
        val screenHeight = EhApplication.application.resources.displayMetrics.heightPixels
        val isWideColorGamut = EhApplication.application.resources.configuration.isScreenWideColorGamut
        val colorSpace = ColorSpace.get(if (isWideColorGamut) ColorSpace.Named.DISPLAY_P3 else ColorSpace.Named.SRGB)

        @Throws(DecodeException::class)
        @JvmStatic
        fun decode(stream: FileInputStream): Image {
            val src = ImageDecoder.createSource(
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY, 0,
                    stream.available().toLong()
                )
            )
            return Image(src)
        }

        @Throws(DecodeException::class)
        @JvmStatic
        fun decode(buffer: ByteBuffer, release: () -> Unit? = {}): Image {
            val src = ImageDecoder.createSource(buffer)
            return Image(src) {
                release()
            }
        }
    }
}