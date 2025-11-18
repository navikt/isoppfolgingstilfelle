package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IOppfolgingstilfelleRepository
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class OppfolgingstilfellePersonRepository(private val database: DatabaseInterface) : IOppfolgingstilfelleRepository {

    override fun createOppfolgingstilfellePerson(
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    ) {
        database.connection.use { connection ->
            val idList = connection.prepareStatement(QUERY_CREATE_OPPFOLGINGSTILFELLE_PERSON).use {
                it.setString(1, oppfolgingstilfellePerson.uuid.toString())
                it.setTimestamp(2, Timestamp.from(oppfolgingstilfellePerson.createdAt.toInstant()))
                it.setString(3, oppfolgingstilfellePerson.personIdentNumber.value)
                it.setObject(4, mapper.writeValueAsString(oppfolgingstilfellePerson.oppfolgingstilfelleList))
                it.setString(5, oppfolgingstilfellePerson.referanseTilfelleBitUuid.toString())
                it.setTimestamp(6, Timestamp.from(oppfolgingstilfellePerson.referanseTilfelleBitInntruffet.toInstant()))
                if (oppfolgingstilfellePerson.hasGjentakendeSykefravar != null) {
                    it.setBoolean(7, oppfolgingstilfellePerson.hasGjentakendeSykefravar)
                } else {
                    it.setNull(7, Types.BOOLEAN)
                }
                it.executeQuery().toList { getInt("id") }
            }
            if (idList.size != 1) {
                throw NoElementInsertedException("Creating OPPFOLGINGSTILFELLE_PERSON failed, no rows affected.")
            }
            connection.commit()
        }
    }

    override fun getOppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): POppfolgingstilfellePerson? =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_OPPFOLGINGSTILFELLE_PERSON).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPOppfolgingstilfellePerson() }
            }.firstOrNull()
        }

    override fun getDodsdato(
        personident: PersonIdentNumber,
    ): LocalDate? {
        val datoList = database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_PERSON_DODSDATO).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { getDate("dodsdato")?.toLocalDate() }
            }
        }
        return datoList.firstOrNull()
    }

    override fun createPerson(
        uuid: UUID,
        personIdent: PersonIdentNumber,
        dodsdato: LocalDate,
        hendelseId: UUID,
    ) {
        database.connection.use { connection ->
            val idList = connection.prepareStatement(QUERY_INSERT_PERSON_DODSDATO).use {
                it.setString(1, uuid.toString())
                it.setObject(2, OffsetDateTime.now())
                it.setString(3, personIdent.value)
                it.setDate(4, Date.valueOf(dodsdato))
                it.setString(5, hendelseId.toString())
                it.executeQuery().toList { getInt("id") }
            }

            if (idList.size != 1) {
                throw NoElementInsertedException("Creating PERSON failed, no rows affected.")
            }
            connection.commit()
        }
    }

    override fun deletePersonWithHendelseId(
        hendelseId: UUID,
    ): Int =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_DELETE_PERSON_WITH_HENDELSE_ID).use {
                it.setString(1, hendelseId.toString())
                it.executeUpdate()
            }.also {
                connection.commit()
            }
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
                    referanse_tilfelle_bit_inntruffet,
                    has_gjentakende_sykefravar
                ) values (DEFAULT, ?, ?, ?, ?::jsonb, ?, ?, ?)
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

        private const val QUERY_GET_PERSON_DODSDATO =
            """
                SELECT DODSDATO FROM PERSON WHERE personident=?
            """

        private const val QUERY_INSERT_PERSON_DODSDATO =
            """
                INSERT INTO PERSON (
                id,
                uuid,
                created_at,
                personident,
                dodsdato,
                hendelse_id
                ) values (DEFAULT, ?, ?, ?, ?, ?)
                RETURNING id    
            """

        private const val QUERY_DELETE_PERSON_WITH_HENDELSE_ID =
            """
                DELETE FROM PERSON WHERE hendelse_id=?    
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
        hasGjentakendeSykefravar = getBoolean("has_gjentakende_sykefravar"),
    )
