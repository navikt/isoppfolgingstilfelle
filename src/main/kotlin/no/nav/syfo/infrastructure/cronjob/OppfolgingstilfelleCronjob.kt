package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.database.bit.toOppfolgingstilfelleBitList
import org.slf4j.LoggerFactory

class OppfolgingstilfelleCronjob(
    private val oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    private val tilfellebitRepository: TilfellebitRepository,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed tilfellebit processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob() = CronjobResult().also { result ->
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

                oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                    oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitForPersonList,
                )
                tilfellebitRepository.setProcessedOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)
                result.updated++
            } catch (exc: Exception) {
                log.error("caught exception when processing oppfolgingstilfelleBit", exc)
                result.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleCronjob::class.java)
    }
}
