package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.PreparedStatement

const val queryUpdateTilfelleBit =
    """
        UPDATE TILFELLE_BIT
        SET personident = ?, processed = FALSE
        WHERE personident = ?
    """

fun Connection.updateTilfelleBit(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    return this.updateIdent(
        query = queryUpdateTilfelleBit,
        nyPersonident = nyPersonident,
        inactiveIdenter = inactiveIdenter,
        commit = commit,
    )
}

const val queryUpdateOppfolgingstilfellePerson =
    """
        UPDATE OPPFOLGINGSTILFELLE_PERSON
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updateOppfolgingstilfellePerson(
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    return this.updateIdent(
        query = queryUpdateOppfolgingstilfellePerson,
        nyPersonident = nyPersonident,
        inactiveIdenter = inactiveIdenter,
        commit = commit,
    )
}

fun Connection.updateIdent(
    query: String,
    nyPersonident: PersonIdentNumber,
    inactiveIdenter: List<PersonIdentNumber>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(query).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryGetIdentCount =
    """
        SELECT COUNT(*)
        FROM (
            SELECT personident FROM TILFELLE_BIT
            UNION ALL
            SELECT personident FROM OPPFOLGINGSTILFELLE_PERSON
        ) identer
        WHERE personident = ?
    """

fun DatabaseInterface.getIdentCount(
    identer: List<PersonIdentNumber>,
): Int =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetIdentCount).use<PreparedStatement, Int> {
            var count = 0
            identer.forEach { ident ->
                it.setString(1, ident.value)
                count += it.executeQuery().toList { getInt(1) }.firstOrNull() ?: 0
            }
            return count
        }
    }

const val queryUpdatePersonidentOnTilfellebit =
    """
        UPDATE TILFELLE_BIT
        SET personident = ?
        WHERE personident = ?
    """

fun Connection.updatePersonidentOnTilfelleBit(
    nyPersonident: PersonIdentNumber,
    inactiveIdent: PersonIdentNumber,
): Int = this.updateIdent(
    query = queryUpdatePersonidentOnTilfellebit,
    nyPersonident = nyPersonident,
    inactiveIdenter = listOf(inactiveIdent),
)

const val querySetNewestProcessedTilfelleBitUnprocessed =
    """
        UPDATE TILFELLE_BIT
        SET processed = FALSE
        WHERE id = (
            SELECT id FROM TILFELLE_BIT
            WHERE personident = ? AND processed
            ORDER BY inntruffet DESC, id DESC
            LIMIT 1
        )
        AND NOT EXISTS (
            SELECT 1 FROM TILFELLE_BIT
            WHERE personident = ? AND ready AND NOT processed
        )
    """

// Not needed if there already is a tilfellebit for the personident that will be picked up by OppfolgingstilfelleCronjob
fun Connection.setNewestProcessedTilfelleBitUnprocessed(
    personident: PersonIdentNumber,
): Int = this.prepareStatement(querySetNewestProcessedTilfelleBitUnprocessed).use {
    it.setString(1, personident.value)
    it.setString(2, personident.value)
    it.executeUpdate()
}
