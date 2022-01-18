package no.nav.syfo.oppfolgingstilfelle.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.database.NoElementInsertedException
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfelleArbeidstaker
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

private val mapper = configuredJacksonMapper()

const val queryCreateOppfolgingstilfellePerson =
    """
    INSERT INTO OPPFOLGINGSTILFELLE_ARBEIDSTAKER (
        id,
        uuid,
        created_at,
        personident,
        oppfolgingstilfeller,
        referanse_tilfelle_bit_uuid,
        referanse_tilfelle_bit_inntruffet
    ) values (DEFAULT, ?, ?, ?, ?::jsonb, ?, ?)
    RETURNING id
    """

fun Connection.createOppfolgingstilfelleArbeidstaker(
    commit: Boolean,
    oppfolgingstilfelleArbeidstaker: OppfolgingstilfelleArbeidstaker,
) {
    val idList = this.prepareStatement(queryCreateOppfolgingstilfellePerson).use {
        it.setString(1, oppfolgingstilfelleArbeidstaker.uuid.toString())
        it.setTimestamp(2, Timestamp.from(oppfolgingstilfelleArbeidstaker.createdAt.toInstant()))
        it.setString(3, oppfolgingstilfelleArbeidstaker.personIdentNumber.value)
        it.setObject(4, mapper.writeValueAsString(oppfolgingstilfelleArbeidstaker.oppfolgingstilfelleList))
        it.setString(5, oppfolgingstilfelleArbeidstaker.referanseTilfelleBitUuid.toString())
        it.setTimestamp(6, Timestamp.from(oppfolgingstilfelleArbeidstaker.referanseTilfelleBitInntruffet.toInstant()))
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating OPPFOLGINGSTILFELLE_ARBEIDSTAKER failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }
}

const val queryGetOppfolgingstilfelleArbeidstaker =
    """
        SELECT * 
        FROM OPPFOLGINGSTILFELLE_ARBEIDSTAKER
        WHERE personident = ?
        ORDER BY referanse_tilfelle_bit_inntruffet DESC;
    """

fun DatabaseInterface.getOppfolgingstilfelleArbeidstaker(
    arbeidstakerPersonIdent: PersonIdentNumber,
): POppfolgingstilfelleArbeidstaker? {
    return this.connection.use {
        connection.prepareStatement(queryGetOppfolgingstilfelleArbeidstaker).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList { toPOppfolgingstilfelleArbeidstaker() }
        }
    }.firstOrNull()
}

fun ResultSet.toPOppfolgingstilfelleArbeidstaker(): POppfolgingstilfelleArbeidstaker =
    POppfolgingstilfelleArbeidstaker(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        personIdentNumber = PersonIdentNumber(getString("personident")),
        createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
        referanseTilfelleBitInntruffet = getTimestamp("referanse_tilfelle_bit_inntruffet").toOffsetDateTimeUTC(),
        referanseTilfelleBitUUID = UUID.fromString(getString("referanse_tilfelle_bit_uuid")),
        oppfolgingstilfeller = mapper.readValue(
            getString("oppfolgingstilfeller"),
            object : TypeReference<List<Oppfolgingstilfelle>>() {}
        ),
    )
