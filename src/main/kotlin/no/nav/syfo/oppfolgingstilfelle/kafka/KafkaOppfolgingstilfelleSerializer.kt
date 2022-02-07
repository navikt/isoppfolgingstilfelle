package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaOppfolgingstilfelleSerializer : Serializer<KafkaOppfolgingstilfellePerson> {
    private val mapper = configuredJacksonMapper()

    override fun serialize(topic: String?, data: KafkaOppfolgingstilfellePerson?): ByteArray = mapper.writeValueAsBytes(data)
}
