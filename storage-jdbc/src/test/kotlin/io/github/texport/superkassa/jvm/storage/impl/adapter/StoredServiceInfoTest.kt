package io.github.texport.superkassa.jvm.storage.impl.adapter

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Сведения о налогоплательщике, записанные узлом до переименования полей
 * в БИН/ИИН и ОКЭД, читаются: иначе касса теряет реквизиты на чеке.
 */
class StoredServiceInfoTest {

    @Test
    fun `запись с прежними именами полей читается под новыми`() {
        val stored = """
            {"orgTitle":"ТОО Дала","orgAddress":"Алматы","orgAddressKz":"Алматы",
             "orgInn":"123456789012","orgOkved":"47111",
             "geoLatitude":1,"geoLongitude":2,"geoSource":"MANUAL"}
        """.trimIndent()

        val info = StorageMapper.decodeServiceInfo(stored)

        assertEquals("123456789012" to "47111", info?.orgIinOrBin to info?.orgOked)
    }

    @Test
    fun `новая запись пишется новыми именами и читается обратно`() {
        val stored = checkNotNull(StorageMapper.encodeServiceInfo(StorageMapper.decodeServiceInfo(LEGACY)))

        assertEquals(true, "orgIinOrBin" in stored && "orgInn" !in stored, stored)
        assertEquals("47111", StorageMapper.decodeServiceInfo(stored)?.orgOked)
    }

    private companion object {
        const val LEGACY = """{"orgTitle":"Т","orgAddress":"А","orgAddressKz":"А","orgInn":"123456789012",""" +
            """"orgOkved":"47111","geoLatitude":1,"geoLongitude":2,"geoSource":"MANUAL"}"""
    }
}
