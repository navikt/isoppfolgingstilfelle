package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.toKafkaOppfolgingstilfellePerson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class OppfolgingstilfellePersonProducer(
    private val kafkaProducerOppfolgingstilfelle: KafkaProducer<String, KafkaOppfolgingstilfellePerson>,
) {
    fun sendOppfolgingstilfellePerson(
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    ) {
        val kafkaOppfolgingstilfellePerson = oppfolgingstilfellePerson.toKafkaOppfolgingstilfellePerson()
        val key = UUID.nameUUIDFromBytes(kafkaOppfolgingstilfellePerson.personIdentNumber.toByteArray()).toString()
        try {
            kafkaProducerOppfolgingstilfelle.send(
                ProducerRecord(
                    OPPFOLGINGSTILFELLE_TOPIC,
                    key,
                    kafkaOppfolgingstilfellePerson,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaOppfolgingstilfelle with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val OPPFOLGINGSTILFELLE_TOPIC = "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person"
        private val log = LoggerFactory.getLogger(OppfolgingstilfellePersonProducer::class.java)
    }
}
