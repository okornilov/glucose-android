package ru.okornilov.glucose.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import ru.okornilov.glucose.data.DayStatsData
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.Zone
import ru.okornilov.glucose.data.dayStats
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.data.percent
import ru.okornilov.glucose.data.zoneOf
import ru.okornilov.glucose.render.ChartPainter
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «Поделиться»: картинка с текущим значением, графиком и статистикой дня плюс то же текстом. */
object Sharer {
    private const val FRESH_MINUTES = 5
    private const val BG = 0xFF1E1F22.toInt()
    private val DATE = DateTimeFormatter.ofPattern("d MMMM")

    /** Готовит Intent для системного меню «Поделиться». Рисует картинку — вызывать не с главного потока. */
    fun intent(
        ctx: Context,
        last: Reading?,
        error: String?,
        day: LocalDate,
        readings: List<Reading>,
        start: Long,
        end: Long,
        low: Double,
        high: Double,
    ): Intent {
        val now = System.currentTimeMillis()
        val stats = dayStats(readings, low, high)
        val text = text(last, error, day, stats, low, high, now)

        val file = File(ctx.cacheDir, "share/glucose.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { image(last, error, day, readings, stats, start, end, low, high, now).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData — чтобы меню показало превью картинки и выдало приложению-получателю доступ к ней
        send.clipData = ClipData.newRawUri("", uri)
        return Intent.createChooser(send, "Поделиться сахаром")
    }

    private fun status(mmol: Double, low: Double, high: Double) = when (zoneOf(mmol, low, high)) {
        Zone.LOW -> "ниже нормы"
        Zone.IN_RANGE -> "в норме"
        Zone.HIGH -> "выше нормы"
    }

    private fun staleMinutes(last: Reading, error: String?, now: Long): Long? {
        val age = (now - last.ts) / 60_000
        return if (error != null || age >= FRESH_MINUTES) age else null
    }

    private fun dayTitle(day: LocalDate): String =
        if (day == LocalDate.now()) "Сегодня" else DATE.format(day)

    private fun text(last: Reading?, error: String?, day: LocalDate, stats: DayStatsData?, low: Double, high: Double, now: Long): String =
        buildString {
            if (last == null) {
                append("🩸 Сахар: нет данных")
            } else {
                append("🩸 Сахар: ${last.mmol.fmt()} ммоль/л ${last.arrow} — ${status(last.mmol, low, high)}\n")
                val lastDay = Instant.ofEpochMilli(last.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                append("Замер: ${if (lastDay == LocalDate.now()) "сегодня" else DATE.format(lastDay)} в ${hhmm(last.ts)}")
                staleMinutes(last, error, now)?.let { append("\n⚠ Данные не обновлялись $it мин") }
            }
            if (stats != null) {
                append("\n\n${dayTitle(day)}: мин ${stats.min.fmt()} · среднее ${stats.avg.fmt()} · макс ${stats.max.fmt()}\n")
                append("В диапазоне ${low.fmt()}–${high.fmt()}: ${percent(stats.inRange)} · ниже ${percent(stats.below)} · выше ${percent(stats.above)}")
            }
        }

    private fun image(
        last: Reading?, error: String?, day: LocalDate, readings: List<Reading>, stats: DayStatsData?,
        start: Long, end: Long, low: Double, high: Double, now: Long,
    ): Bitmap {
        val w = 1080
        val h = 920
        val pad = 56f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                textAlign = align
                if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val white = 0xFFFFFFFF.toInt()
        val gray = 0x99FFFFFF.toInt()

        if (last == null) {
            c.drawText("Нет данных", pad, 190f, paint(110f, white, bold = true))
        } else {
            val stale = staleMinutes(last, error, now)
            val level = ChartPainter.levelColor(last.mmol, low, high)
            // Устаревшее значение — приглушённым цветом, как в приложении
            val valueColor = if (stale != null) (level and 0x00FFFFFF) or 0x80000000.toInt() else level
            val value = paint(150f, valueColor, bold = true)
            c.drawText(last.mmol.fmt(), pad, 200f, value)
            c.drawText(" ${last.arrow}", pad + value.measureText(last.mmol.fmt()), 200f, paint(100f, valueColor))

            c.drawText("ммоль/л", w - pad, 110f, paint(34f, gray, align = Paint.Align.RIGHT))
            c.drawText(hhmm(last.ts), w - pad, 175f, paint(56f, if (stale != null) ChartPainter.LOW_COLOR else white, bold = true, align = Paint.Align.RIGHT))
            val lastDay = Instant.ofEpochMilli(last.ts).atZone(ZoneId.systemDefault()).toLocalDate()
            c.drawText(DATE.format(lastDay), w - pad, 220f, paint(32f, gray, align = Paint.Align.RIGHT))

            val statusPaint = paint(36f, level, bold = true)
            val statusText = status(last.mmol, low, high).replaceFirstChar { it.uppercase() }
            c.drawText(statusText, pad, 272f, statusPaint)
            if (stale != null) {
                c.drawText("  ⚠ данные не обновлялись $stale мин", pad + statusPaint.measureText(statusText), 272f, paint(32f, ChartPainter.LOW_COLOR))
            }
        }

        c.drawText(dayTitle(day), pad, 335f, paint(30f, gray))
        ChartPainter.draw(c, RectF(pad, 355f, w - pad, 760f), readings, start, end, low, high, scale = 2.6f, labels = true, outline = BG)

        if (stats != null) {
            c.drawText(
                "Мин ${stats.min.fmt()}   ·   Среднее ${stats.avg.fmt()}   ·   Макс ${stats.max.fmt()}",
                pad, 830f, paint(36f, white, bold = true),
            )
            c.drawText(
                "В диапазоне ${percent(stats.inRange)}   ·   ниже ${percent(stats.below)}   ·   выше ${percent(stats.above)}",
                pad, 885f, paint(32f, gray),
            )
        }
        return bmp
    }
}
