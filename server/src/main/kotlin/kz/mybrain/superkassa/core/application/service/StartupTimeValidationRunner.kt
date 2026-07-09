package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.time.SystemTimeStartupValidationException
import kz.mybrain.superkassa.core.application.time.ValidateSystemTimeOnStartupUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class StartupTimeValidationRunner(
    private val validateSystemTimeOnStartupUseCase: ValidateSystemTimeOnStartupUseCase
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(StartupTimeValidationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        try {
            validateSystemTimeOnStartupUseCase.execute()
            logger.info("System time validation passed on startup")
        } catch (e: SystemTimeStartupValidationException) {
            logger.error(
                "System time validation failed on startup: reason={}",
                e.validationResult.reason,
                e
            )
            throw e
        }
    }
}
