package testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.personhendelse.db.getDodsdato
import org.flywaydb.core.Flyway
import java.sql.Connection

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply {
            autoCommit = false
        }

    init {
        pg = try {
            EmbeddedPostgres.start()
        } catch (e: Exception) {
            EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
        }

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        """
        DELETE FROM TILFELLE_BIT
        """.trimIndent(),
        """
        DELETE FROM TILFELLE_BIT_DELETED
        """.trimIndent(),
        """
        DELETE FROM OPPFOLGINGSTILFELLE_PERSON
        """.trimIndent(),
        """
        DELETE FROM PERSON
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.getDodsdato(
    personIdent: PersonIdentNumber
) = this.connection.use {
    it.getDodsdato(personIdent)
}

fun DatabaseInterface.countDeletedTilfelleBit() =
    this.connection.use { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM TILFELLE_BIT_DELETED").use { ps ->
            val resultSet = ps.executeQuery().also { it.next() }
            resultSet.getInt(1)
        }
    }
