package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.domain.toKafkaOppfolgingstilfelleArbeidstaker
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class OppfolgingstilfelleProducer(
    private val kafkaProducerOppfolgingstilfelle: KafkaProducer<String, KafkaOppfolgingstilfelleArbeidstaker>,
) {
    fun sendOppfolgingstilfelle(oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker) {
        val key = oppfolgingstilfelleArbeidstaker.uuid.toString()
        try {
            val kafkaOppfolgingstilfelle = oppfolgingstilfelleArbeidstaker.toKafkaOppfolgingstilfelleArbeidstaker()
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
        const val OPPFOLGINGSTILFELLE_TOPIC = "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-arbeidstaker"
        private val log = LoggerFactory.getLogger(OppfolgingstilfelleProducer::class.java)
    }
}
