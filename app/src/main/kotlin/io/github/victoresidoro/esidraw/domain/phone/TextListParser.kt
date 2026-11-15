package io.github.victoresidoro.esidraw.domain.phone

/** Uma linha de uma lista colada em texto. [phone] é `null` quando não havia telefone válido com DDD. */
data class ListEntry(val name: String, val phone: String?)

/**
 * Lê listas coladas como "Maria Silva - (31) 98888-7777" ou "João 31988887777", uma pessoa por linha.
 * Aceita marcadores ("-", "•", "1.", "2)") no começo da linha e o telefone antes ou depois do nome.
 */
object TextListParser {

    private val bullet = Regex("""^\s*(?:[-•*·]|\d{1,3}[.)])\s+""")
    private val phoneCandidate = Regex("""\+?\(?\d[\d\s().\-]{6,}\d""")
    private const val SEPARATORS = " \t-–—:;,|"

    fun parse(text: String): List<ListEntry> =
        text.lineSequence()
            .map { it.replace(bullet, "").trim() }
            .filter { it.isNotEmpty() }
            .map(::parseLine)
            .toList()

    private fun parseLine(line: String): ListEntry {
        val match = phoneCandidate.find(line)
        val phone = match?.let { PhoneNumber.normalize(it.value) }
        val rest = if (match == null) line else line.removeRange(match.range)
        val name = rest.trim { it in SEPARATORS }.replace(Regex("""\s+"""), " ")
        return ListEntry(name = name, phone = phone)
    }
}
