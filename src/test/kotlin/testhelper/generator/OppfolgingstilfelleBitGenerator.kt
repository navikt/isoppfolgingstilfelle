package testhelper.generator

import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag
import testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import java.time.LocalDateTime
import java.util.*

fun generateOppfolgingstilfelleBit() = OppfolgingstilfelleBit(
    uuid = UUID.randomUUID(),
    personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
    virksomhetsnummer = "987654321",
    createdAt = LocalDateTime.now(),
    inntruffet = LocalDateTime.now().minusDays(1),
    fom = LocalDateTime.now().minusDays(1),
    tom = LocalDateTime.now().plusDays(1),
    tagList = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ),
    ressursId = UUID.randomUUID().toString(),
)
