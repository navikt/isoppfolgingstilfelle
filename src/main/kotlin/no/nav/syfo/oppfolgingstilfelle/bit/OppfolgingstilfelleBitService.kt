package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.*
import org.slf4j.LoggerFactory
import java.sql.Connection

class OppfolgingstilfelleBitService() {
    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ) {
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            val existingWithSameUuid = connection.getOppfolgingstilfelleBitForUUID(oppfolgingstilfelleBit.uuid)
                ?.toOppfolgingstilfelleBit()
            if (existingWithSameUuid != null) {
                handleDuplicate(
                    connection = connection,
                    existing = existingWithSameUuid,
                    incoming = oppfolgingstilfelleBit,
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            } else {
                val isRelevant = if (oppfolgingstilfelleBit.ready) {
                    true
                } else {
                    val existing = connection.getProcessedOppfolgingstilfelleBitList(
                        personIdentNumber = oppfolgingstilfelleBit.personIdentNumber
                    ).toOppfolgingstilfelleBitList()
                    !existing.containsSendtSykmeldingBit(oppfolgingstilfelleBit)
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

    private fun handleDuplicate(
        connection: Connection,
        existing: OppfolgingstilfelleBit,
        incoming: OppfolgingstilfelleBit,
    ) {
        if (existing.uuid == incoming.uuid && existing.korrigerer == null && incoming.korrigerer != null) {
            connection.setKorrigererOppfolgingstilfelleBit(
                uuid = existing.uuid,
                korrigerer = incoming.korrigerer,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleBitService::class.java)
    }
}
