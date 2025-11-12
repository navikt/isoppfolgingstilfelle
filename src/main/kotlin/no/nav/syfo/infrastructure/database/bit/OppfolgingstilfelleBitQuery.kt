package no.nav.syfo.infrastructure.database.bit

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection
import java.util.*

const val queryGetNotReadyOppfolgingstilfelleBitList =
    """
    SELECT *
    FROM TILFELLE_BIT
    WHERE NOT ready
    ORDER BY inntruffet ASC, id ASC
    LIMIT 4000;
    """

fun DatabaseInterface.getNotReadyOppfolgingstilfelleBitList() =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetNotReadyOppfolgingstilfelleBitList).use {
            it.executeQuery().toList {
                toPOppfolgingstilfelleBit()
            }
        }
    }

const val querySetVirksomhetsnummerOppfolgingstilfelleBit =
    """
    UPDATE TILFELLE_BIT 
    SET virksomhetsnummer=?
    WHERE uuid=?
    """

fun Connection.setVirksomhetsnummerOppfolgingstilfelleBit(
    uuid: UUID,
    orgnr: String,
) = this.prepareStatement(querySetVirksomhetsnummerOppfolgingstilfelleBit).use {
    it.setString(1, orgnr)
    it.setString(2, uuid.toString())
    it.executeUpdate()
}.also { updateCount ->
    if (updateCount != 1) {
        throw RuntimeException("Unexpected update count: $updateCount")
    }
}

const val querySetReadyOppfolgingstilfelleBit =
    """
    UPDATE TILFELLE_BIT 
    SET ready=true
    WHERE uuid=?
    """

fun Connection.setReadyOppfolgingstilfelleBit(uuid: UUID) =
    this.prepareStatement(querySetReadyOppfolgingstilfelleBit).use {
        it.setString(1, uuid.toString())
        it.executeUpdate()
    }.also { updateCount ->
        if (updateCount != 1) {
            throw RuntimeException("Unexpected update count: $updateCount")
        }
    }
