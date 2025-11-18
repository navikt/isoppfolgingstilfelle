package no.nav.syfo.infrastructure.database.bit

import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.tagsToString
import no.nav.syfo.domain.toTagList
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.toOffsetDateTimeUTC
import org.slf4j.LoggerFactory
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
import java.util.*

class TilfellebitRepository(private val database: DatabaseInterface) {

    fun createOppfolgingstilfelleBit(
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
    ) {
        database.connection.use { connection ->
            try {
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
            } catch (e: SQLException) {
                connection.rollback()
                log.error("Error creating OppfolgingstilfelleBit with uuid=${oppfolgingstilfelleBit.uuid}", e)
            }
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

    fun createOppfolgingstilfelleBitAvbrutt(
        pOppfolgingstilfelleBit: POppfolgingstilfelleBit,
        inntruffet: OffsetDateTime,
    ) {
        database.connection.use { connection ->
            val now = OffsetDateTime.now()
            val oppfolgingstilfelleBitIdList = connection.prepareStatement(QUERY_CREATE_TILFELLE_BIT_AVBRUTT).use {
                it.setString(1, UUID.randomUUID().toString())
                it.setObject(2, now)
                it.setObject(3, now)
                it.setInt(4, pOppfolgingstilfelleBit.id)
                it.setObject(5, inntruffet)
                it.setBoolean(6, true)
                it.executeQuery().toList { getInt("id") }
            }

            if (oppfolgingstilfelleBitIdList.size != 1) {
                throw NoElementInsertedException("Creating TILFELLE_BIT_AVBRUTT failed, no rows affected.")
            }

            connection.commit()
        }
    }

    fun getProcessedOppfolgingstilfelleBitList(
        personIdentNumber: PersonIdentNumber,
        includeAvbrutt: Boolean = false,
    ): List<POppfolgingstilfelleBit> =
        database.connection.use { connection ->
            connection.prepareStatement(
                if (includeAvbrutt)
                    QUERY_GET_ALL_PROCESSED_TILFELLE_BITER
                else
                    QUERY_GET_PROCESSED_TILFELLE_BITER_AVBRUTT_EXCLUDED
            ).use {
                it.setString(1, personIdentNumber.value)
                it.executeQuery().toList {
                    toPOppfolgingstilfelleBit()
                }
            }
        }

    fun setProcessedOppfolgingstilfelleBit(
        uuid: UUID,
        processed: Boolean = true,
    ) =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_SET_PROCESSED_TILFELLE_BIT).use {
                it.setBoolean(1, processed)
                it.setString(2, uuid.toString())
                it.executeUpdate()
            }.also { updateCount ->
                if (updateCount != 1) {
                    throw RuntimeException("Unexpected update count: $updateCount")
                }
            }
            connection.commit()
        }

    fun deleteOppfolgingstilfelleBit(
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
    ) {
        database.connection.use { connection ->
            connection.registerDeletedTilfelleBit(oppfolgingstilfelleBit)
            connection.prepareStatement(QUERY_DELETE_TILFELLE_BIT).use {
                it.setString(1, oppfolgingstilfelleBit.uuid.toString())
                it.executeUpdate()
            }.also { updateCount ->
                if (updateCount != 1) {
                    throw RuntimeException("Unexpected update count: $updateCount")
                }
            }
            connection.commit()
        }
    }

    private fun Connection.registerDeletedTilfelleBit(oppfolgingstilfelleBit: OppfolgingstilfelleBit) {
        val oppfolgingstilfelleBitIdList = this.prepareStatement(QUERY_INSERT_TILFELLE_BIT_DELETED).use {
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
    }

    fun isTilfelleBitAvbrutt(tilfelleBitId: UUID): Boolean =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_IS_TILFELLE_BIT_AVBRUTT).use {
                it.setString(1, tilfelleBitId.toString())
                val rs = it.executeQuery()
                if (rs.next()) rs.getBoolean(1) else false
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

        private const val QUERY_CREATE_TILFELLE_BIT_AVBRUTT =
            """
                INSERT INTO TILFELLE_BIT_AVBRUTT (
                    id,
                    uuid,
                    created_at,
                    updated_at,
                    tilfelle_bit_id,
                    inntruffet,
                    avbrutt
                ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)
                RETURNING id
            """

        private const val QUERY_GET_PROCESSED_TILFELLE_BITER_AVBRUTT_EXCLUDED =
            """
                SELECT t.*
                FROM TILFELLE_BIT t
                WHERE personident = ? AND processed AND NOT EXISTS (
                    SELECT ID FROM TILFELLE_BIT_AVBRUTT WHERE tilfelle_bit_id = t.id AND avbrutt 
                )
                ORDER BY inntruffet DESC, id DESC;
            """

        private const val QUERY_GET_ALL_PROCESSED_TILFELLE_BITER =
            """
                SELECT t.*
                FROM TILFELLE_BIT t
                WHERE personident = ? AND processed
                ORDER BY inntruffet DESC, id DESC;
            """

        private const val QUERY_SET_PROCESSED_TILFELLE_BIT =
            """
                UPDATE TILFELLE_BIT 
                SET processed=?
                WHERE uuid=?
            """

        private const val QUERY_DELETE_TILFELLE_BIT =
            """
                DELETE
                FROM TILFELLE_BIT
                WHERE uuid = ?;
            """

        private const val QUERY_INSERT_TILFELLE_BIT_DELETED =
            """
                INSERT INTO TILFELLE_BIT_DELETED (
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

        private const val QUERY_IS_TILFELLE_BIT_AVBRUTT =
            """
                SELECT avbrutt 
                FROM TILFELLE_BIT_AVBRUTT a INNER JOIN TILFELLE_BIT t ON (t.id = a.tilfelle_bit_id) 
                WHERE t.uuid=?
            """

        private val log = LoggerFactory.getLogger(TilfellebitRepository::class.java)
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
