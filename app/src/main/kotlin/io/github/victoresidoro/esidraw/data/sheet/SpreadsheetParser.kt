package io.github.victoresidoro.esidraw.data.sheet

import io.github.victoresidoro.esidraw.domain.phone.ListEntry
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class SpreadsheetException(message: String) : Exception(message)

/**
 * Lê uma lista de nome + telefone de um arquivo .xlsx ou .csv (detecta pelo conteúdo).
 *
 * As colunas são achadas pelo cabeçalho ("Nome", "Telefone"/"Celular"/"WhatsApp"); sem cabeçalho,
 * a primeira coluna é o nome e a segunda o telefone. Só a primeira aba do .xlsx é lida.
 */
object SpreadsheetParser {

    const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_ROWS = 5000

    /** Lê o arquivo inteiro, recusando o que passar do limite de tamanho. */
    fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) throw SpreadsheetException("Arquivo grande demais (máximo de 5 MB).")
        }
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray): List<ListEntry> {
        if (bytes.size > MAX_BYTES) throw SpreadsheetException("Arquivo grande demais (máximo de 5 MB).")
        val rows = if (isZip(bytes)) readXlsx(bytes) else readCsv(bytes)
        return toEntries(rows)
    }

    // --- Linhas -> pessoas -----------------------------------------------------------------------

    internal fun toEntries(rows: List<List<String>>): List<ListEntry> {
        val nonEmpty = rows.filter { row -> row.any { it.isNotBlank() } }
        if (nonEmpty.isEmpty()) return emptyList()

        val header = nonEmpty.first().map(::fold)
        val nameCol = header.indexOfFirst { it in NAME_HEADERS }
        val phoneCol = header.indexOfFirst { it in PHONE_HEADERS }
        val hasHeader = nameCol >= 0 || phoneCol >= 0

        val nameIndex = if (nameCol >= 0) nameCol else if (phoneCol == 0) 1 else 0
        val phoneIndex = if (phoneCol >= 0) phoneCol else if (nameIndex == 0) 1 else 0

        val data = if (hasHeader) nonEmpty.drop(1) else nonEmpty
        return data.take(MAX_ROWS).mapNotNull { row ->
            val name = row.getOrNull(nameIndex).orEmpty().replace(Regex("""\s+"""), " ").trim()
            val rawPhone = row.getOrNull(phoneIndex).orEmpty().trim()
            if (name.isEmpty() && rawPhone.isEmpty()) null else ListEntry(name, PhoneNumber.normalize(rawPhone))
        }
    }

    private val NAME_HEADERS = setOf("nome", "name", "participante", "pessoa")
    private val PHONE_HEADERS = setOf("telefone", "celular", "whatsapp", "fone", "phone", "numero", "tel")

    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase().trim()

    // --- CSV -------------------------------------------------------------------------------------

    private fun isZip(bytes: ByteArray) =
        bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    internal fun readCsv(bytes: ByteArray): List<List<String>> {
        val text = decode(bytes).removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delimiter = listOf(';', ',', '\t').maxBy { d -> firstLine.count { it == d } }
            .takeIf { d -> firstLine.contains(d) } ?: ';'

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); rows.add(row); row = ArrayList() }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> endCell()
                !quoted && c == '\r' -> {}
                !quoted && c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** UTF-8 se for válido; senão Latin-1 (o que o Excel brasileiro costuma salvar). */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charsets.ISO_8859_1)
    }

    // --- XLSX ------------------------------------------------------------------------------------

    internal fun readXlsx(bytes: ByteArray): List<List<String>> {
        var sharedXml: ByteArray? = null
        val sheets = sortedMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    if (++count > 200) throw SpreadsheetException("Arquivo .xlsx inválido.")
                    val name = entry.name
                    val wanted = name == "xl/sharedStrings.xml" ||
                        (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                    if (wanted) {
                        val data = zip.readLimited()
                        if (name == "xl/sharedStrings.xml") sharedXml = data else sheets[name] = data
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: SpreadsheetException) {
            throw e
        } catch (_: Exception) {
            throw SpreadsheetException("Não consegui abrir o arquivo. Use o modelo do EsiDraw (.xlsx) ou um .csv.")
        }
        val sheet = sheets["xl/worksheets/sheet1.xml"] ?: sheets.values.firstOrNull()
            ?: throw SpreadsheetException("Não encontrei nenhuma aba na planilha.")
        val strings = sharedXml?.let(::sharedStrings).orEmpty()
        return sheetRows(sheet, strings)
    }

    private fun ZipInputStream.readLimited(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_ENTRY_BYTES) throw SpreadsheetException("Planilha grande demais.")
        }
        return out.toByteArray()
    }

    private fun parseXml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        // Sem DTD/entidades externas: o arquivo vem de fora.
        for ((feature, value) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) {
            try { factory.setFeature(feature, value) } catch (_: Exception) { /* o parser pode não oferecer */ }
        }
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.children(tag: String): List<Element> {
        val result = ArrayList<Element>()
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && (node.localName ?: node.nodeName).substringAfter(':') == tag) result.add(node)
            node = node.nextSibling
        }
        return result
    }

    private fun Element.descendantText(tag: String): String {
        val all = getElementsByTagName("*")
        val sb = StringBuilder()
        for (i in 0 until all.length) {
            val e = all.item(i) as Element
            if ((e.localName ?: e.nodeName).substringAfter(':') == tag) sb.append(e.textContent)
        }
        return sb.toString()
    }

    private fun sharedStrings(xml: ByteArray): List<String> =
        parseXml(xml).children("si").map { it.descendantText("t") }

    private fun sheetRows(xml: ByteArray, strings: List<String>): List<List<String>> {
        val root = parseXml(xml)
        val data = root.children("sheetData").firstOrNull() ?: return emptyList()
        val rows = ArrayList<List<String>>()
        for (rowEl in data.children("row")) {
            val cells = ArrayList<String>()
            for (c in rowEl.children("c")) {
                val col = columnIndex(c.getAttribute("r"), cells.size)
                while (cells.size < col) cells.add("")
                cells.add(cellValue(c, strings))
            }
            rows.add(cells)
        }
        return rows
    }

    /** "C5" -> 2. Sem referência, usa a próxima posição. */
    private fun columnIndex(ref: String, fallback: Int): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return fallback
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private fun cellValue(c: Element, strings: List<String>): String {
        val type = c.getAttribute("t")
        val v = c.children("v").firstOrNull()?.textContent.orEmpty()
        return when (type) {
            "s" -> strings.getOrNull(v.trim().toIntOrNull() ?: -1).orEmpty()
            "inlineStr" -> c.children("is").firstOrNull()?.descendantText("t").orEmpty()
            "str", "e", "b" -> v
            else -> numeric(v)
        }
    }

    /** Número como o Excel guarda (5.531988887777E12) volta a ser só dígitos. */
    private fun numeric(v: String): String {
        val t = v.trim()
        if (t.isEmpty()) return ""
        return try {
            val d = BigDecimal(t)
            if (d.scale() <= 0 || d.stripTrailingZeros().scale() <= 0) d.toBigInteger().toString() else t
        } catch (_: NumberFormatException) {
            t
        }
    }
}
