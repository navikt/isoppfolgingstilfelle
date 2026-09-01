package no.nav.syfo.infrastructure.kafka

data class KafkaStartOppfolgingMelding(
    val personident: String,
    val aarsak: Aarsak,
    val kilde: Kilde,
    val arbeidsoppfolgingskontor: String?,
    val registrant: Registrant,
) {
    enum class Aarsak {
        SYKMELDT_UTEN_ARBEIDSGIVER_4_UKER,
    }

    enum class Kilde {
        ISYFO,
    }

    data class Registrant(
        val type: String,
        val opprettetAv: String,
    ) {
        companion object {
            private const val TYPE_SYSTEM = "SYSTEM"

            fun system(opprettetAv: String) = Registrant(
                type = TYPE_SYSTEM,
                opprettetAv = opprettetAv,
            )
        }
    }
}
