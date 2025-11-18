package testhelper.generator

import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.nowUTC
import testhelper.UserConstants
import java.time.LocalDate
import java.util.*

fun generateOppfolgingstilfellePerson(
    personIdent: PersonIdentNumber = UserConstants.PERSONIDENTNUMBER_DEFAULT,
    antallSykedager: Int = 120,
    oppfolgingstilfelleList: List<Oppfolgingstilfelle>? = null,
) = OppfolgingstilfellePerson(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    personIdentNumber = personIdent,
    oppfolgingstilfelleList = oppfolgingstilfelleList
        ?: listOf(
            generateOppfolgingstilfelle(
                start = LocalDate.now().minusMonths(6),
                end = LocalDate.now().minusMonths(2),
                antallSykedager = antallSykedager,
            )
        ),
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = nowUTC(),
    dodsdato = null,
)

fun generateOppfolgingstilfelle(
    start: LocalDate,
    end: LocalDate,
    antallSykedager: Int? = null,
    arbeidstakerAtTilfelleEnd: Boolean = true,
    virksomhetsnummerList: List<Virksomhetsnummer> = emptyList(),
    gradertAtTilfelleEnd: Boolean = false,
) = Oppfolgingstilfelle(
    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
    start = start,
    end = end,
    antallSykedager = antallSykedager,
    virksomhetsnummerList = virksomhetsnummerList,
    gradertAtTilfelleEnd = gradertAtTilfelleEnd,
)
