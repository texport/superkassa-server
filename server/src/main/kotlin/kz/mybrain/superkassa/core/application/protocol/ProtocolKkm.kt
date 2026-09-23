package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdServiceInfo
import kotlinx.serialization.json.JsonObject

/**
 * Касса документа: реквизиты из пакета, оформление — этой машины.
 *
 * Документ мог быть пробит на другой кассе, и печатать его под чужими
 * реквизитами нельзя: регистрационный номер КГД, БИН и адрес обязаны
 * остаться теми, под которыми документ ушёл в ОФД. А вот оформление —
 * язык, ширина ленты, логотип и цвет — берётся у машины, которая сейчас
 * рисует: владелец просил один вид документа, а не два.
 *
 * @param service служебный блок запроса с реквизитами кассы и налогоплательщика.
 * @return копия сведений о кассе с реквизитами документа.
 */
internal fun KkmInfo.withProtocolRegistration(service: JsonObject?): KkmInfo {
    val registration = service?.child("regInfo") ?: return this
    val device = registration.child("kkm")
    val owner = registration.child("org")
    return copy(
        id = device?.text("kkmId") ?: id,
        registrationNumber = device?.text(REGISTRATION_NUMBER) ?: registrationNumber,
        factoryNumber = device?.text("serialNumber") ?: factoryNumber,
        ofdServiceInfo = owner?.let { taxpayerOf(it) } ?: ofdServiceInfo
    )
}

/** Налогоплательщик документа: то, что напечатано в шапке чека. */
private fun KkmInfo.taxpayerOf(owner: JsonObject): OfdServiceInfo {
    val known = ofdServiceInfo
    return OfdServiceInfo(
        orgTitle = owner.text("title") ?: known?.orgTitle.orEmpty(),
        orgAddress = owner.text("address") ?: known?.orgAddress.orEmpty(),
        orgAddressKz = owner.text("addressKz") ?: known?.orgAddressKz.orEmpty(),
        orgIinOrBin = owner.text("iin") ?: known?.orgIinOrBin.orEmpty(),
        orgOked = owner.text("oked") ?: known?.orgOked.orEmpty(),
        geoLatitude = known?.geoLatitude ?: 0,
        geoLongitude = known?.geoLongitude ?: 0,
        geoSource = known?.geoSource.orEmpty()
    )
}

/**
 * Поле регистрационного номера кассы в протоколе.
 *
 * Имя поля досталось протоколу от чужой юрисдикции; номер в нём —
 * регистрационный номер, который выдаёт КГД. Имя пишется здесь один раз
 * и дальше по коду не расходится.
 */
private const val REGISTRATION_NUMBER = "fnsKkmId"
