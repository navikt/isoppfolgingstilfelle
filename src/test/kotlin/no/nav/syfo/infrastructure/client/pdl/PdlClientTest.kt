package no.nav.syfo.infrastructure.client.pdl

import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.PersonIdentNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants

class PdlClientTest {

    private val pdlClient = ExternalMockEnvironment.instance.pdlClient

    @Test
    fun `hentIdenterBolk returns aktiv ident for each ident found in PDL`() {
        val result = runBlocking {
            pdlClient.hentIdenterBolk(listOf(UserConstants.ARBEIDSTAKER_FNR, UserConstants.ARBEIDSTAKER_3_FNR))
        }

        assertEquals(2, result.size)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, result[UserConstants.ARBEIDSTAKER_FNR]?.aktivIdent)
        assertEquals(UserConstants.ARBEIDSTAKER_3_AKTIV_FNR.value, result[UserConstants.ARBEIDSTAKER_3_FNR]?.aktivIdent)
    }

    @Test
    fun `hentIdenterBolk excludes identer not found in PDL`() {
        val result = runBlocking {
            pdlClient.hentIdenterBolk(listOf(UserConstants.ARBEIDSTAKER_FNR, UserConstants.ARBEIDSTAKER_WITH_ERROR))
        }

        assertEquals(1, result.size)
        assertFalse(result.containsKey(UserConstants.ARBEIDSTAKER_WITH_ERROR))
    }

    @Test
    fun `hentIdenterBolk splits request into chunks of max size`() {
        val identer = (1..PdlClient.IDENTER_BOLK_MAX_SIZE + 1).map { PersonIdentNumber(String.format("1%010d", it)) }

        val result = runBlocking { pdlClient.hentIdenterBolk(identer + identer.first()) }

        assertEquals(identer.size, result.size)
        identer.forEach { assertEquals(it.value, result[it]?.aktivIdent) }
    }

    @Test
    fun `hentIdenterBolk returns empty map for empty list`() {
        assertEquals(emptyMap<PersonIdentNumber, PdlIdenter>(), runBlocking { pdlClient.hentIdenterBolk(emptyList()) })
    }
}
