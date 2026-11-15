package io.github.victoresidoro.esidraw.data.contacts

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber

/** Um contato da agenda. [phone] é `null` quando nenhum número dele serve (sem DDD, por exemplo). */
data class AgendaContact(
    val id: Long,
    val name: String,
    val phone: String?,
    val groupIds: Set<Long>,
)

/** Um rótulo (grupo) da agenda, como os do Google Contatos. Rótulos com o mesmo nome em contas diferentes viram um só. */
data class AgendaGroup(val title: String, val ids: Set<Long>)

/** Lê a agenda do Android (precisa de READ_CONTACTS). */
object ContactsReader {

    fun listContacts(resolver: ContentResolver): List<AgendaContact> {
        val memberships = readMemberships(resolver)

        class Acc(val id: Long, val name: String) {
            var phone: String? = null
            var phoneIsMobile = false
        }

        val byId = LinkedHashMap<Long, Acc>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.NORMALIZED_NUMBER, Phone.TYPE),
            null,
            null,
            "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            val normalizedCol = cursor.getColumnIndexOrThrow(Phone.NORMALIZED_NUMBER)
            val typeCol = cursor.getColumnIndexOrThrow(Phone.TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val acc = byId.getOrPut(id) { Acc(id, cursor.getString(nameCol).orEmpty()) }
                val phone = PhoneNumber.normalize(cursor.getString(numberCol))
                    ?: PhoneNumber.normalize(cursor.getString(normalizedCol))
                val mobile = cursor.getInt(typeCol) == Phone.TYPE_MOBILE
                // Fica com o primeiro número válido, trocando por um celular se aparecer depois.
                if (phone != null && (acc.phone == null || (mobile && !acc.phoneIsMobile))) {
                    acc.phone = phone
                    acc.phoneIsMobile = mobile
                }
            }
        }
        return byId.values
            .filter { it.name.isNotBlank() }
            .map { AgendaContact(it.id, it.name, it.phone, memberships[it.id].orEmpty()) }
    }

    fun listGroups(resolver: ContentResolver): List<AgendaGroup> {
        val byTitle = sortedMapOf<String, MutableSet<Long>>(String.CASE_INSENSITIVE_ORDER)
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE),
            "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL",
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Groups._ID)
            val titleCol = cursor.getColumnIndexOrThrow(Groups.TITLE)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol)?.trim().orEmpty()
                if (title.isNotEmpty()) byTitle.getOrPut(title) { mutableSetOf() } += cursor.getLong(idCol)
            }
        }
        return byTitle.map { (title, ids) -> AgendaGroup(title, ids) }
    }

    private fun readMemberships(resolver: ContentResolver): Map<Long, Set<Long>> {
        val result = HashMap<Long, MutableSet<Long>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, GroupMembership.GROUP_ROW_ID),
            "${Data.MIMETYPE} = ?",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val contactCol = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
            val groupCol = cursor.getColumnIndexOrThrow(GroupMembership.GROUP_ROW_ID)
            while (cursor.moveToNext()) {
                result.getOrPut(cursor.getLong(contactCol)) { mutableSetOf() } += cursor.getLong(groupCol)
            }
        }
        return result
    }
}
