package io.github.victoresidoro.esidraw.domain.phone

/**
 * Telefones no formato que o WhatsApp espera em `?phone=`: só dígitos, com código do país
 * (ex.: 5531988887777). Foco no Brasil; números internacionais explícitos (+...) passam como estão.
 */
object PhoneNumber {

    /**
     * Normaliza [raw] (com espaços, parênteses, hífens, "+55", zero na frente...) ou, se houver,
     * o [waid] do vCard do WhatsApp (que já vem no formato certo). Devolve `null` se não parecer um
     * telefone válido — em especial, números sem DDD.
     */
    fun normalize(raw: String?, waid: String? = null): String? {
        waid?.let { digitsOnly(it) }
            ?.takeIf { it.length in 8..15 }
            ?.let { id ->
                return if (id.startsWith(BR)) brazilian(id.drop(BR.length)) else id
            }
        if (raw == null) return null

        val trimmed = raw.trim()
        var digits = digitsOnly(trimmed)
        val international = trimmed.startsWith("+") || digits.startsWith("00")
        if (digits.startsWith("00")) digits = digits.drop(2)

        if (international) {
            return if (digits.startsWith(BR)) brazilian(digits.drop(BR.length)) else digits.takeIf { it.length in 8..15 }
        }

        // Prefixo de tronco "0" (ex.: 031 98888-7777).
        digits = digits.trimStart('0')
        return when (digits.length) {
            10, 11 -> brazilian(digits)
            12, 13 -> if (digits.startsWith(BR)) brazilian(digits.drop(BR.length)) else null
            else -> null
        }
    }

    /** `+55 (31) 98888-7777`; formatos desconhecidos viram `+<dígitos>`. */
    fun format(normalized: String): String {
        if (normalized.startsWith(BR) && normalized.length in 12..13) {
            val national = normalized.drop(BR.length)
            val ddd = national.take(2)
            val subscriber = national.drop(2)
            val split = subscriber.length - 4
            return "+55 ($ddd) ${subscriber.take(split)}-${subscriber.drop(split)}"
        }
        return "+$normalized"
    }

    /** Número nacional (DDD + assinante) → com 55; `null` se o DDD ou o tamanho não fizerem sentido. */
    private fun brazilian(national: String): String? {
        if (national.length !in 10..11) return null
        val ddd = national.take(2).toIntOrNull() ?: return null
        if (ddd !in VALID_DDD) return null
        if (national.length == 11 && national[2] != '9') return null // celular tem 9 na frente
        return BR + national
    }

    private fun digitsOnly(text: String) = text.filter { it.isDigit() }

    private const val BR = "55"

    /** DDDs existentes no Brasil. */
    private val VALID_DDD = setOf(
        11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 27, 28,
        31, 32, 33, 34, 35, 37, 38, 41, 42, 43, 44, 45, 46, 47, 48, 49,
        51, 53, 54, 55, 61, 62, 63, 64, 65, 66, 67, 68, 69,
        71, 73, 74, 75, 77, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89,
        91, 92, 93, 94, 95, 96, 97, 98, 99,
    )
}
