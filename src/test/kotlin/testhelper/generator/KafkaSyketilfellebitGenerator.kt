package testhelper.generator

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebit
import testhelper.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateKafkaSyketilfellebit(
    arbeidstakerPersonIdentNumber: PersonIdentNumber = UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER,
    virksomhetsnummer: Virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_DEFAULT,
) = KafkaSyketilfellebit(
    id = UUID.randomUUID().toString(),
    fnr = arbeidstakerPersonIdentNumber.value,
    orgnummer = virksomhetsnummer.value,
    opprettet = OffsetDateTime.now(),
    inntruffet = OffsetDateTime.now().minusDays(1),
    tags = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ).map { tag -> tag.name },
    ressursId = UUID.randomUUID().toString(),
    fom = LocalDate.now().minusDays(1),
    tom = LocalDate.now().plusDays(1),
    korrigererSendtSoknad = null,
)
