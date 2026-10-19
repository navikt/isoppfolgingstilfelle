package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import org.slf4j.LoggerFactory
import java.util.*

// Engangsjobb for å rydde opp i data der tilfellebiter og oppfolgingstilfellePerson er lagret på utdatert ident.
// Personidenten som skal håndteres utledes fra de konfigurerte tilfellebit-uuid'ene.
// Jobben er idempotent: når identen er oppdatert, finnes det ikke lenger noe å flytte.
class OutdatedIdentCronjob(
    private val tilfellebitRepository: TilfellebitRepository,
    private val identhendelseService: IdenthendelseService,
    private val uuids : List<UUID> = OUTDATED_IDENT_TILFELLEBIT_UUIDS,
    override val initialDelayMinutes: Long = 5,
    override val intervalDelayMinutes: Long = 60 * 24,
) : Cronjob {

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed outdated ident job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob() = CronjobResult().also { result ->
        val identer = uuids.mapNotNull { uuid ->
            val tilfellebit = tilfellebitRepository.getOppfolgingstilfelleBit(uuid)
            if (tilfellebit == null) {
                log.warn("Fant ikke tilfellebit med uuid $uuid")
            }
            tilfellebit?.personIdentNumber
        }.distinct()

        identer.forEach { ident ->
            try {
                if (identhendelseService.handleOutdatedIdent(ident) > 0) {
                    result.updated++
                }
            } catch (exc: Exception) {
                log.error("Caught exception when handling outdated ident", exc)
                result.failed++
            }
        }
    }

    companion object {
        // Tilfellebiter med utdatert ident: personident som skal håndteres hentes fra denne
        // (trenger bare en tilfellebit per ident som skal håndteres)
        private val OUTDATED_IDENT_TILFELLEBIT_UUIDS: List<UUID> = listOf<String>(
            // "2ff59c11-a3d5-485f-a36d-33d8975381e7", processed 2026-10-19
        ).map { UUID.fromString(it) }

        private val log = LoggerFactory.getLogger(OutdatedIdentCronjob::class.java)
    }
}
