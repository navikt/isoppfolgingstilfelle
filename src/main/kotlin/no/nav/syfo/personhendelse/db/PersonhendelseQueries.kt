package no.nav.syfo.personhendelse.db

import no.nav.syfo.application.database.toList
import no.nav.syfo.database.NoElementInsertedException
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.Date
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

const val queryInsertPersonDodsdato =
    """
    INSERT INTO PERSON (
        id,
        uuid,
        created_at,
        personident,
        dodsdato
    ) values (DEFAULT, ?, ?, ?, ?)
    RETURNING id    
    """

fun Connection.createPerson(
    uuid: UUID,
    personIdent: PersonIdentNumber,
    dodsdato: LocalDate,
) {
    val idList = this.prepareStatement(queryInsertPersonDodsdato).use {
        it.setString(1, uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setString(3, personIdent.value)
        it.setDate(4, Date.valueOf(dodsdato))
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating PERSON failed, no rows affected.")
    }
}

const val queryGetPersonDodsdato =
    """
    SELECT DODSDATO FROM PERSON WHERE personident=?
    """

fun Connection.getDodsdato(
    personIdent: PersonIdentNumber,
): LocalDate? {
    val datoList = this.prepareStatement(queryGetPersonDodsdato).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { getDate("dodsdato")?.toLocalDate() }
    }
    return datoList.firstOrNull()
}
