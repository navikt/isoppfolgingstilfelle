package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE
import no.nav.syfo.util.kafkaCallId
import org.slf4j.LoggerFactory
import java.sql.Connection

class OppfolgingstilfelleBitService(
    val database: DatabaseInterface,
    val oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
) {
    fun oppfolgingstilfelleBitList(
        personIdentNumber: PersonIdentNumber,
    ) = database.connection.use { connection ->
        connection.getOppfolgingstilfelleBitList(
            personIdentNumber = personIdentNumber,
        ).toOppfolgingstilfelleBitList()
    }

    fun createOppfolgingstilfelleBitList(
        connection: Connection,
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
        cronjobEnabled: Boolean,
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
                if (!cronjobEnabled) {
                    oppfolgingstilfelleBitInPollCreatedList.add(oppfolgingstilfelleBit)

                    val oppfolgingstilfelleBitInPollCreatedForPersonList =
                        oppfolgingstilfelleBitInPollCreatedList.filter { createdBit ->
                            createdBit.personIdentNumber.value == oppfolgingstilfelleBit.personIdentNumber.value
                        }

                    val oppfolgingstilfelleBitForPersonList = allCreatedOppfolgingstilfelleBit(
                        oppfolgingstilfelleBitInPollCreatedForPersonList = oppfolgingstilfelleBitInPollCreatedForPersonList
                    )

                    oppfolgingstilfellePersonService.createOppfolgingstilfellePerson(
                        connection = connection,
                        oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                        oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitForPersonList,
                    )
                }
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED.increment()
            }
        }
    }

    private fun allCreatedOppfolgingstilfelleBit(
        oppfolgingstilfelleBitInPollCreatedForPersonList: List<OppfolgingstilfelleBit>,
    ): List<OppfolgingstilfelleBit> {
        val oppfolgingstilfelleBitListCreatedBeforePoll = oppfolgingstilfelleBitList(
            personIdentNumber = oppfolgingstilfelleBitInPollCreatedForPersonList.first().personIdentNumber
        )
        val oppfolgingstilfelleBitForPersonList = oppfolgingstilfelleBitListCreatedBeforePoll.toMutableList()
        oppfolgingstilfelleBitForPersonList.addAll(oppfolgingstilfelleBitInPollCreatedForPersonList)
        return oppfolgingstilfelleBitForPersonList.sortedByDescending { bit -> bit.inntruffet }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleBitService::class.java)
    }
}
