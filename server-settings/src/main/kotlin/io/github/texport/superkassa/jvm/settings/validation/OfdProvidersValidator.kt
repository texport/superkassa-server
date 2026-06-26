package io.github.texport.superkassa.jvm.settings.validation

import io.github.texport.superkassa.jvm.settings.IllegalServerConfigurationException
import kz.mybrain.superkassa.core.application.model.CoreSettings

internal object OfdProvidersValidator {

    fun validateOfdProviders(settings: CoreSettings) {
        val providers = settings.ofdProviders ?: return
        for ((key, config) in providers) {
            if (key.isBlank()) {
                throw IllegalServerConfigurationException(ValidationErrors.OFD_PROVIDER_KEY_BLANK)
            }
            val isInvalid = config.nameRu.isBlank() ||
                config.nameKk.isBlank() ||
                config.website.isBlank() ||
                !isValidUrl(config.website) ||
                config.checkDomain.isBlank()
            if (isInvalid) {
                throw IllegalServerConfigurationException(ValidationErrors.ofdProviderDetailsInvalid(key))
            }
        }
    }
}
