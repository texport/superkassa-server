package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings

/**
 * Настройки, которыми владеет развёртывание, а не сохранённая запись.
 *
 * Какой ОФД обслуживает узел и по какой версии протокола он с ним
 * разговаривает, решает запуск: эти поля приходят свойствами и при старте
 * перекрывают то, что лежит в файле или в базе. Знание об этом объявлено
 * здесь один раз, потому что раньше оно было только в сборке адаптеров:
 * `GET /info` отдавал перекрытое значение 203, а `GET /settings` читал
 * репозиторий заново и отвечал сохранённым 204, хотя обмен всё это время
 * шёл по 2.0.3.
 *
 * @receiver настройки, прочитанные из хранилища.
 * @param deployment настройки запуска узла.
 * @return настройки хранилища с полями запуска.
 */
fun CoreSettings.withDeploymentOwned(deployment: CoreSettings): CoreSettings = copy(
    ofdProviderId = deployment.ofdProviderId,
    ofdProtocolVersion = deployment.ofdProtocolVersion
)
