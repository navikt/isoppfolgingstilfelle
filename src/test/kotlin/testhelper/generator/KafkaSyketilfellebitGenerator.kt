package testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus.*
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.util.nowUTC
import testhelper.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer? = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = KafkaSyketilfellebit(
    id = UUID.randomUUID().toString(),
    fnr = personIdent.value,
    orgnummer = virksomhetsnummer?.value,
    opprettet = nowUTC(),
    inntruffet = nowUTC().minusDays(1),
    tags = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ).map { tag -> tag.name },
    ressursId = UUID.randomUUID().toString(),
    fom = LocalDate.now().minusDays(16),
    tom = LocalDate.now().minusDays(14),
    korrigererSendtSoknad = null,
)

fun generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
    fom: LocalDate,
    tom: LocalDate,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer,
).copy(
    orgnummer = null,
    tags = listOf(
        Tag.SYKMELDING,
        Tag.BEKREFTET,
        Tag.PERIODE,
        Tag.INGEN_AKTIVITET,
    ).map { tag -> tag.name },
    fom = fom,
    tom = tom,
)

fun generateKafkaSyketilfellebitNotRelevantNoVirksomhet(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer
).copy(
    id = UUID.randomUUID().toString(),
    orgnummer = null,
    tags = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ).map { tag -> tag.name },
)

fun generateKafkaSyketilfellebitSykmeldingNy(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
).copy(
    id = UUID.randomUUID().toString(),
    orgnummer = null,
    fom = LocalDate.now().minusDays(12),
    tom = LocalDate.now(),
    tags = listOf(
        Tag.SYKMELDING,
        Tag.NY,
        Tag.PERIODE,
        Tag.INGEN_AKTIVITET,
    ).map { tag -> tag.name },
)

fun generateKafkaSyketilfellebitInntektsmelding(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer
).copy(
    id = UUID.randomUUID().toString(),
    tags = listOf(
        Tag.INNTEKTSMELDING,
        Tag.ARBEIDSGIVERPERIODE,
    ).map { tag -> tag.name },
)

fun generateKafkaSyketilfellebitEgenmelding(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer
).copy(
    id = UUID.randomUUID().toString(),
    inntruffet = nowUTC().minusDays(16),
    tags = listOf(
        Tag.SYKMELDING,
        Tag.SENDT,
        Tag.EGENMELDING,
    ).map { tag -> tag.name },
)

fun generateKafkaStatusendring(
    sykmeldingId: UUID,
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
) = SykmeldingStatusKafkaMessageDTO(
    kafkaMetadata = KafkaMetadataDTO(
        sykmeldingId = sykmeldingId.toString(),
        timestamp = OffsetDateTime.now(),
        fnr = personIdentNumber.value,
        source = "",
    ),
    event = SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmeldingId.toString(),
        timestamp = OffsetDateTime.now(),
        statusEvent = StatusEndring.STATUS_AVBRUTT.value,
    ),
)
