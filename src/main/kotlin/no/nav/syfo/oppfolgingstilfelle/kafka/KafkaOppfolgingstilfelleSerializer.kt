package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaOppfolgingstilfelleSerializer : Serializer<KafkaOppfolgingstilfelleArbeidstaker> {
    private val mapper = configuredJacksonMapper()

    override fun serialize(topic: String?, data: KafkaOppfolgingstilfelleArbeidstaker?): ByteArray = mapper.writeValueAsBytes(data)
}
