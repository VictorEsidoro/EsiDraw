package io.github.victoresidoro.esidraw.data.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber

/** Lê nome e telefone do contato escolhido no seletor da agenda do Android. */
object ContactsReader {

    fun read(resolver: ContentResolver, contactUri: Uri): ImportedContact? {
        val contactId = contactUri.lastPathSegment?.toLongOrNull() ?: return null
        val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.NORMALIZED_NUMBER, Phone.TYPE)

        data class Row(val name: String, val phone: String?, val mobile: Boolean)

        val rows = mutableListOf<Row>()
        resolver.query(
            Phone.CONTENT_URI,
            projection,
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            val normalizedCol = cursor.getColumnIndexOrThrow(Phone.NORMALIZED_NUMBER)
            val typeCol = cursor.getColumnIndexOrThrow(Phone.TYPE)
            while (cursor.moveToNext()) {
                val phone = PhoneNumber.normalize(cursor.getString(numberCol))
                    ?: PhoneNumber.normalize(cursor.getString(normalizedCol))
                rows += Row(
                    name = cursor.getString(nameCol).orEmpty(),
                    phone = phone,
                    mobile = cursor.getInt(typeCol) == Phone.TYPE_MOBILE,
                )
            }
        }
        if (rows.isEmpty()) return null

        val best = rows.filter { it.phone != null }.sortedByDescending { it.mobile }.firstOrNull()
        return ImportedContact(name = (best ?: rows.first()).name, phone = best?.phone)
    }
}
