package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.phone.ListEntry
import io.github.victoresidoro.esidraw.domain.phone.TextListParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TextListParserTest {

    @Test
    fun `nome seguido de telefone com formatos variados`() {
        val result = TextListParser.parse(
            """
            Maria Silva - (31) 98888-7777
            João 31988887777
            Ana, +55 31 97777-6666
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ListEntry("Maria Silva", "5531988887777"),
                ListEntry("João", "5531988887777"),
                ListEntry("Ana", "5531977776666"),
            ),
            result,
        )
    }

    @Test
    fun `telefone antes do nome e marcadores de lista`() {
        val result = TextListParser.parse("1. (31) 98888-7777 Carlos\n- 31 97777-6666: Bia\n• Dudu 31 95555-4444")
        assertEquals(
            listOf(
                ListEntry("Carlos", "5531988887777"),
                ListEntry("Bia", "5531977776666"),
                ListEntry("Dudu", "5531955554444"),
            ),
            result,
        )
    }

    @Test
    fun `linha sem telefone valido volta com phone nulo e linhas vazias somem`() {
        val result = TextListParser.parse("\nMarcos\n\nLuiza 98888-7777\n   \n")
        assertEquals(listOf(ListEntry("Marcos", null), ListEntry("Luiza", null)), result)
    }

    @Test
    fun `texto vazio nao gera entradas`() {
        assertEquals(emptyList<ListEntry>(), TextListParser.parse("  \n \n"))
    }
}
