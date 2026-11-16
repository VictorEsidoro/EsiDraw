package io.github.victoresidoro.esidraw.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.victoresidoro.esidraw.data.sheet.XlsxTemplate
import java.io.File

/** Gera o modelo e abre a lista de apps (WhatsApp, e-mail, Drive...) para enviá-lo. */
fun shareTemplate(context: Context): Boolean = runCatching {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, XlsxTemplate.FILE_NAME).apply { writeBytes(XlsxTemplate.build()) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = XlsxTemplate.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(XlsxTemplate.FILE_NAME, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Enviar modelo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess
