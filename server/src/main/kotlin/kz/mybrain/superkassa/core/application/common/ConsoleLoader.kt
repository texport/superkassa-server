package kz.mybrain.superkassa.core.application.common

import kz.mybrain.superkassa.core.domain.model.kkm.KkmState
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Лоадер консоли при успешном запуске приложения.
 * Выводит красивую сводную статистику о состоянии сервера,
 * активных профилях, подключенной базе данных и доступных сетевых путях API.
 */
@Component
class ConsoleLoader(
    private val env: Environment,
    private val storage: StoragePort? = null,
    private val coreSettings: CoreSettings? = null
) : ApplicationListener<ApplicationReadyEvent> {

    private val log = LoggerFactory.getLogger(ConsoleLoader::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val serverPort = env.getProperty("server.port")
        val contextPath = env.getProperty("server.servlet.context-path") ?: "/"
        val protocol = "http"
        val hostAddress =
            try {
                InetAddress.getLocalHost().hostAddress
            } catch (e: Exception) {
                "localhost"
            }

        val banner =
            """
                               __                        
   _______  ______  ___  _____/ /______ _______________ _
  / ___/ / / / __ \/ _ \/ ___/ //_/ __ `/ ___/ ___/ __ `/
 (__  ) /_/ / /_/ /  __/ /  / ,< / /_/ (__  |__  ) /_/ / 
/____/\__,_/ .___/\___/_/  /_/|_|\__,_/____/____/\__,_/  
          /_/                                            
        """

        println(ANSI_CYAN + banner + ANSI_RESET)

        val stats = gatherStartupStats()
        val startedAt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val javaVersion = System.getProperty("java.version") ?: "—"
        val timezone = ZoneId.systemDefault().id
        val profiles = env.getProperty("spring.profiles.active")?.takeIf { it.isNotBlank() } ?: "default"
        val storageEngine = coreSettings?.storage?.engine ?: "—"
        val ofdVersion = coreSettings?.ofdProtocolVersion ?: "—"
        val deliverySummary = formatDeliverySummary()
        val (loggingLevel, loggingImpl) = getLoggingInfo()

        val lines = mutableListOf<String>()
        lines.add(" Application started successfully! ")
        lines.add(" Status:          " + ANSI_BOLD + "Running" + ANSI_RESET)
        lines.add(" --------------------------------------------------")
        lines.add(" Started:         $startedAt")
        lines.add(" JVM:             Java $javaVersion | TZ: $timezone")
        lines.add(" Active Profiles: $profiles")
        lines.add(" Logging:         $loggingLevel ($loggingImpl)")
        lines.add(" --------------------------------------------------")
        lines.add(" Database:        ${stats.dbStatus} ($storageEngine)")
        lines.add(" --------------------------------------------------")
        lines.add(" Total KKMs:      ${stats.kkmTotal} (${stats.kkmByStatus})")
        lines.add(" Open Shifts:     ${stats.openShiftsStr}")
        lines.add(" Total Receipts:  ${stats.receiptsTotal}")
        lines.add(" Closed Shifts:   ${stats.closedShiftsTotal} (Z-reports)")
        lines.add(" Offline Queue:   ${stats.offlineQueueTotal} tx")
        lines.add(" --------------------------------------------------")
        lines.add(" OFD Protocol:    $ofdVersion")
        lines.add(deliverySummary)
        if (coreSettings != null) {
            lines.add(" Running Mode:    ${coreSettings.mode.name}")
            lines.add(" Node:            ${coreSettings.nodeId}")
        }
        val swaggerPath = env.getProperty("springdoc.swagger-ui.path") ?: "/swagger"
        val apiDocsPath = env.getProperty("springdoc.api-docs.path") ?: "/v3/api-docs"

        val plainLengths = lines.map { visibleLength(it) }
        val innerWidth = maxOf(MIN_BOX_WIDTH, plainLengths.maxOrNull() ?: MIN_BOX_WIDTH)

        fun border(corner: Char) = ANSI_GREEN + corner + "-".repeat(innerWidth) + corner + ANSI_RESET
        fun row(content: String, plainLen: Int) =
            ANSI_GREEN + "|" + ANSI_RESET +
                content + " ".repeat((innerWidth - plainLen).coerceAtLeast(0)) +
                ANSI_GREEN + "|" + ANSI_RESET

        println(border('+'))
        println(row(lines[0], plainLengths[0]))
        println(border('+'))
        for (i in 1 until lines.size) {
            println(row(lines[i], plainLengths[i]))
        }
        println(border('+'))

        println()
        println("  Local:        $protocol://localhost:$serverPort$contextPath")
        println("  Network:      $protocol://$hostAddress:$serverPort$contextPath")
        println("  Swagger UI:   $protocol://localhost:$serverPort$swaggerPath")
        println("  API Docs:     $protocol://localhost:$serverPort$apiDocsPath")
        println()
    }

    private fun formatDeliverySummary(): String {
        val d = coreSettings?.delivery ?: return " Delivery:        not configured"
        val printOn = d.print?.enabled == true
        val channelCount = d.channels.count { it.enabled }
        val parts = mutableListOf<String>()
        if (printOn) parts.add("print")
        if (channelCount > 0) parts.add("channels: $channelCount")
        return " Delivery:        " + if (parts.isEmpty()) "off" else parts.joinToString(", ")
    }

    private fun gatherStartupStats(): StartupStats {
        if (storage == null) {
            return StartupStats(
                dbStatus = "not checked (storage not connected)",
                kkmTotal = "—",
                kkmByStatus = "—",
                openShiftsStr = "—",
                receiptsTotal = "—",
                closedShiftsTotal = "—",
                offlineQueueTotal = "—"
            )
        }
        return try {
            val total = storage.countKkms(state = null, search = null)
            val byStatus = KkmState.entries.joinToString(", ") { state ->
                val shortState = when (state) {
                    KkmState.ACTIVE -> "ACT"
                    KkmState.PROGRAMMING -> "PROG"
                    KkmState.BLOCKED -> "BLK"
                    else -> state.name
                }
                "$shortState:${storage.countKkms(state = state.name, search = null)}"
            }
            val openShiftsCount = countOpenShifts(storage, total)
            val openShiftsStr = if (openShiftsCount >= 0) openShiftsCount.toString() else "—"
            val receipts = storage.countFiscalDocuments("CHECK")
            val closedShifts = storage.countClosedShifts()
            val offlineQueue = storage.countOfflineQueue()
            StartupStats(
                dbStatus = "connected",
                kkmTotal = total.toString(),
                kkmByStatus = byStatus,
                openShiftsStr = openShiftsStr,
                receiptsTotal = receipts.toString(),
                closedShiftsTotal = closedShifts.toString(),
                offlineQueueTotal = offlineQueue.toString()
            )
        } catch (e: Exception) {
            log.warn("Startup stats failed", e)
            StartupStats(
                dbStatus = "verification error",
                kkmTotal = "—",
                kkmByStatus = "—",
                openShiftsStr = "—",
                receiptsTotal = "—",
                closedShiftsTotal = "—",
                offlineQueueTotal = "—"
            )
        }
    }

    private fun getLoggingInfo(): Pair<String, String> {
        val envLevel = env.getProperty("logging.level.root")
            ?: env.getProperty("logging.level.kz.mybrain")
            ?: "INFO"

        val level = try {
            val context = LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)?.level?.toString()
        } catch (_: Throwable) {
            null
        } ?: envLevel

        val impl = try {
            val simpleName = LoggerFactory.getILoggerFactory().javaClass.simpleName
            if (simpleName == "LoggerContext") "Logback" else simpleName.removeSuffix("LoggerFactory")
        } catch (_: Exception) {
            "—"
        }
        return level to impl
    }

    private fun countOpenShifts(storage: StoragePort, kkmLimit: Int): Int {
        if (kkmLimit <= 0) return 0
        return try {
            val kkms = storage.listKkms(limit = minOf(kkmLimit, 500), offset = 0, state = null, search = null)
            kkms.count { storage.findOpenShift(it.id) != null }
        } catch (e: Exception) {
            log.debug("Open shifts count failed", e)
            -1
        }
    }

    /** Длина строки без ANSI-кодов (для выравнивания рамки). */
    private fun visibleLength(s: String): Int = stripAnsi(s).length

    /** Удаляет все ANSI CSI-последовательности (например \u001B[1m, \u001B[0;32m). */
    private fun stripAnsi(s: String): String = s.replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "")

    private data class StartupStats(
        val dbStatus: String,
        val kkmTotal: String,
        val kkmByStatus: String,
        val openShiftsStr: String,
        val receiptsTotal: String,
        val closedShiftsTotal: String,
        val offlineQueueTotal: String
    )

    companion object {
        private const val MIN_BOX_WIDTH = 56

        const val ANSI_RESET = "\u001B[0m"
        const val ANSI_GREEN = "\u001B[32m"
        const val ANSI_CYAN = "\u001B[36m"
        const val ANSI_BOLD = "\u001B[1m"
    }
}
