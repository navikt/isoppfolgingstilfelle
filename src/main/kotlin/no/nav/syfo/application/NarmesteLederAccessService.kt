package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient

class NarmesteLederAccessService(private val narmesteLederClient: NarmesteLederClient) {
    suspend fun isNarmesteLederForArbeidstakerInVirksomhet(
        arbeidstakerPersonIdentNumber: PersonIdentNumber,
        callId: String,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
        tokenx: String,
        virksomhetsnummer: Virksomhetsnummer,
    ): Boolean {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(
            narmesteLederIdent = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            callId = callId,
        )

        return aktiveAnsatteRelasjoner.any { relasjon ->
            relasjon.arbeidstakerPersonIdentNumber == arbeidstakerPersonIdentNumber.value && relasjon.virksomhetsnummer == virksomhetsnummer.value
        }
    }
}
