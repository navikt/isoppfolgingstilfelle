package testhelper.generator

import no.nav.syfo.oppfolgingstilfelle.person.domain.*
import no.nav.syfo.util.nowUTC
import testhelper.UserConstants
import java.time.LocalDate
import java.util.*

fun generateOppfolgingstilfellePerson() = OppfolgingstilfellePerson(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    personIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    oppfolgingstilfelleList = listOf(
        Oppfolgingstilfelle(
            arbeidstakerAtTilfelleEnd = false,
            start = LocalDate.now().minusMonths(6),
            end = LocalDate.now().minusMonths(2),
            virksomhetsnummerList = listOf(),
            gradertAtTilfelleEnd = false,
        )
    ),
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = nowUTC(),
)
