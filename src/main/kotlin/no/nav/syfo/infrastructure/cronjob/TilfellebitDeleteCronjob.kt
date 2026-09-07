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
//
// The recompute trigger (setProcessedOppfolgingstilfelleBit / createOppfolgingstilfellePerson) is
// deliberately performed BEFORE the physical delete. Each step commits independently, so if the
// recompute step fails, the bit is still present (still to_be_deleted AND processed) and will be
// retried on the next run. If instead the delete happened first and a later step failed, the bit
// would be gone for good - unretryable and permanently stale - since nothing would ever select it
// again.
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
                // The bit being deleted still physically exists at this point, so it must be
                // excluded explicitly to compute what remains for this person afterwards.
                val remainingTilfelleBitList = tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(
                    personIdentNumber = pOppfolgingstilfelleBit.personIdentNumber,
                    includeAvbrutt = true,
                ).filterNot { it.uuid == pOppfolgingstilfelleBit.uuid }

                val nyesteTilfelleBit = remainingTilfelleBitList.firstOrNull()
                if (nyesteTilfelleBit != null) {
                    // Set the newest remaining tilfelleBit to unprocessed so that oppfolgingstilfelle
                    // is recomputed (without the bit we are about to delete) by OppfolgingstilfelleCronjob.
                    tilfellebitRepository.setProcessedOppfolgingstilfelleBit(nyesteTilfelleBit.uuid, false)
                } else {
                    // No other tilfellebit remains for this person: explicitly (re)create an empty
                    // oppfolgingstilfellePerson snapshot, so the person's state reflects that no
                    // active oppfolgingstilfelle remains - before the bit itself disappears.
                    oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                        oppfolgingstilfelleBit = pOppfolgingstilfelleBit.toOppfolgingstilfelleBit(),
                        oppfolgingstilfelleBitForPersonList = emptyList(),
                    )
                }

                // Only physically delete once the recompute trigger has durably succeeded.
                tilfellebitRepository.deleteOppfolgingstilfelleBit(pOppfolgingstilfelleBit.toOppfolgingstilfelleBit())

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
