package no.nav.syfo.application

import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.containsSendtSykmeldingBit
import no.nav.syfo.infrastructure.database.bit.*
import no.nav.syfo.infrastructure.kafka.syketilfelle.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED
import no.nav.syfo.infrastructure.kafka.syketilfelle.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE
import no.nav.syfo.infrastructure.kafka.syketilfelle.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_SKIPPED_CREATE
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class OppfolgingstilfelleBitService(
    private val tilfellebitRepository: TilfellebitRepository,
) {
    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ) {
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            val existingWithSameUuid = tilfellebitRepository.getOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)
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
                    tilfellebitRepository.createOppfolgingstilfelleBit(
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
            val existing = tilfellebitRepository.getOppfolgingstilfelleBit(uuid)
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
