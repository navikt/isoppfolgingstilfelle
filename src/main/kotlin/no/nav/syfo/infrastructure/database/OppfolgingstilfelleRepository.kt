package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

private val mapper = configuredJacksonMapper()

class OppfolgingstilfelleRepository(private val database: DatabaseInterface) {

    fun createOppfolgingstilfellePerson(
        connection: Connection,
        commit: Boolean,
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    ) {
        val idList = connection.prepareStatement(QUERY_CREATE_OPPFOLGINGSTILFELLE_PERSON).use {
            it.setString(1, oppfolgingstilfellePerson.uuid.toString())
            it.setTimestamp(2, Timestamp.from(oppfolgingstilfellePerson.createdAt.toInstant()))
            it.setString(3, oppfolgingstilfellePerson.personIdentNumber.value)
            it.setObject(4, mapper.writeValueAsString(oppfolgingstilfellePerson.oppfolgingstilfelleList))
            it.setString(5, oppfolgingstilfellePerson.referanseTilfelleBitUuid.toString())
            it.setTimestamp(6, Timestamp.from(oppfolgingstilfellePerson.referanseTilfelleBitInntruffet.toInstant()))
            it.executeQuery().toList { getInt("id") }
        }
        if (idList.size != 1) {
            throw NoElementInsertedException("Creating OPPFOLGINGSTILFELLE_PERSON failed, no rows affected.")
        }
        if (commit) connection.commit()
    }

    fun getOppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): POppfolgingstilfellePerson? =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_OPPFOLGINGSTILFELLE_PERSON).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPOppfolgingstilfellePerson() }
            }.firstOrNull()
        }

    companion object {
        private const val QUERY_CREATE_OPPFOLGINGSTILFELLE_PERSON =
            """
                INSERT INTO OPPFOLGINGSTILFELLE_PERSON (
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

        private const val QUERY_GET_OPPFOLGINGSTILFELLE_PERSON =
            """
                SELECT * 
                FROM OPPFOLGINGSTILFELLE_PERSON
                WHERE personident = ?
                ORDER BY referanse_tilfelle_bit_inntruffet DESC, id DESC
                LIMIT 1
            """
    }
}

private fun ResultSet.toPOppfolgingstilfellePerson(): POppfolgingstilfellePerson =
    POppfolgingstilfellePerson(
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
