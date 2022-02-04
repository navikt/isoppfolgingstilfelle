package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.domain.toKafkaOppfolgingstilfellePerson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class OppfolgingstilfelleProducer(
    private val kafkaProducerOppfolgingstilfelle: KafkaProducer<String, KafkaOppfolgingstilfellePerson>,
) {
    fun sendOppfolgingstilfelle(
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    ) {
        val key = oppfolgingstilfellePerson.uuid.toString()
        try {
            val kafkaOppfolgingstilfelle = oppfolgingstilfellePerson.toKafkaOppfolgingstilfellePerson()
            kafkaProducerOppfolgingstilfelle.send(
                ProducerRecord(
                    OPPFOLGINGSTILFELLE_TOPIC,
                    key,
                    kafkaOppfolgingstilfelle,
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
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleProducer::class.java)
    }
}
