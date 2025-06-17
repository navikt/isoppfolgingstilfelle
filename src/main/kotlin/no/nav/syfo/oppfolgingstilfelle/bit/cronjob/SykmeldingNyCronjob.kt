package no.nav.syfo.oppfolgingstilfelle.bit.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.arbeidsforhold.ArbeidsforholdClient
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import org.slf4j.LoggerFactory

class SykmeldingNyCronjob(
    private val database: DatabaseInterface,
    private val arbeidsforholdClient: ArbeidsforholdClient,
) : Cronjob {
    override val initialDelayMinutes: Long = 7
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed sykmelding-ny processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob() = CronjobResult().also { result ->
        val notReady = database.getNotReadyOppfolgingstilfelleBitList().toOppfolgingstilfelleBitList()
        notReady.forEach { oppfolgingstilfelleBit ->
            try {
                val arbeidsforhold = arbeidsforholdClient.getArbeidsforhold(oppfolgingstilfelleBit.personIdentNumber)
                val orgnr = arbeidsforhold.find {
                    val periode = it.ansettelsesperiode
                    it.arbeidssted.getOrgnummer() != null &&
                            periode.startdato.isBefore(oppfolgingstilfelleBit.tom) &&
                            (periode.sluttdato == null || periode.sluttdato.isAfter(oppfolgingstilfelleBit.tom))
                }?.arbeidssted?.getOrgnummer()
                database.connection.use { connection ->
                    orgnr?.let {
                        connection.setVirksomhetsnummerOppfolgingstilfelleBit(
                            uuid = oppfolgingstilfelleBit.uuid,
                            orgnr = it,
                        )
                    }
                    connection.setReadyOppfolgingstilfelleBit(oppfolgingstilfelleBit.uuid)
                    connection.commit()
                }
                result.updated++
            } catch (exc: Exception) {
                log.error("caught exception when processing oppfolgingstilfelleBit", exc)
                result.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingNyCronjob::class.java)
    }
}
