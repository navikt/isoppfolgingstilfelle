package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KafkaSyketilfellebitDeserializer : Deserializer<KafkaSyketilfellebit> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(topic: String, data: ByteArray): KafkaSyketilfellebit =
        mapper.readValue(data, KafkaSyketilfellebit::class.java)

    override fun close() {}
}
