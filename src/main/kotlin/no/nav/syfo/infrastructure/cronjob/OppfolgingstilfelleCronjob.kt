package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.domain.isSykmeldingBekreftet
import no.nav.syfo.domain.isSykmeldingNy
import no.nav.syfo.domain.isSykepengesoknad
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.database.bit.toOppfolgingstilfelleBitList
import org.slf4j.LoggerFactory
import java.time.LocalDate

class OppfolgingstilfelleCronjob(
    private val oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    private val tilfellebitRepository: TilfellebitRepository,
    private val pdlClient: PdlClient,
    private val kandidatRepository: SykmeldtUtenArbeidsgiverKandidatRepository,
    override val intervalDelayMinutes: Long = 10,
) : Cronjob {
    override val initialDelayMinutes: Long = 2

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed tilfellebit processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob() = CronjobResult().also { result ->
        val unprocessed = tilfellebitRepository.getUnprocessedOppfolgingstilfelleBitList().toOppfolgingstilfelleBitList()
        unprocessed.forEach { oppfolgingstilfelleBit ->
            try {
                val oppfolgingstilfelleBitForPersonList = tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(
                    personIdentNumber = oppfolgingstilfelleBit.personIdentNumber,
                ).toOppfolgingstilfelleBitList().toMutableList()

                if (!tilfellebitRepository.isTilfelleBitAvbrutt(oppfolgingstilfelleBit.uuid)) {
                    oppfolgingstilfelleBitForPersonList.add(
                        index = 0,
                        element = oppfolgingstilfelleBit,
                    )
                }

                val oppfolgingstilfellePerson = oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                    oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitForPersonList,
                )
                tilfellebitRepository.setProcessedOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)

                lagreBekreftetKandidatHvisAktuell(
                    incomingBit = oppfolgingstilfelleBit,
                    oppfolgingstilfellePerson = oppfolgingstilfellePerson,
                    oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitForPersonList,
                )

                oppdaterHasSykepengesoknadHvisAktuell(
                    incomingBit = oppfolgingstilfelleBit,
                    oppfolgingstilfellePerson = oppfolgingstilfellePerson,
                )

                result.updated++
            } catch (exc: Exception) {
                log.error("caught exception when processing oppfolgingstilfelleBit", exc)
                result.failed++
            }
        }
    }

    private suspend fun lagreBekreftetKandidatHvisAktuell(
        incomingBit: OppfolgingstilfelleBit,
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
        oppfolgingstilfelleBitForPersonList: List<OppfolgingstilfelleBit>,
    ) {
        try {
            if (!incomingBit.isSykmeldingBekreftet()) return

            val latestTilfelle = oppfolgingstilfellePerson.oppfolgingstilfelleList.lastOrNull() ?: return

            // Ignore if tilfelle is not current
            if (latestTilfelle.end.isBefore(LocalDate.now())) return

            // Ignore incomingBit (bekreftet) if there is a newer bit in tilfelle, unless the only newer bit(s) are SYKMELDING NY
            val nyereBiterSomIkkeErSykmeldingNy = oppfolgingstilfelleBitForPersonList.filter { bit ->
                bit.tom > incomingBit.tom
            }.filterNot { bit ->
                bit.isSykmeldingNy()
            }
            if (nyereBiterSomIkkeErSykmeldingNy.isNotEmpty()) return

            // A higher-priority SENDT/INNTEKTSMELDING bit may still "win" the tilfelle end day
            // even though the incoming bit is BEKREFTET, so this check is not redundant.
            if (latestTilfelle.arbeidstakerAtTilfelleEnd) return

            val aktorId = pdlClient.pdlIdenter(incomingBit.personIdentNumber)?.hentIdenter?.aktivAktorId ?: run {
                log.warn("Fant ikke aktorId i PDL for BEKREFTET kandidat, hopper over")
                return
            }

            val hasSykepengesoknad = oppfolgingstilfelleBitForPersonList.any { bit ->
                bit.isSykepengesoknad() && bit.fom <= latestTilfelle.end && bit.tom >= latestTilfelle.start
            }

            val kandidat = SykmeldtUtenArbeidsgiverKandidat.opprett(
                personident = incomingBit.personIdentNumber,
                aktorId = aktorId,
                referanseId = incomingBit.ressursId,
                tilfelleStart = latestTilfelle.start,
                hasSykepengesoknad = hasSykepengesoknad,
            )
            kandidatRepository.createIfMissing(kandidat)
        } catch (exc: Exception) {
            log.error("Failed to process SykmeldtUtenArbeidsgiverKandidat for tilfellebit: ${incomingBit.uuid}", exc)
        }
    }

    private fun oppdaterHasSykepengesoknadHvisAktuell(
        incomingBit: OppfolgingstilfelleBit,
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    ) {
        try {
            if (!incomingBit.isSykepengesoknad()) return

            if (!kandidatRepository.existsForPersonident(incomingBit.personIdentNumber)) return

            // Finn tilfellet som sykepengesøknaden hører til, slik at vi kan oppdatere
            // en eventuell eksisterende kandidat for det tilfellet (identifisert ved tilfelle_start)
            val tilfelle = oppfolgingstilfellePerson.oppfolgingstilfelleList.find { tilfelle ->
                incomingBit.fom <= tilfelle.end && incomingBit.tom >= tilfelle.start
            } ?: return

            kandidatRepository.oppdaterHasSykepengesoknad(
                personident = incomingBit.personIdentNumber,
                tilfelleStart = tilfelle.start,
            )
        } catch (exc: Exception) {
            log.error("Failed to update hasSykepengesoknad for tilfellebit: ${incomingBit.uuid}", exc)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleCronjob::class.java)
    }
}
