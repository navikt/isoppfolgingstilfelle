package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.DAYS_AFTER_TILFELLE_START
import no.nav.syfo.domain.FregStatusSjekkResultat
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.pensjonpen.PensjonPenClient
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import no.nav.syfo.infrastructure.kafka.StartOppfolgingProducer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

val MINIMUM_NUMBER_OF_DAYS_BETWEEN_TILFELLER = 16L

class ModiaAOOversendingCronjob(
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val kandidatRepository: SykmeldtUtenArbeidsgiverKandidatRepository,
    private val pensjonPenClient: PensjonPenClient,
    private val pdlClient: PdlClient,
    private val startOppfolgingProducer: StartOppfolgingProducer,
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

    suspend fun runJob() = CronjobResult().also { result ->
        val kandidater = kandidatRepository.getKandidaterForProcessing()
        kandidater.forEach { kandidat ->
            try {
                val oppfolgingstilfellePerson = oppfolgingstilfelleService.getOppfolgingstilfellePerson(
                    personIdent = kandidat.personident
                )
                val oppfolgingstilfellePersonDto = oppfolgingstilfellePerson?.toOppfolgingstilfellePersonDTO()
                val oppfolgingstilfelleUuid = oppfolgingstilfellePerson?.uuid.toString()
                val latestTilfelle = oppfolgingstilfellePersonDto?.oppfolgingstilfelleList?.firstOrNull()
                val uforegrad = pensjonPenClient.getUforegrad(kandidat.personident)

                val today = LocalDate.now(ZoneId.of("Europe/Oslo"))

                when {
                    oppfolgingstilfellePersonDto == null || latestTilfelle == null ||
                        oppfolgingstilfellePersonDto.dodsdato != null ||
                        latestTilfelle.end.plusDays(MINIMUM_NUMBER_OF_DAYS_BETWEEN_TILFELLER).isBefore(today) -> {
                        kandidatRepository.markerFerdig(kandidat.uuid)
                    }

                    uforegrad?.uforegrad == 100 -> {
                        log.info(
                            "Kandidat ferdigstilles uten oversending fordi personen har 100% uføregrad, {}",
                            StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                        )
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

                    latestTilfelle.arbeidstakerAtTilfelleEnd -> {
                        kandidatRepository.markerFerdig(kandidat.uuid)
                        log.info(
                            "Kandidat ferdigstilles fordi siste oppfolgingstilfelle har arbeidsgiver, {}, {}",
                            StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                            StructuredArguments.keyValue("oppfolgingstilfellePersonDtoUuid", oppfolgingstilfelleUuid),
                        )
                    }

                    else -> sendHvisPersonKvalifiserer(kandidat = kandidat, today = today)
                }
                result.updated++
            } catch (exc: Exception) {
                log.error("Feil ved behandling av kandidat ${kandidat.uuid}", exc)
                result.failed++
            }
        }
    }

    private suspend fun sendHvisPersonKvalifiserer(kandidat: SykmeldtUtenArbeidsgiverKandidat, today: LocalDate) {
        val pdlPerson = pdlClient.hentPerson(kandidat.personident)
            ?: throw RuntimeException("Fant ikke person i PDL for kandidat ${kandidat.uuid}")
        val isUnder18 = pdlPerson.isUnder18(today)
        val isOver67 = pdlPerson.isOver67(today)
        val fregStatusSjekk = pdlPerson.fregStatusSjekk()
        kandidatRepository.oppdaterPersonstatus(
            uuid = kandidat.uuid,
            isUnder18 = isUnder18,
            isOver67 = isOver67,
            fregStatusSjekk = fregStatusSjekk,
        )

        when {
            isUnder18 -> {
                log.info(
                    "Kandidat ferdigstilles uten oversending fordi personen er under 18 år, {}",
                    StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                )
                kandidatRepository.markerFerdig(kandidat.uuid)
            }

            isOver67 -> {
                log.info(
                    "Kandidat ferdigstilles uten oversending fordi personen er over 67 år, {}",
                    StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                )
                kandidatRepository.markerFerdig(kandidat.uuid)
            }

            fregStatusSjekk != FregStatusSjekkResultat.FREG_STATUS_OK -> {
                log.info(
                    "Kandidat ferdigstilles uten oversending pga. folkeregisterstatus, {}, {}",
                    StructuredArguments.keyValue("kandidatUuid", kandidat.uuid),
                    StructuredArguments.keyValue("fregStatusSjekk", fregStatusSjekk),
                )
                kandidatRepository.markerFerdig(kandidat.uuid)
            }

            else -> {
                startOppfolgingProducer.sendSykmeldtUtenArbeidsgiverKandidat(
                    personident = kandidat.personident,
                )

                kandidatRepository.markerOversendt(kandidat.uuid)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ModiaAOOversendingCronjob::class.java)
    }
}
