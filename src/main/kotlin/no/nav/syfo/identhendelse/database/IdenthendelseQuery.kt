package no.nav.syfo.identhendelse.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.POppfolgingstilfelleBit

const val queryUpdateTilfelleBitFnr =
    """
        UPDATE TILFELLE_BIT
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateTilfelleBitFnr(nyPersonident: PersonIdentNumber, tilfelleBitWithOldIdentList: List<POppfolgingstilfelleBit>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateTilfelleBitFnr).use {
            tilfelleBitWithOldIdentList.forEach { tilfelleBit ->
                it.setString(1, nyPersonident.value)
                it.setString(2, tilfelleBit.personIdentNumber.value)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}
