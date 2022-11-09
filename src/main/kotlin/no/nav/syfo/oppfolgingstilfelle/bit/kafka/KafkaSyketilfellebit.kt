package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import java.time.LocalDate
import java.time.OffsetDateTime

data class KafkaSyketilfellebit(
    val id: String,
    val fnr: String,
    val orgnummer: String?,
    val opprettet: OffsetDateTime,
    val inntruffet: OffsetDateTime,
    val tags: List<String>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val korrigererSendtSoknad: String?,
)

// TODO: Beskrive hva dette er: Bit for bekreftet sykmelding er for en person som er arbeidsledig, permittert, frilanser, selvstendig næringsdrivene eller annet(hvor fellesnevner er at det ikke er snakk om en arbeidstaker)
fun KafkaSyketilfellebit.isRelevantForOppfolgingstilfelle(): Boolean =
    (this.orgnummer != null && !this.tags.contains(Tag.INNTEKTSMELDING.name)) ||
    this.tags.containsAll(listOf(Tag.SYKMELDING.name, Tag.BEKREFTET.name))
