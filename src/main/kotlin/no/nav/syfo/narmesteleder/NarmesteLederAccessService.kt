package no.nav.syfo.narmesteleder

import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

class NarmesteLederAccessService(private val narmesteLederClient: NarmesteLederClient) {
    suspend fun isNarmesteLederForArbeidstakerInVirksomhet(
        arbeidstakerPersonIdentNumber: PersonIdentNumber,
        callId: String,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
        tokenx: String,
        virksomhetsnummer: Virksomhetsnummer
    ): Boolean {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(
            narmesteLederIdent = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            callId = callId,
        )

        println("___narm: $aktiveAnsatteRelasjoner")

        return aktiveAnsatteRelasjoner.any { relasjon ->
            relasjon.arbeidstakerPersonIdentNumber == arbeidstakerPersonIdentNumber.value && relasjon.virksomhetsnummer == virksomhetsnummer.value
        }
    }
}
