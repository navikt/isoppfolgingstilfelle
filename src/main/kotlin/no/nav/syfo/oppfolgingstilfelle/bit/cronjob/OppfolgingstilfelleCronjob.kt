package no.nav.syfo.oppfolgingstilfelle.bit.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import org.slf4j.LoggerFactory

class OppfolgingstilfelleCronjob(
    private val database: DatabaseInterface,
    private val oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
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
        val unprocessed = database.getUnprocessedOppfolgingstilfelleBitList().toOppfolgingstilfelleBitList()
        unprocessed.forEach { oppfolgingstilfelleBit ->
            try {
                database.connection.use { connection ->
                    val oppfolgingstilfelleBitForPersonList = connection.getProcessedOppfolgingstilfelleBitList(
                        personIdentNumber = oppfolgingstilfelleBit.personIdentNumber,
                    ).toOppfolgingstilfelleBitList().toMutableList()

                    if (!connection.isTilfelleBitAvbrutt(oppfolgingstilfelleBit.uuid)) {
                        oppfolgingstilfelleBitForPersonList.add(
                            index = 0,
                            element = oppfolgingstilfelleBit,
                        )
                    }

                    oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                        connection = connection,
                        oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                        oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitForPersonList,
                    )
                    connection.setProcessedOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)
                    connection.commit()
                }
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
