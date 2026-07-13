package kz.mybrain.superkassa.core.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureLeakTest {

    @Test
    fun `controllers must strictly only interact with core presentation layer and never access internal domain data delivery offlinequeue or receiptrenderer packages`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("kz.mybrain.superkassa")

        val rule = noClasses()
            .that()
            .resideInAPackage("kz.mybrain.superkassa.core.application.http.controllers..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.github.texport.superkassa.core.domain..",
                "io.github.texport.superkassa.core.data..",
                "io.github.texport.superkassa.offlinequeue..",
                "io.github.texport.superkassa.delivery..",
                "io.github.texport.superkassa.receiptrenderer.."
            )

        rule.check(importedClasses)
    }

    @Test
    fun `application layer and services must never directly access core domain usecases or internal data and implementation modules`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("kz.mybrain.superkassa")

        val rule = noClasses()
            .that()
            .resideInAPackage("kz.mybrain.superkassa.core.application..")
            .and()
            .resideOutsideOfPackage("kz.mybrain.superkassa.core.application.http.controllers..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.github.texport.superkassa.core.domain.usecase..",
                "io.github.texport.superkassa.core.data..",
                "io.github.texport.superkassa.offlinequeue..",
                "io.github.texport.superkassa.delivery..",
                "io.github.texport.superkassa.receiptrenderer.."
            )

        rule.check(importedClasses)
    }

    @Test
    fun `application layer and services must never depend on CoreSettings from core domain`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("kz.mybrain.superkassa")

        val rule = noClasses()
            .that()
            .resideInAPackage("kz.mybrain.superkassa.core.application..")
            .should()
            .dependOnClassesThat()
            .haveNameMatching(".*CoreSettings")

        rule.check(importedClasses)
    }
}
