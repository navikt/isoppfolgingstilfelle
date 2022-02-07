package testhelper

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val PERSONIDENTNUMBER_DEFAULT = PersonIdentNumber("12345678912")
    val PERSONIDENTNUMBER_VEILEDER_NO_ACCESS =
        PersonIdentNumber(PERSONIDENTNUMBER_DEFAULT.value.replace("2", "1"))

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer("987654321")
}
