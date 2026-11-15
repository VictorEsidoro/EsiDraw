package io.github.victoresidoro.esidraw.domain.message

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Valores em reais digitados pelo usuário ⇄ centavos. */
object Money {

    /**
     * Aceita "100", "100,5", "100.50", "1.000,00" e "R$ 1.234,56". Devolve `null` para texto vazio ou
     * inválido e para valores negativos.
     */
    fun parseCents(text: String): Long? {
        var s = text.replace("R$", "").filter { !it.isWhitespace() }
        if (s.isEmpty()) return null
        s = when {
            ',' in s -> s.replace(".", "").replace(',', '.') // 1.234,56 → 1234.56
            // Só pontos: "1.234" (milhar) ou "100.50" (decimal)?
            s.count { it == '.' } > 1 -> s.replace(".", "")
            s.substringAfter('.', "").length == 3 -> s.replace(".", "")
            else -> s
        }
        val value = runCatching { BigDecimal(s) }.getOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Texto para preencher o campo de edição: 10000 → "100,00". */
    fun toInput(cents: Long): String = String.format(Locale.ROOT, "%d,%02d", cents / 100, cents % 100)
}
