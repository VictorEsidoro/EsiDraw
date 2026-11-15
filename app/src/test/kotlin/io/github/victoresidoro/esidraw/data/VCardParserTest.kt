package io.github.victoresidoro.esidraw.data

import io.github.victoresidoro.esidraw.data.vcard.VCardParser
import io.github.victoresidoro.esidraw.data.vcard.VCardParser.toImported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VCardParserTest {

    @Test
    fun `cartao do WhatsApp com waid`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            N:;Maria Souza;;;
            FN:Maria Souza
            TEL;type=CELL;type=VOICE;waid=5531988887777:+55 31 98888-7777
            END:VCARD
        """.trimIndent()

        val contact = VCardParser.parse(text).single()
        assertEquals("Maria Souza", contact.name)
        assertEquals("5531988887777", contact.phones.single().waid)
        assertEquals(ImportedExpected("Maria Souza", "5531988887777"), contact.toImported().let { ImportedExpected(it.name, it.phone) })
    }

    @Test
    fun `usa waid mesmo quando o numero visivel e invalido`() {
        val text = "BEGIN:VCARD\nFN:Joao\nTEL;waid=5521999990000:99990000\nEND:VCARD"
        assertEquals("5521999990000", VCardParser.parse(text).single().toImported().phone)
    }

    @Test
    fun `varios contatos num arquivo e grupo item1 do iOS`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ana
            item1.TEL;type=pref:(31) 99999-1111
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Bruno
            TEL;TYPE=HOME:3133334444
            TEL;TYPE=CELL:031 98888-2222
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(text).map { it.toImported() }
        assertEquals(2, contacts.size)
        assertEquals("5531999991111", contacts[0].phone)
        // Prefere o celular ao fixo.
        assertEquals("5531988882222", contacts[1].phone)
    }

    @Test
    fun `vcard 2_1 com tipos soltos e quoted-printable em UTF-8`() {
        val text = "BEGIN:VCARD\r\nVERSION:2.1\r\n" +
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9 da =\r\nSilva\r\n" +
            "TEL;CELL;VOICE:+5511987654321\r\nEND:VCARD\r\n"

        val contact = VCardParser.parse(text).single()
        assertEquals("José da Silva", contact.name)
        assertEquals("5511987654321", contact.toImported().phone)
    }

    @Test
    fun `linhas dobradas sao juntadas`() {
        val text = "BEGIN:VCARD\nFN:Maria das\n  Gracas\nTEL:+55 (31) 98888-0000\nEND:VCARD"
        assertEquals("Maria das Gracas", VCardParser.parse(text).single().name)
    }

    @Test
    fun `sem FN usa o nome estruturado e depois a organizacao`() {
        val structured = "BEGIN:VCARD\nN:Silva;Carlos;Eduardo;;\nTEL:31988887777\nEND:VCARD"
        assertEquals("Carlos Eduardo Silva", VCardParser.parse(structured).single().name)

        val org = "BEGIN:VCARD\nORG:Padaria Boa\\, Vista;Loja 1\nTEL:31988887777\nEND:VCARD"
        assertEquals("Padaria Boa, Vista", VCardParser.parse(org).single().name)
    }

    @Test
    fun `contato sem telefone utilizavel fica com phone nulo`() {
        val text = "BEGIN:VCARD\nFN:Sem DDD\nTEL:98888-7777\nEND:VCARD\nBEGIN:VCARD\nFN:Sem tel\nEND:VCARD"
        val contacts = VCardParser.parse(text)
        assertEquals(2, contacts.size)
        assertNull(contacts[0].toImported().phone)
        assertNull(contacts[1].toImported().phone)
    }

    @Test
    fun `tel em formato de URI e BOM no inicio`() {
        val text = "﻿BEGIN:VCARD\nFN:Ana\nTEL:tel:+5531988887777\nEND:VCARD"
        assertEquals("5531988887777", VCardParser.parse(text).single().toImported().phone)
    }

    @Test
    fun `texto sem vcard devolve lista vazia`() {
        assertEquals(0, VCardParser.parse("qualquer coisa\nFN:Ninguem").size)
    }

    private data class ImportedExpected(val name: String, val phone: String?)
}
