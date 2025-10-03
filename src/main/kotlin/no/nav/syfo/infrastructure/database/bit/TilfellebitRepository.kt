package no.nav.syfo.infrastructure.database.bit

import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.tagsToString
import no.nav.syfo.domain.toTagList
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

class TilfellebitRepository(private val database: DatabaseInterface) {

    fun createOppfolgingstilfelleBit(
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
    ) {
        database.connection.use { connection ->
            val oppfolgingstilfelleBitIdList = connection.prepareStatement(QUERY_CREATE_TILFELLE_BIT).use {
                it.setString(1, oppfolgingstilfelleBit.uuid.toString())
                it.setTimestamp(2, Timestamp.from(oppfolgingstilfelleBit.createdAt.toInstant()))
                it.setTimestamp(3, Timestamp.from(oppfolgingstilfelleBit.inntruffet.toInstant()))
                it.setString(4, oppfolgingstilfelleBit.personIdentNumber.value)
                it.setString(5, oppfolgingstilfelleBit.ressursId)
                it.setString(6, oppfolgingstilfelleBit.tagsToString())
                it.setString(7, oppfolgingstilfelleBit.virksomhetsnummer)
                it.setDate(8, Date.valueOf(oppfolgingstilfelleBit.fom))
                it.setDate(9, Date.valueOf(oppfolgingstilfelleBit.tom))
                it.setBoolean(10, oppfolgingstilfelleBit.ready)
                it.setBoolean(11, oppfolgingstilfelleBit.processed)
                it.setString(12, oppfolgingstilfelleBit.korrigerer?.toString())
                it.executeQuery().toList { getInt("id") }
            }

            if (oppfolgingstilfelleBitIdList.size != 1) {
                throw NoElementInsertedException("Creating TILFELLE_BIT failed, no rows affected.")
            }
            connection.commit()
        }
    }

    fun getOppfolgingstilfelleBit(uuid: UUID): POppfolgingstilfelleBit? =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_OPPFOLGINGSTILFELLE_BIT_FOR_UUID).use<PreparedStatement, List<POppfolgingstilfelleBit>> {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPOppfolgingstilfelleBit() }
            }.firstOrNull()
        }

    fun getOppfolgingstilfelleBitForRessursId(
        ressursId: String,
    ): List<POppfolgingstilfelleBit> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_TILFELLE_BIT_FOR_RESSURS_ID).use {
                it.setString(1, ressursId)
                it.executeQuery().toList {
                    toPOppfolgingstilfelleBit()
                }
            }
        }

    fun getUnprocessedOppfolgingstilfelleBitList() =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_UNPROCESSED_TILFELLE_BIT_LIST).use {
                it.executeQuery().toList {
                    toPOppfolgingstilfelleBit()
                }
            }
        }

    companion object {
        private const val QUERY_GET_OPPFOLGINGSTILFELLE_BIT_FOR_UUID =
            """
            SELECT *
            FROM TILFELLE_BIT
            WHERE uuid = ?;
            """

        private const val QUERY_CREATE_TILFELLE_BIT =
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
                tom,
                ready,
                processed,
                korrigerer
                ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
            """

        private const val QUERY_GET_TILFELLE_BIT_FOR_RESSURS_ID =
            """
                SELECT *
                FROM TILFELLE_BIT
                WHERE ressurs_id = ?;
            """

        private const val QUERY_GET_UNPROCESSED_TILFELLE_BIT_LIST =
            """
                SELECT *
                FROM TILFELLE_BIT
                WHERE ready AND NOT processed
                ORDER BY inntruffet ASC, id ASC 
                LIMIT 4000;
            """
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
        ready = getBoolean("ready"),
        processed = getBoolean("processed"),
        korrigerer = getString("korrigerer"),
    )
