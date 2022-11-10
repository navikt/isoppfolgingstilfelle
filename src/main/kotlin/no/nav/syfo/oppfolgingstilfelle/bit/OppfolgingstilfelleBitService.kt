package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE
import no.nav.syfo.util.kafkaCallId
import org.slf4j.LoggerFactory
import java.sql.Connection

class OppfolgingstilfelleBitService() {
    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ) {
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            val isDuplicate = connection.getOppfolgingstilfelleBitForUUID(oppfolgingstilfelleBit.uuid) != null
            if (isDuplicate) {
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            } else {
                log.info("Received relevant ${OppfolgingstilfelleBit::class.java.simpleName}: inntruffet=${oppfolgingstilfelleBit.inntruffet}, callId=${kafkaCallId()}")
                connection.createOppfolgingstilfelleBit(
                    commit = false,
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED.increment()
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleBitService::class.java)
    }
}
