package ru.okornilov.glucose.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ru.okornilov.glucose.data.Report
import ru.okornilov.glucose.data.durationText
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.percent
import ru.okornilov.glucose.render.AgpPainter
import ru.okornilov.glucose.render.ChartPainter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Отчёт в PDF (A4) для врача: итоги периода, время в диапазоне и «средний день». */
object ReportPdf {
    private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")

    /** Рисует PDF и готовит Intent «Поделиться». Вызывать не с главного потока. */
    fun intent(ctx: Context, r: Report, low: Double, high: Double): Intent {
        val file = File(ctx.cacheDir, "share/glucose-report.pdf").apply { parentFile?.mkdirs() }
        val doc = PdfDocument()
        // A4 в пунктах
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        draw(page.canvas, r, low, high)
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Отчёт по сахару: ${period(r)}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        return Intent.createChooser(send, "Отправить отчёт")
    }

    fun period(r: Report): String {
        val zone = ZoneId.systemDefault()
        return "${DATE.format(Instant.ofEpochMilli(r.from).atZone(zone))} — ${DATE.format(Instant.ofEpochMilli(r.to).atZone(zone))}"
    }

    private fun draw(c: android.graphics.Canvas, r: Report, low: Double, high: Double) {
        val pad = 40f
        val black = 0xFF202124.toInt()
        val gray = 0xFF5F6368.toInt()
        fun paint(size: Float, color: Int = black, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 64f
        c.drawText("Отчёт по глюкозе за ${r.days} дней", pad, y, paint(20f, bold = true))
        y += 20
        c.drawText(period(r), pad, y, paint(11f, gray))
        if (r.coveredDays < r.days - 0.5) {
            y += 14
            c.drawText("Данные есть за ${r.coveredDays.fmt()} дн. — приложение копит историю с момента установки", pad, y, paint(10f, gray))
        }

        // Ключевые показатели — в ряд
        y += 40
        val items = listOf(
            "Среднее" to "${r.stats.avg.fmt()} ммоль/л",
            "HbA1c (GMI)" to "≈ ${r.gmi.fmt()}%",
            "Вариабельность" to (r.cv?.let { "${it.fmt()}%" } ?: "—"),
            "Эпизоды низкого" to "${r.lowEpisodes}",
        )
        val colW = (595 - 2 * pad) / items.size
        items.forEachIndexed { i, (label, value) ->
            c.drawText(label, pad + i * colW, y, paint(10f, gray))
            c.drawText(value, pad + i * colW, y + 20, paint(15f, bold = true))
        }

        // Время в диапазоне
        y += 60
        c.drawText("Время в диапазоне ${low.fmt()}–${high.fmt()} ммоль/л", pad, y, paint(13f, bold = true))
        y += 12
        val barW = 595 - 2 * pad
        var x = pad
        listOf(r.stats.below to ChartPainter.LOW_COLOR, r.stats.inRange to ChartPainter.IN_RANGE_COLOR, r.stats.above to ChartPainter.HIGH_COLOR)
            .forEach { (share, color) ->
                val w = barW * share
                if (w > 0) c.drawRect(x, y, x + w, y + 16, Paint().apply { this.color = color })
                x += w
            }
        y += 34
        c.drawText(
            "Ниже: ${percent(r.stats.below)} (${durationText(r.belowMinutes)})    " +
                "В диапазоне: ${percent(r.stats.inRange)}    " +
                "Выше: ${percent(r.stats.above)} (${durationText(r.aboveMinutes)})",
            pad, y, paint(11f),
        )
        y += 16
        c.drawText(
            "Мин ${r.stats.min.fmt()} · макс ${r.stats.max.fmt()} ммоль/л · цели: в диапазоне > 70%, ниже < 4%, вариабельность ≤ 36%",
            pad, y, paint(10f, gray),
        )

        // Средний день
        y += 40
        c.drawText("Средний день (AGP)", pad, y, paint(13f, bold = true))
        y += 8
        AgpPainter.draw(c, RectF(pad, y, 595 - pad, y + 300), r.bands, low, high, scale = 1f, textColor = gray, gridColor = 0x22000000)
        y += 318
        c.drawText(
            "Линия — медиана, тёмная полоса — 25–75% значений, светлая — 10–90%. Пунктир — пороги ${low.fmt()} и ${high.fmt()}.",
            pad, y, paint(9f, gray),
        )
        c.drawText("Источник: LibreLinkUp. Приложение «Сахар».", pad, 842 - 30f, paint(9f, gray))
    }
}
