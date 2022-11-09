package testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebit
import no.nav.syfo.util.nowUTC
import testhelper.UserConstants
import java.time.LocalDate
import java.util.*

fun generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = KafkaSyketilfellebit(
    id = UUID.randomUUID().toString(),
    fnr = personIdent.value,
    orgnummer = virksomhetsnummer.value,
    opprettet = nowUTC(),
    inntruffet = nowUTC().minusDays(1),
    tags = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ).map { tag -> tag.name },
    ressursId = UUID.randomUUID().toString(),
    fom = LocalDate.now().minusDays(1),
    tom = LocalDate.now().plusDays(1),
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
    orgnummer = null,
    tags = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ).map { tag -> tag.name },
)

fun generateKafkaSyketilfellebitNotRelevantSykmeldingNy(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer
).copy(
    orgnummer = null,
    tags = listOf(
        Tag.SYKMELDING,
        Tag.NY,
    ).map { tag -> tag.name },
)

fun generateKafkaSyketilfellebitInntektsmelding(
    personIdentNumber: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = generateKafkaSyketilfellebitRelevantVirksomhet(
    personIdent = personIdentNumber,
    virksomhetsnummer = virksomhetsnummer
).copy(
    tags = listOf(
        Tag.INNTEKTSMELDING,
        Tag.ARBEIDSGIVERPERIODE,
    ).map { tag -> tag.name },
)
