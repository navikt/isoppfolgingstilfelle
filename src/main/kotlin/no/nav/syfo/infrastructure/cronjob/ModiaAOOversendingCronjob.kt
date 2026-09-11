package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.DAYS_AFTER_TILFELLE_START
import no.nav.syfo.domain.isSykmeldingNy
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.database.bit.toOppfolgingstilfelleBitList
import no.nav.syfo.infrastructure.kafka.StartOppfolgingProducer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

val MINIMUM_NUMBER_OF_DAYS_BETWEEN_TILFELLER = 16L

class ModiaAOOversendingCronjob(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val kandidatRepository: SykmeldtUtenArbeidsgiverKandidatRepository,
    private val tilfellebitRepository: TilfellebitRepository,
    private val startOppfolgingProducer: StartOppfolgingProducer,
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
                )
                val oppfolgingstilfellePersonDto = oppfolgingstilfellePerson?.toOppfolgingstilfellePersonDTO()
                val oppfolgingstilfelleUuid = oppfolgingstilfellePerson?.uuid.toString()
                val latestTilfelle = oppfolgingstilfellePersonDto?.oppfolgingstilfelleList?.firstOrNull()

                val today = LocalDate.now(ZoneId.of("Europe/Oslo"))

                when {
                    oppfolgingstilfellePersonDto == null || latestTilfelle == null ||
                        oppfolgingstilfellePersonDto.dodsdato != null ||
                        latestTilfelle.end.plusDays(MINIMUM_NUMBER_OF_DAYS_BETWEEN_TILFELLER).isBefore(today) -> {
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
                        val nyesteSykmeldingNyBit = tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(
                            personIdentNumber = kandidat.personident,
                        ).toOppfolgingstilfelleBitList().firstOrNull { bit ->
                            bit.isSykmeldingNy() &&
                                bit.fom <= latestTilfelle.end &&
                                bit.tom >= latestTilfelle.start
                        }

                        if (nyesteSykmeldingNyBit?.ufor == true) {
                            kandidatRepository.markerFerdig(kandidat.uuid)
                            log.info(
                                "Kandidat ferdigstilles fordi nyeste SYKMELDING NY-bit i tilfellet har ufor=true, {}, {}",
                                StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                                StructuredArguments.keyValue("oppfolgingstilfellePersonDtoUuid", oppfolgingstilfelleUuid),
                            )
                        } else {
                            if (sendEnabled) {
                                startOppfolgingProducer.sendSykmeldtUtenArbeidsgiverKandidat(
                                    personident = kandidat.personident,
                                )
                            }
                            kandidatRepository.markerOversendt(kandidat.uuid)
                        }
                    }

                    else -> {
                        kandidatRepository.markerFerdig(kandidat.uuid)
                        log.info(
                            "Kandidat ferdigstilles fordi siste oppfolgingstilfelle har arbeidsgiver, {}, {}",
                            StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                            StructuredArguments.keyValue("oppfolgingstilfellePersonDtoUuid", oppfolgingstilfelleUuid),
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
