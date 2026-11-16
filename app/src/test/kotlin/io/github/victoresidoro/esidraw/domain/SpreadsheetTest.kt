package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.data.sheet.SpreadsheetException
import io.github.victoresidoro.esidraw.data.sheet.SpreadsheetParser
import io.github.victoresidoro.esidraw.data.sheet.XlsxTemplate
import io.github.victoresidoro.esidraw.domain.phone.ListEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetTest {

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/sheets/$name")) { "faltou $name" }.readBytes()

    @Test
    fun `xlsx com cabecalho, texto, numero e celula vazia`() {
        val entries = SpreadsheetParser.parse(resource("com-cabecalho.xlsx"))
        assertEquals(
            listOf(
                ListEntry("Ana Teste", "5531999990001"),
                ListEntry("Bruno Teste", "5531999990002"),
                ListEntry("Diego Teste", null), // sem DDD
                ListEntry("Eva Teste", "5531999990005"),
                ListEntry("Fábio Açaí", "5531999999006"), // 10 dígitos: celular antigo ganha o 9
            ),
            entries,
        )
    }

    @Test
    fun `xlsx sem cabecalho com telefone na primeira coluna`() {
        val entries = SpreadsheetParser.parse(resource("sem-cabecalho.xlsx"))
        // Sem cabeçalho a 1ª coluna é o nome: aqui o "nome" é o número, e o telefone é "Gabi Teste" (inválido).
        assertEquals(1, entries.size)
        assertEquals(null, entries[0].phone)
    }

    @Test
    fun `modelo vazio do app e lido como lista vazia`() {
        assertTrue(SpreadsheetParser.parse(XlsxTemplate.build()).isEmpty())
    }

    @Test
    fun `csv com ponto e virgula e acentos em UTF-8 com BOM`() {
        val csv = "﻿Nome;Telefone\nJoão Teste;(31) 99999-0001\n\"Maria; Teste\";31 99999-0002\n"
        val entries = SpreadsheetParser.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(
            listOf(ListEntry("João Teste", "5531999990001"), ListEntry("Maria; Teste", "5531999990002")),
            entries,
        )
    }

    @Test
    fun `csv Latin-1 com virgula e colunas invertidas pelo cabecalho`() {
        val csv = "Telefone,Nome\n31999990003,José Teste\n"
        val entries = SpreadsheetParser.parse(csv.toByteArray(Charsets.ISO_8859_1))
        assertEquals(listOf(ListEntry("José Teste", "5531999990003")), entries)
    }

    @Test
    fun `csv sem cabecalho usa nome e telefone`() {
        val entries = SpreadsheetParser.parse("Ana Teste;31999990001\nBia Teste;31999990002".toByteArray())
        assertEquals(2, entries.size)
        assertEquals("5531999990002", entries[1].phone)
    }

    @Test(expected = SpreadsheetException::class)
    fun `zip que nao e planilha da erro claro`() {
        val zip = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("qualquer.txt")); z.write(1); z.closeEntry()
            }
        }.toByteArray()
        SpreadsheetParser.parse(zip)
    }
}
