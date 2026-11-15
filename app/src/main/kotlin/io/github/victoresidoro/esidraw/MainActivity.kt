package io.github.victoresidoro.esidraw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.victoresidoro.esidraw.ui.EsiDrawApp
import io.github.victoresidoro.esidraw.ui.EsiDrawViewModel
import io.github.victoresidoro.esidraw.ui.theme.EsiDrawTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: EsiDrawViewModel by viewModels {
        EsiDrawViewModel.factory((application as EsiDrawApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Só na primeira criação: ao girar a tela o mesmo intent chegaria de novo e duplicaria a importação.
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            EsiDrawTheme { EsiDrawApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Contato compartilhado pelo WhatsApp (ACTION_SEND) ou arquivo .vcf aberto (ACTION_VIEW). */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> return
        }
        val inlineText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.contains("BEGIN:VCARD", ignoreCase = true) }

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { uri?.let(::readVCard) } ?: inlineText
            if (text == null) {
                viewModel.notify("Não consegui ler o contato compartilhado.")
            } else {
                viewModel.importVCard(text)
            }
        }
    }

    /** Lê o .vcf com limite de tamanho: contato é pequeno, e um arquivo enorme não deve travar o app. */
    private fun readVCard(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_VCARD_BYTES) return@runCatching null
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }.getOrNull()

    private companion object {
        const val MAX_VCARD_BYTES = 2 * 1024 * 1024
    }
}
