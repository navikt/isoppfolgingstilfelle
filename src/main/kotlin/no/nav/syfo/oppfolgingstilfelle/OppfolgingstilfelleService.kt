package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitForUUID
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE
import no.nav.syfo.oppfolgingstilfelle.database.*
import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.kafka.OppfolgingstilfelleProducer
import no.nav.syfo.util.kafkaCallId
import org.slf4j.LoggerFactory
import java.sql.Connection

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
    val oppfolgingstilfelleProducer: OppfolgingstilfelleProducer,
) {
    fun oppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = database.getOppfolgingstilfellePerson(
            personIdent = personIdent,
        )
        return oppfolgingstilfellePerson?.toOppfolgingstilfellePerson()
    }

    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>
    ) {
        val oppfolgingstilfelleBitInPollCreatedList = mutableListOf<OppfolgingstilfelleBit>()
        oppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            log.info("Received relevant ${OppfolgingstilfelleBit::class.java.simpleName}, ready to process and attempt to create, inntruffet=${oppfolgingstilfelleBit.inntruffet}, callId=${kafkaCallId()}")

            val isOppfolgingstilfelleBitDuplicate =
                connection.getOppfolgingstilfelleBitForUUID(oppfolgingstilfelleBit.uuid) != null
            if (isOppfolgingstilfelleBitDuplicate) {
                log.warn(
                    "No ${OppfolgingstilfelleBit::class.java.simpleName} was inserted into database, attempted to insert a duplicate"
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            } else {
                connection.createOppfolgingstilfelleBit(
                    commit = false,
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                )
                oppfolgingstilfelleBitInPollCreatedList.add(oppfolgingstilfelleBit)

                val oppfolgingstilfelleBitInPollCreatedForPersonList =
                    oppfolgingstilfelleBitInPollCreatedList.filter { createdBit ->
                        createdBit.personIdentNumber.value == oppfolgingstilfelleBit.personIdentNumber.value
                    }
                createOppfolgingstilfellePerson(
                    connection = connection,
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                    oppfolgingstilfelleBitInPollCreatedForPersonList = oppfolgingstilfelleBitInPollCreatedForPersonList,
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED.increment()
            }
        }
    }

    private fun allCreatedOppfolgingstilfelleBit(
        oppfolgingstilfelleBitInPollCreatedForPersonList: List<OppfolgingstilfelleBit>,
    ): List<OppfolgingstilfelleBit> {
        val oppfolgingstilfelleBitListCreatedBeforePoll = oppfolgingstilfelleBitService.oppfolgingstilfelleBitList(
            personIdentNumber = oppfolgingstilfelleBitInPollCreatedForPersonList.first().personIdentNumber
        )
        val oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitListCreatedBeforePoll
            .toMutableList()
        oppfolgingstilfelleBitForPersonList.addAll(oppfolgingstilfelleBitInPollCreatedForPersonList)
        oppfolgingstilfelleBitForPersonList.sortedByDescending { bit -> bit.inntruffet }
        return oppfolgingstilfelleBitForPersonList
    }

    private fun createOppfolgingstilfellePerson(
        connection: Connection,
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
        oppfolgingstilfelleBitInPollCreatedForPersonList: List<OppfolgingstilfelleBit>,
    ) {
        val oppfolgingstilfelleBitForPersonList = allCreatedOppfolgingstilfelleBit(
            oppfolgingstilfelleBitInPollCreatedForPersonList = oppfolgingstilfelleBitInPollCreatedForPersonList
        )

        val oppfolgingstilfellePerson = oppfolgingstilfelleBit.toOppfolgingstilfellePerson(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitForPersonList,
        )
        connection.createOppfolgingstilfellePerson(
            commit = false,
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )

        oppfolgingstilfelleProducer.sendOppfolgingstilfelle(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleService::class.java)
    }
}
