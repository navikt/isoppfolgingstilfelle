package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import java.time.OffsetDateTime

const val STATUS_APEN = "APEN"
const val STATUS_AVBRUTT = "AVBRUTT"
const val STATUS_UTGATT = "UTGATT"
const val STATUS_SENDT = "SENDT"
const val STATUS_BEKREFTET = "BEKREFTET"
const val STATUS_SLETTET = "SLETTET"

data class SykmeldingStatusKafkaMessageDTO(
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)

data class KafkaMetadataDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val fnr: String,
    val source: String
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
    val orgNavn: String
)
