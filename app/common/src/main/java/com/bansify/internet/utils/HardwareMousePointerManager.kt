package com.bansify.internet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.PointerIcon
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import com.bansify.internet.AppContext
import com.bansify.internet.Config
import java.util.concurrent.ConcurrentHashMap

object HardwareMousePointerManager {

    // Style constants
    const val STYLE_CLASSIC_WHITE = 0
    const val STYLE_DARK_ONYX = 1
    const val STYLE_NEON_YELLOW = 2
    const val STYLE_CYAN_GLOW = 3
    const val STYLE_RED_ACCENT = 4

    private val pointerCache = ConcurrentHashMap<String, PointerIcon>()

    fun invalidateCache() {
        pointerCache.clear()
    }

    /**
     * Resolves the hardware pointer icon for a given view/hover request.
     * Takes the system's requested type (e.g. arrow, hand, text) and provides
     * the custom scaled and styled pointer icon if customization is enabled.
     */
    fun resolvePointerIcon(context: Context, requestedIcon: PointerIcon?): PointerIcon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return requestedIcon
        val config = AppContext.provideConfig()
        if (!config.enableHardwareMouseCustomization) return requestedIcon

        val type = extractPointerIconType(requestedIcon)
        return getCustomPointerIcon(context, type, config.hardwareMouseSizePercent, config.hardwareMouseStyle)
            ?: requestedIcon
    }

    private fun extractPointerIconType(icon: PointerIcon?): Int {
        if (icon == null) return PointerIcon.TYPE_ARROW
        return try {
            val method = icon.javaClass.getMethod("getType")
            (method.invoke(icon) as? Int) ?: PointerIcon.TYPE_ARROW
        } catch (e: Throwable) {
            try {
                val field = icon.javaClass.getDeclaredField("mType")
                field.isAccessible = true
                field.getInt(icon)
            } catch (e2: Throwable) {
                PointerIcon.TYPE_ARROW
            }
        }
    }

    /**
     * Gets the default arrow pointer icon with current user preferences.
     */
    fun getArrowPointerIcon(context: Context): PointerIcon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val config = AppContext.provideConfig()
        if (!config.enableHardwareMouseCustomization) return null

        return getCustomPointerIcon(context, PointerIcon.TYPE_ARROW, config.hardwareMouseSizePercent, config.hardwareMouseStyle)
    }

    /**
     * Applies the custom hardware mouse pointer icon to the specified View.
     */
    fun applyToView(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val icon = getArrowPointerIcon(view.context)
            view.pointerIcon = icon
        }
    }

    /**
     * Applies the custom hardware mouse pointer icon to an entire Activity Window.
     */
    fun applyToWindow(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val decorView = window.decorView
            val icon = getArrowPointerIcon(decorView.context)
            decorView.pointerIcon = icon
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getCustomPointerIcon(
        context: Context,
        pointerType: Int,
        sizePercent: Int,
        style: Int
    ): PointerIcon? {
        val cacheKey = "$sizePercent-$style-$pointerType"
        pointerCache[cacheKey]?.let { return it }

        val (bitmap, hotSpotX, hotSpotY) = createPointerBitmapAndHotspot(
            context,
            pointerType,
            sizePercent,
            style
        ) ?: return null

        val pointerIcon = try {
            PointerIcon.create(bitmap, hotSpotX, hotSpotY)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (pointerIcon != null) {
            pointerCache[cacheKey] = pointerIcon
        }
        return pointerIcon
    }

    /**
     * Generates a preview bitmap suitable for display in the Settings UI.
     */
    fun createPreviewBitmap(
        context: Context,
        sizePercent: Int,
        style: Int
    ): Bitmap? {
        val triple = createPointerBitmapAndHotspot(
            context,
            PointerIcon.TYPE_ARROW,
            sizePercent,
            style
        )
        return triple?.first
    }

    private fun createPointerBitmapAndHotspot(
        context: Context,
        pointerType: Int,
        sizePercent: Int,
        style: Int
    ): Triple<Bitmap, Float, Float>? {
        return try {
            val density = context.resources.displayMetrics.density
            val scale = (sizePercent.toFloat() / 100f).coerceIn(0.30f, 3.0f)

            when (pointerType) {
                PointerIcon.TYPE_HAND -> createHandPointerBitmap(density, scale, style)
                PointerIcon.TYPE_TEXT -> createTextPointerBitmap(density, scale, style)
                else -> createArrowPointerBitmap(density, scale, style)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class StyleColors(val fillColor: Int, val strokeColor: Int, val shadowColor: Int)

    private fun getColorsForStyle(style: Int): StyleColors {
        return when (style) {
            STYLE_DARK_ONYX -> StyleColors(
                fillColor = Color.parseColor("#1E293B"),
                strokeColor = Color.parseColor("#FFFFFF"),
                shadowColor = Color.parseColor("#80000000")
            )
            STYLE_NEON_YELLOW -> StyleColors(
                fillColor = Color.parseColor("#FACC15"),
                strokeColor = Color.parseColor("#000000"),
                shadowColor = Color.parseColor("#90000000")
            )
            STYLE_CYAN_GLOW -> StyleColors(
                fillColor = Color.parseColor("#38BDF8"),
                strokeColor = Color.parseColor("#0F172A"),
                shadowColor = Color.parseColor("#90000000")
            )
            STYLE_RED_ACCENT -> StyleColors(
                fillColor = Color.parseColor("#EF4444"),
                strokeColor = Color.parseColor("#FFFFFF"),
                shadowColor = Color.parseColor("#90000000")
            )
            else -> StyleColors( // Classic White
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#000000"),
                shadowColor = Color.parseColor("#70000000")
            )
        }
    }

    private fun createArrowPointerBitmap(
        density: Float,
        scale: Float,
        style: Int
    ): Triple<Bitmap, Float, Float> {
        val baseW = 24f * density
        val baseH = 34f * density
        val width = (baseW * scale).toInt().coerceIn(16, 256)
        val height = (baseH * scale).toInt().coerceIn(22, 360)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = (2.2f * density * (scale.coerceIn(0.7f, 2.0f))).coerceIn(2.0f, 6.0f)
        val strokeOffset = strokeWidth / 2f + 1f

        val colors = getColorsForStyle(style)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.fillColor
            this.style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.strokeColor
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.shadowColor
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth + 2f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val path = Path()
        val availW = width - strokeOffset * 2 - 2f
        val availH = height - strokeOffset * 2 - 2f

        path.moveTo(strokeOffset, strokeOffset)
        path.lineTo(strokeOffset, strokeOffset + availH * 0.82f)
        path.lineTo(strokeOffset + availW * 0.28f, strokeOffset + availH * 0.62f)
        path.lineTo(strokeOffset + availW * 0.54f, strokeOffset + availH * 0.98f)
        path.lineTo(strokeOffset + availW * 0.74f, strokeOffset + availH * 0.84f)
        path.lineTo(strokeOffset + availW * 0.48f, strokeOffset + availH * 0.48f)
        path.lineTo(strokeOffset + availW * 0.88f, strokeOffset + availH * 0.48f)
        path.close()

        // Draw shadow slightly offset
        canvas.save()
        canvas.translate(1.5f, 1.5f)
        canvas.drawPath(path, shadowPaint)
        canvas.restore()

        // Draw fill and outline
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        val hotSpotX = strokeOffset
        val hotSpotY = strokeOffset
        return Triple(bitmap, hotSpotX, hotSpotY)
    }

    private fun createHandPointerBitmap(
        density: Float,
        scale: Float,
        style: Int
    ): Triple<Bitmap, Float, Float> {
        val baseW = 24f * density
        val baseH = 32f * density
        val width = (baseW * scale).toInt().coerceIn(16, 256)
        val height = (baseH * scale).toInt().coerceIn(22, 360)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = (2.0f * density * (scale.coerceIn(0.7f, 2.0f))).coerceIn(2.0f, 5.5f)
        val strokeOffset = strokeWidth / 2f + 1f

        val colors = getColorsForStyle(style)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.fillColor
            this.style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.strokeColor
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val path = Path()
        val availW = width - strokeOffset * 2 - 2f
        val availH = height - strokeOffset * 2 - 2f

        // Pointing hand: index finger pointing up-left
        path.moveTo(strokeOffset + availW * 0.32f, strokeOffset)
        path.lineTo(strokeOffset + availW * 0.48f, strokeOffset)
        path.lineTo(strokeOffset + availW * 0.48f, strokeOffset + availH * 0.42f)
        path.lineTo(strokeOffset + availW * 0.62f, strokeOffset + availH * 0.42f)
        path.lineTo(strokeOffset + availW * 0.62f, strokeOffset + availH * 0.52f)
        path.lineTo(strokeOffset + availW * 0.76f, strokeOffset + availH * 0.52f)
        path.lineTo(strokeOffset + availW * 0.76f, strokeOffset + availH * 0.64f)
        path.lineTo(strokeOffset + availW * 0.88f, strokeOffset + availH * 0.64f)
        path.lineTo(strokeOffset + availW * 0.88f, strokeOffset + availH * 0.90f)
        path.lineTo(strokeOffset + availW * 0.20f, strokeOffset + availH * 0.90f)
        path.lineTo(strokeOffset, strokeOffset + availH * 0.62f)
        path.lineTo(strokeOffset + availW * 0.16f, strokeOffset + availH * 0.46f)
        path.lineTo(strokeOffset + availW * 0.32f, strokeOffset + availH * 0.56f)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        val hotSpotX = strokeOffset + availW * 0.40f
        val hotSpotY = strokeOffset
        return Triple(bitmap, hotSpotX, hotSpotY)
    }

    private fun createTextPointerBitmap(
        density: Float,
        scale: Float,
        style: Int
    ): Triple<Bitmap, Float, Float> {
        val baseW = 16f * density
        val baseH = 28f * density
        val width = (baseW * scale).toInt().coerceIn(14, 200)
        val height = (baseH * scale).toInt().coerceIn(20, 320)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = (2.2f * density * (scale.coerceIn(0.7f, 2.0f))).coerceIn(2.0f, 5.0f)
        val colors = getColorsForStyle(style)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.fillColor
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.strokeColor
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth + 2f
            strokeCap = Paint.Cap.ROUND
        }

        val centerX = width / 2f
        val pad = strokeWidth + 2f

        // I-beam shape
        fun drawIBeam(p: Paint) {
            canvas.drawLine(pad, pad, width - pad, pad, p) // top bar
            canvas.drawLine(centerX, pad, centerX, height - pad, p) // vertical center
            canvas.drawLine(pad, height - pad, width - pad, height - pad, p) // bottom bar
        }

        drawIBeam(outlinePaint)
        drawIBeam(strokePaint)

        val hotSpotX = centerX
        val hotSpotY = height / 2f
        return Triple(bitmap, hotSpotX, hotSpotY)
    }
}
