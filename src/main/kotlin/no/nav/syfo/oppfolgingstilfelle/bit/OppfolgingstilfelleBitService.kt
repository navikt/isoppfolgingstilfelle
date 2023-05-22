package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID

class OppfolgingstilfelleBitService() {
    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ) {
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            val existingWithSameUuid = connection.getOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)
            if (existingWithSameUuid != null) {
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            } else {
                val isRelevant = if (oppfolgingstilfelleBit.ready) {
                    true
                } else {
                    if (oppfolgingstilfelleBit.fom.isBefore(ARENA_CUTOFF)) {
                        false
                    } else {
                        val existing = connection.getProcessedOppfolgingstilfelleBitList(
                            personIdentNumber = oppfolgingstilfelleBit.personIdentNumber
                        ).toOppfolgingstilfelleBitList()
                        !existing.containsSendtSykmeldingBit(oppfolgingstilfelleBit)
                    }
                }
                if (isRelevant) {
                    log.info("Received relevant ${OppfolgingstilfelleBit::class.java.simpleName}: inntruffet=${oppfolgingstilfelleBit.inntruffet}, tags=${oppfolgingstilfelleBit.tagList}")
                    connection.createOppfolgingstilfelleBit(
                        commit = false,
                        oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                    )
                    COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED.increment()
                } else {
                    COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_SKIPPED_CREATE.increment()
                }
            }
        }
    }

    fun deleteOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitIdList: List<UUID>,
    ) {
        oppfolgingstilfelleBitIdList.forEach { uuid ->
            val existing = connection.getOppfolgingstilfelleBit(uuid)
            if (existing != null) {
                connection.deleteOppfolgingstilfelleBit(existing.toOppfolgingstilfelleBit())
                connection.getProcessedOppfolgingstilfelleBitList(
                    personIdentNumber = existing.personIdentNumber,
                    includeAvbrutt = true,
                ).firstOrNull()?.let {
                    // Set the newest tilfelleBit to unprocessed so that oppfolgingstilfelle is updated by cronjob
                    connection.setProcessedOppfolgingstilfelleBit(it.uuid, false)
                }
            } else {
                log.warn("No tilfellebit found for tombstone with uuid $uuid")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleBitService::class.java)
        private val ARENA_CUTOFF = LocalDate.of(2022, 5, 21)
    }
}
