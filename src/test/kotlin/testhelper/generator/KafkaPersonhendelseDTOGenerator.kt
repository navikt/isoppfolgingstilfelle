package testhelper.generator

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.syfo.domain.PersonIdentNumber
import testhelper.UserConstants
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

fun generateKafkaPersonhendelseDTO(
    personident: PersonIdentNumber = UserConstants.ARBEIDSTAKER_FNR,
) = Personhendelse().apply {
    hendelseId = UUID.randomUUID().toString()
    personidenter = listOf(personident.value)
    opprettet = Instant.now()
    doedsfall = Doedsfall().apply {
        doedsdato = LocalDate.now()
    }
    endringstype = Endringstype.OPPRETTET
}
