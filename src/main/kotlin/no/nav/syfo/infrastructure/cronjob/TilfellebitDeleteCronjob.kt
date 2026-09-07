package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.database.bit.toOppfolgingstilfelleBit
import org.slf4j.LoggerFactory

// Performs the actual physical deletion of tilfellebits that have been marked for deletion
// (via tombstone records on the syketilfellebit topic). Deletion is deferred to this separate
// cronjob - and only ever acts on bits that are already `processed` - so that it never races
// with OppfolgingstilfelleCronjob's read-then-mark-processed flow for the same bit.
class TilfellebitDeleteCronjob(
    private val tilfellebitRepository: TilfellebitRepository,
    private val oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    override val initialDelayMinutes: Long = 9,
    override val intervalDelayMinutes: Long = 10,
) : Cronjob {

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed tilfellebit delete job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob() = CronjobResult().also { result ->
        val markedForDeletion = tilfellebitRepository.getOppfolgingstilfelleBitMarkedForDeletion()
        markedForDeletion.forEach { pOppfolgingstilfelleBit ->
            try {
                tilfellebitRepository.deleteOppfolgingstilfelleBit(pOppfolgingstilfelleBit.toOppfolgingstilfelleBit())
                val nyesteTilfelleBit = tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(
                    personIdentNumber = pOppfolgingstilfelleBit.personIdentNumber,
                    includeAvbrutt = true,
                ).firstOrNull()
                if (nyesteTilfelleBit != null) {
                    // Set the newest tilfelleBit to unprocessed so that oppfolgingstilfelle is updated by cronjob
                    tilfellebitRepository.setProcessedOppfolgingstilfelleBit(nyesteTilfelleBit.uuid, false)
                } else {
                    // The deleted bit was the only (remaining) tilfellebit for this person, so
                    // there is no other bit left to flip to unprocessed to trigger a recompute
                    // via OppfolgingstilfelleCronjob. Explicitly (re)create an empty
                    // oppfolgingstilfellePerson snapshot instead, so the person's state reflects
                    // that no active oppfolgingstilfelle remains.
                    oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                        oppfolgingstilfelleBit = pOppfolgingstilfelleBit.toOppfolgingstilfelleBit(),
                        oppfolgingstilfelleBitForPersonList = emptyList(),
                    )
                }
                result.updated++
            } catch (exc: Exception) {
                log.error("caught exception when deleting oppfolgingstilfelleBit", exc)
                result.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TilfellebitDeleteCronjob::class.java)
    }
}
