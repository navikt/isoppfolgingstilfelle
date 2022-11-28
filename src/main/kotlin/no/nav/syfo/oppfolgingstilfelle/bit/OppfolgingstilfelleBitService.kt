package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDate

class OppfolgingstilfelleBitService() {
    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ) {
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            val existingWithSameUuid = connection.getOppfolgingstilfelleBitForUUID(oppfolgingstilfelleBit.uuid)
                ?.toOppfolgingstilfelleBit()
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

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleBitService::class.java)
        private val ARENA_CUTOFF = LocalDate.of(2022, 5, 21)
    }
}
