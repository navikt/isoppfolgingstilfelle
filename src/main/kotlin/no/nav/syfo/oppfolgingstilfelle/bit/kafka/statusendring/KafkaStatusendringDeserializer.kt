package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KafkaStatusendringDeserializer : Deserializer<SykmeldingStatusKafkaMessageDTO?> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(topic: String, data: ByteArray): SykmeldingStatusKafkaMessageDTO? =
        mapper.readValue(data, SykmeldingStatusKafkaMessageDTO::class.java)

    override fun close() {}
}
