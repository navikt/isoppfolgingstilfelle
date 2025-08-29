package no.nav.syfo.infrastructure.kafka.sykmeldingstatus

import java.time.OffsetDateTime

enum class StatusEndring(val value: String) {
    STATUS_APEN("APEN"),
    STATUS_AVBRUTT("AVBRUTT"),
    STATUS_UTGATT("UTGATT"),
    STATUS_SENDT("SENDT"),
    STATUS_BEKREFTET("BEKREFTET"),
    STATUS_SLETTET("SLETTET");
}

data class SykmeldingStatusKafkaMessageDTO(
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)

data class KafkaMetadataDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val fnr: String,
    val source: String,
)

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val arbeidsgiver: ArbeidsgiverStatusDTO? = null,
    val erSvarOppdatering: Boolean? = null,
)

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String,
)
