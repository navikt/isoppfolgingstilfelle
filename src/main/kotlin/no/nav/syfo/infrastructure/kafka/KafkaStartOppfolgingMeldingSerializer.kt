package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaStartOppfolgingMeldingSerializer : Serializer<KafkaStartOppfolgingMelding> {
    private val mapper = configuredJacksonMapper()

    override fun serialize(topic: String?, data: KafkaStartOppfolgingMelding?): ByteArray = mapper.writeValueAsBytes(data)
}
