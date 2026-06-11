package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.DAYS_AFTER_TILFELLE_START
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

val DAYS_AFTER_TILFELLE_END = 16L

class ModiaAOOversendingCronjob(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val kandidatRepository: SykmeldtUtenArbeidsgiverKandidatRepository,
    private val sendEnabled: Boolean = false,
    override val initialDelayMinutes: Long = 11,
    override val intervalDelayMinutes: Long = 60,
) : Cronjob {
    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed ModiaAO oversending job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob() = CronjobResult().also { result ->
        val kandidater = kandidatRepository.getKandidaterForProcessing()
        kandidater.forEach { kandidat ->
            try {
                val oppfolgingstilfellePerson = oppfolgingstilfelleService.getOppfolgingstilfellePerson(
                    personIdent = kandidat.personident
                )?.toOppfolgingstilfellePersonDTO()
                val latestTilfelle = oppfolgingstilfellePerson?.oppfolgingstilfelleList?.lastOrNull()

                val today = LocalDate.now(ZoneId.of("Europe/Oslo"))

                when {
                    oppfolgingstilfellePerson == null || latestTilfelle == null ||
                        oppfolgingstilfellePerson.dodsdato != null ||
                        latestTilfelle.end.plusDays(DAYS_AFTER_TILFELLE_END).isBefore(today) -> {
                        kandidatRepository.markerFerdig(kandidat.uuid)
                    }

                    latestTilfelle.end.isBefore(today) -> {
                        val nextProcessingAt = today
                            .plusDays(1)
                            .atStartOfDay(ZoneId.of("Europe/Oslo"))
                            .toOffsetDateTime()
                        kandidatRepository.markerUtsatt(kandidat.uuid, nextProcessingAt)
                    }

                    latestTilfelle.start.plusDays(DAYS_AFTER_TILFELLE_START).isAfter(today) -> {
                        val nextProcessingAt = latestTilfelle.start
                            .plusDays(DAYS_AFTER_TILFELLE_START)
                            .atStartOfDay(ZoneId.of("Europe/Oslo"))
                            .toOffsetDateTime()
                        kandidatRepository.markerUtsatt(kandidat.uuid, nextProcessingAt)
                    }

                    !latestTilfelle.arbeidstakerAtTilfelleEnd -> {
                        if (sendEnabled) {
                            // TODO: Send kandidat til Modia/AO
                        }
                        kandidatRepository.markerOversendt(kandidat.uuid)
                    }

                    else -> {
                        kandidatRepository.markerFerdig(kandidat.uuid)
                        log.info(
                            "Kandidat ferdigstilles fordi siste oppfolgingstilfelle har arbeidsgiver, {}, {}",
                            StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                            StructuredArguments.keyValue("oppfolgingstilfellePersonUuid", oppfolgingstilfellePerson.uuid),
                        )
                    }
                }
                result.updated++
            } catch (exc: Exception) {
                log.error("Feil ved behandling av kandidat ${kandidat.uuid}", exc)
                result.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ModiaAOOversendingCronjob::class.java)
    }
}
