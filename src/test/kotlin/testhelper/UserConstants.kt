package testhelper

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val ARBEIDSTAKER_PERSONIDENTNUMBER = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_PERSONIDENTNUMBER_VEILEDER_NO_ACCESS =
        PersonIdentNumber(ARBEIDSTAKER_PERSONIDENTNUMBER.value.replace("2", "1"))

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer("987654321")
}
