package testhelper

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val PERSONIDENTNUMBER_DEFAULT = PersonIdentNumber("12345678912")
    val PERSONIDENTNUMBER_VEILEDER_NO_ACCESS =
        PersonIdentNumber(PERSONIDENTNUMBER_DEFAULT.value.replace("2", "1"))

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer("912345678")

    val ARBEIDSTAKER_FNR = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_2_FNR = PersonIdentNumber("12345678911")
    val ARBEIDSTAKER_3_FNR = PersonIdentNumber("12345678913")
    val ARBEIDSTAKER_4_FNR = PersonIdentNumber("12345678919")
    val ARBEIDSTAKER_WITH_ERROR = PersonIdentNumber("12345678666")
    val ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "8"))
    val ARBEIDSTAKER_UNKNOWN_AAREG = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "9"))

    val NARMESTELEDER_FNR = PersonIdentNumber("98765432101")
    val NARMESTELEDER_FNR_2 = PersonIdentNumber("98765432102")
    val VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer(VIRKSOMHETSNUMMER_DEFAULT.value)
    val OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("922222222")

    const val PERSON_TLF = "12345678"
}
