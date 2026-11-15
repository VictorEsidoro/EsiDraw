package io.github.victoresidoro.esidraw.data.vcard

import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import java.nio.charset.Charset

data class VCardPhone(
    val number: String,
    /** `waid=` do WhatsApp: o número já no formato internacional, quando presente. */
    val waid: String?,
    /** Tipos em maiúsculas (CELL, VOICE, HOME...). */
    val types: Set<String>,
)

data class VCardContact(
    val name: String,
    val phones: List<VCardPhone>,
)

/** Contato pronto para virar participante; [phone] é `null` quando nenhum número é utilizável. */
data class ImportedContact(
    val name: String,
    val phone: String?,
)

/**
 * Leitor de vCard (.vcf) para o que o WhatsApp e a agenda do Android compartilham: vCard 2.1, 3.0 e
 * 4.0, vários contatos num arquivo, linhas dobradas, quoted-printable e o parâmetro `waid` do
 * WhatsApp. Só entende o necessário (FN, N, ORG e TEL); o resto é ignorado.
 */
object VCardParser {

    fun parse(text: String): List<VCardContact> {
        val contacts = mutableListOf<VCardContact>()
        var name: String? = null
        var structuredName: String? = null
        var org: String? = null
        var phones = mutableListOf<VCardPhone>()
        var inCard = false

        for (line in unfold(text.removePrefix("﻿"))) {
            val upper = line.trim().uppercase()
            when {
                upper == "BEGIN:VCARD" -> {
                    inCard = true
                    name = null; structuredName = null; org = null
                    phones = mutableListOf()
                }
                upper == "END:VCARD" -> {
                    if (inCard) {
                        val finalName = name?.takeIf { it.isNotBlank() }
                            ?: structuredName?.takeIf { it.isNotBlank() }
                            ?: org.orEmpty()
                        contacts += VCardContact(finalName.trim(), phones.toList())
                    }
                    inCard = false
                }
                inCard -> {
                    val property = parseProperty(line) ?: continue
                    when (property.name) {
                        "FN" -> if (name == null) name = unescape(property.value)
                        "N" -> if (structuredName == null) structuredName = structuredName(property.value)
                        "ORG" -> if (org == null) org = splitUnescaped(property.value).firstOrNull()?.let(::unescape)
                        "TEL" -> {
                            val number = property.value.trim().removePrefix("tel:").trim()
                            if (number.isNotEmpty()) {
                                phones += VCardPhone(
                                    number = number,
                                    waid = property.params["WAID"]?.firstOrNull(),
                                    types = property.types,
                                )
                            }
                        }
                    }
                }
            }
        }
        return contacts
    }

    /**
     * Escolhe o melhor telefone: o `waid` do WhatsApp primeiro, depois celulares, depois o resto.
     * Só conta número que o [PhoneNumber] aceita (com DDD).
     */
    fun VCardContact.toImported(): ImportedContact {
        val ranked = phones.sortedBy {
            when {
                it.waid != null -> 0
                "CELL" in it.types || "MOBILE" in it.types -> 1
                else -> 2
            }
        }
        val phone = ranked.firstNotNullOfOrNull { PhoneNumber.normalize(it.number, it.waid) }
        return ImportedContact(name, phone)
    }

    // ---------------------------------------------------------------------------------------------

    private class Property(
        val name: String,
        val params: Map<String, List<String>>,
        val types: Set<String>,
        val value: String,
    )

    /** Junta linhas dobradas (continuam com espaço/tab) e quebras suaves do quoted-printable (`=`). */
    private fun unfold(text: String): List<String> {
        val logical = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val last = logical.lastOrNull()
            when {
                last != null && (raw.startsWith(" ") || raw.startsWith("\t")) ->
                    logical[logical.lastIndex] = last + raw.substring(1)
                last != null && last.endsWith("=") && isQuotedPrintable(last) ->
                    logical[logical.lastIndex] = last.dropLast(1) + raw
                else -> logical += raw
            }
        }
        return logical
    }

    private fun isQuotedPrintable(line: String) =
        line.substringBefore(':').uppercase().contains("QUOTED-PRINTABLE")

    private fun parseProperty(line: String): Property? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        var value = line.substring(colon + 1)

        val parts = head.split(';')
        // "item1.TEL" → grupo "item1" é ignorado.
        val name = parts[0].substringAfterLast('.').uppercase()
        val params = mutableMapOf<String, List<String>>()
        val types = mutableSetOf<String>()
        for (part in parts.drop(1)) {
            val eq = part.indexOf('=')
            if (eq < 0) { // vCard 2.1: tipo solto, ex.: TEL;CELL;VOICE
                types += part.trim().uppercase()
                continue
            }
            val key = part.substring(0, eq).trim().uppercase()
            val values = part.substring(eq + 1).split(',').map { it.trim().trim('"') }
            params[key] = values
            if (key == "TYPE") types += values.map { it.uppercase() }
        }

        if (params["ENCODING"]?.any { it.equals("QUOTED-PRINTABLE", ignoreCase = true) } == true) {
            value = decodeQuotedPrintable(value, params["CHARSET"]?.firstOrNull())
        }
        return Property(name, params, types, value)
    }

    private fun decodeQuotedPrintable(value: String, charsetName: String?): String {
        val charset = charsetName
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: Charsets.UTF_8
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 < value.length && isHex(value[i + 1]) && isHex(value[i + 2])) {
                out.write(value.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.toString().toByteArray(charset))
                i++
            }
        }
        return String(out.toByteArray(), charset)
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** N = sobrenome;nome;nomes do meio;prefixo;sufixo → "nome meio sobrenome". */
    private fun structuredName(value: String): String {
        val parts = splitUnescaped(value).map(::unescape)
        fun part(i: Int) = parts.getOrNull(i).orEmpty().trim()
        return listOf(part(1), part(2), part(0)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Separa por `;` ignorando os escapados (`\;`). */
    private fun splitUnescaped(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(c).append(value[i + 1])
                i += 2
            } else if (c == ';') {
                parts += current.toString()
                current.clear()
                i++
            } else {
                current.append(c)
                i++
            }
        }
        parts += current.toString()
        return parts
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append(' ')
                    else -> out.append(next) // \, \; \\
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().trim()
    }
}
