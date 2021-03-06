package no.nav.syfo.oppfolgingstilfelle.bit.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.database.NoElementInsertedException
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.POppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.sql.Date
import java.util.*

const val queryCreateOppfolgingstilfellebit =
    """
    INSERT INTO TILFELLE_BIT (
        id,
        uuid,
        created_at,
        inntruffet,
        personident,
        ressurs_id,
        tags,
        virksomhetsnummer,
        fom,
        tom
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
    """

fun Connection.createOppfolgingstilfelleBit(
    commit: Boolean,
    oppfolgingstilfelleBit: OppfolgingstilfelleBit,
) {
    val oppfolgingstilfelleBitIdList = this.prepareStatement(queryCreateOppfolgingstilfellebit).use {
        it.setString(1, oppfolgingstilfelleBit.uuid.toString())
        it.setTimestamp(2, Timestamp.from(oppfolgingstilfelleBit.createdAt.toInstant()))
        it.setTimestamp(3, Timestamp.from(oppfolgingstilfelleBit.inntruffet.toInstant()))
        it.setString(4, oppfolgingstilfelleBit.personIdentNumber.value)
        it.setString(5, oppfolgingstilfelleBit.ressursId)
        it.setString(6, oppfolgingstilfelleBit.tagsToString())
        it.setString(7, oppfolgingstilfelleBit.virksomhetsnummer)
        it.setDate(8, Date.valueOf(oppfolgingstilfelleBit.fom))
        it.setDate(9, Date.valueOf(oppfolgingstilfelleBit.tom))
        it.executeQuery().toList { getInt("id") }
    }

    if (oppfolgingstilfelleBitIdList.size != 1) {
        throw NoElementInsertedException("Creating TILFELLE_BIT failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }
}

const val queryGetOppfolgingstilfelleBitForUUID =
    """
    SELECT *
    FROM TILFELLE_BIT
    WHERE uuid = ?;
    """

fun Connection.getOppfolgingstilfelleBitForUUID(
    uuid: UUID,
): POppfolgingstilfelleBit? = this.prepareStatement(queryGetOppfolgingstilfelleBitForUUID).use {
    it.setString(1, uuid.toString())
    it.executeQuery().toList {
        toPOppfolgingstilfelleBit()
    }
}.firstOrNull()

const val queryGetOppfolgingstilfelleBitList =
    """
    SELECT *
    FROM TILFELLE_BIT
    WHERE personident = ?
    ORDER BY inntruffet DESC;
    """

fun DatabaseInterface.getOppfolgingstilfelleBitList(
    personIdentNumber: PersonIdentNumber,
): List<POppfolgingstilfelleBit> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetOppfolgingstilfelleBitList).use {
            it.setString(1, personIdentNumber.value)
            it.executeQuery().toList {
                toPOppfolgingstilfelleBit()
            }
        }
    }
}

fun ResultSet.toPOppfolgingstilfelleBit(): POppfolgingstilfelleBit =
    POppfolgingstilfelleBit(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        personIdentNumber = PersonIdentNumber(getString("personident")),
        virksomhetsnummer = getString("virksomhetsnummer"),
        createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
        inntruffet = getTimestamp("inntruffet").toOffsetDateTimeUTC(),
        tagList = getString("tags").toTagList(),
        ressursId = getString("ressurs_id"),
        fom = getDate("fom").toLocalDate(),
        tom = getDate("tom").toLocalDate(),
    )
