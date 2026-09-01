package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.PersonIdentNumber
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class StartOppfolgingProducer(
    private val producer: KafkaProducer<String, KafkaStartOppfolgingMelding>,
) {
    fun sendSykmeldtUtenArbeidsgiverKandidat(
        personident: PersonIdentNumber,
    ) {
        val kafkaStartOppfolgingMelding = KafkaStartOppfolgingMelding(
            personident = personident.value,
            aarsak = KafkaStartOppfolgingMelding.Aarsak.SYKMELDT_UTEN_ARBEIDSGIVER_4_UKER,
            kilde = KafkaStartOppfolgingMelding.Kilde.ISYFO,
            arbeidsoppfolgingskontor = null,
            registrant = KafkaStartOppfolgingMelding.Registrant.system(APPLICATION_NAME),
        )
        try {
            producer.send(
                ProducerRecord(
                    START_OPPFOLGING_TOPIC,
                    personident.value,
                    kafkaStartOppfolgingMelding,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send StartOppfolgingMelding: ${e.message}",
                e,
            )
            throw e
        }
    }

    companion object {
        const val START_OPPFOLGING_TOPIC = "poao.start-oppfolging"
        const val APPLICATION_NAME = "isoppfolgingstilfelle"
        private val log = LoggerFactory.getLogger(StartOppfolgingProducer::class.java)
    }
}
