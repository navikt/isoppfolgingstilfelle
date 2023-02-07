package no.nav.syfo.oppfolgingstilfelle.bit.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit.Companion.TAG_PRIORITY
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebit
import no.nav.syfo.oppfolgingstilfelle.person.domain.*
import no.nav.syfo.util.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

enum class Tag {
    SYKMELDING,
    NY,
    BEKREFTET,
    SENDT,
    KORRIGERT,
    AVBRUTT,
    UTGAATT,
    PERIODE,
    FULL_AKTIVITET,
    INGEN_AKTIVITET,
    GRADERT_AKTIVITET,
    BEHANDLINGSDAGER,
    BEHANDLINGSDAG,
    ANNET_FRAVAR,
    SYKEPENGESOKNAD,
    FERIE,
    PERMISJON,
    OPPHOLD_UTENFOR_NORGE,
    EGENMELDING,
    FRAVAR_FOR_SYKMELDING,
    PAPIRSYKMELDING,
    ARBEID_GJENNOPPTATT,
    KORRIGERT_ARBEIDSTID,
    UKJENT_AKTIVITET,
    UTDANNING,
    FULLTID,
    DELTID,
    REDUSERT_ARBEIDSGIVERPERIODE,
    REISETILSKUDD,
    AVVENTENDE,
    INNTEKTSMELDING,
    ARBEIDSGIVERPERIODE,
}

data class OppfolgingstilfelleBit(
    val uuid: UUID,
    val personIdentNumber: PersonIdentNumber,
    val virksomhetsnummer: String? = null,
    val createdAt: OffsetDateTime,
    val inntruffet: OffsetDateTime,
    val tagList: List<Tag>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val ready: Boolean = true,
    val processed: Boolean = true,
    val korrigerer: UUID?,
) {
    companion object {
        val TAG_PRIORITY: List<ListContainsPredicate<Tag>> = listOf(
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.ARBEID_GJENNOPPTATT,
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.KORRIGERT_ARBEIDSTID and Tag.BEHANDLINGSDAGER,
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.KORRIGERT_ARBEIDSTID and Tag.FULL_AKTIVITET,
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.KORRIGERT_ARBEIDSTID and (Tag.GRADERT_AKTIVITET or Tag.INGEN_AKTIVITET),
            Tag.SYKEPENGESOKNAD and Tag.SENDT and (Tag.PERMISJON or Tag.FERIE),
            Tag.SYKEPENGESOKNAD and Tag.SENDT and (Tag.EGENMELDING or Tag.PAPIRSYKMELDING or Tag.FRAVAR_FOR_SYKMELDING),
            Tag.SYKEPENGESOKNAD and Tag.SENDT and ListContainsPredicate.tagsSize(2),
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.BEHANDLINGSDAG,
            Tag.SYKEPENGESOKNAD and Tag.SENDT and Tag.BEHANDLINGSDAGER,
            Tag.INNTEKTSMELDING and Tag.ARBEIDSGIVERPERIODE,
            Tag.SYKMELDING and Tag.SENDT and Tag.PERIODE and Tag.BEHANDLINGSDAGER,
            Tag.SYKMELDING and Tag.SENDT and Tag.PERIODE and Tag.FULL_AKTIVITET,
            Tag.SYKMELDING and Tag.SENDT and Tag.PERIODE and (Tag.GRADERT_AKTIVITET or Tag.INGEN_AKTIVITET),
            Tag.SYKMELDING and Tag.BEKREFTET and Tag.PERIODE and Tag.BEHANDLINGSDAGER,
            Tag.SYKMELDING and Tag.BEKREFTET and Tag.PERIODE and Tag.FULL_AKTIVITET,
            Tag.SYKMELDING and Tag.BEKREFTET and Tag.PERIODE and (Tag.GRADERT_AKTIVITET or Tag.INGEN_AKTIVITET),
            Tag.SYKMELDING and Tag.BEKREFTET and Tag.ANNET_FRAVAR,
            Tag.SYKMELDING and Tag.SENDT and Tag.PERIODE and Tag.REISETILSKUDD and Tag.UKJENT_AKTIVITET,
            Tag.SYKMELDING and Tag.NY and Tag.PERIODE and Tag.BEHANDLINGSDAGER,
            Tag.SYKMELDING and Tag.NY and Tag.PERIODE and Tag.FULL_AKTIVITET,
            Tag.SYKMELDING and Tag.NY and Tag.PERIODE and (Tag.GRADERT_AKTIVITET or Tag.INGEN_AKTIVITET),
            Tag.SYKMELDING and Tag.NY and Tag.PERIODE and Tag.REISETILSKUDD and Tag.UKJENT_AKTIVITET,
        )
    }
}

fun List<OppfolgingstilfelleBit>.generateOppfolgingstilfelleList(): List<Oppfolgingstilfelle> {
    val filtrertOppfolgingstilfelleBitList = this.filtrerOppfolgingstilfelleBitList()
    return if (filtrertOppfolgingstilfelleBitList.isEmpty()) {
        emptyList()
    } else {
        val oppfolgingstilfelleBiterPerVirksomhet = filtrertOppfolgingstilfelleBitList.groupBy { it.virksomhetsnummer }
        val oppfolgingstilfelleDagerPerVirksomhet =
            oppfolgingstilfelleBiterPerVirksomhet.mapValues { it.value.toOppfolgingstilfelleDagList() }

        filtrertOppfolgingstilfelleBitList.toOppfolgingstilfelleDagList()
            .groupOppfolgingstilfelleList(oppfolgingstilfelleDagerPerVirksomhet)
    }
}

private fun List<OppfolgingstilfelleBit>.filtrerOppfolgingstilfelleBitList(): List<OppfolgingstilfelleBit> {
    val korrigerte = this.mapNotNull { bit -> bit.korrigerer?.toString() }
    return this.filter { bit -> !korrigerte.contains(bit.ressursId) }
}

private fun List<OppfolgingstilfelleBit>.toOppfolgingstilfelleDagList(): List<OppfolgingstilfelleDag> {
    require(this.isNotEmpty())

    val firstFom = this.firstFom()
    val lastTom = this.lastTom()

    val numberOfDaysInOppfolgingstilleTimeline = (0..ChronoUnit.DAYS.between(firstFom, lastTom))

    return numberOfDaysInOppfolgingstilleTimeline
        .map { day ->
            this.pickOppfolgingstilfelleDag(
                dag = firstFom.plusDays(day),
            )
        }
}

fun List<OppfolgingstilfelleBit>.firstFom(): LocalDate =
    this.minByOrNull { it.fom }!!.fom

fun List<OppfolgingstilfelleBit>.lastTom(): LocalDate =
    this.maxByOrNull { it.tom }!!.tom

fun List<OppfolgingstilfelleBit>.pickOppfolgingstilfelleDag(
    dag: LocalDate,
): OppfolgingstilfelleDag {
    val bitListForDag = this.filter { bit ->
        dag in (bit.fom..(bit.tom))
    }.filter { bit ->
        bit.findTagPriorityElementOrNull() != null
    }.toMutableList().apply {
        sortByDescending { bit -> bit.inntruffet }
        sortByTagPriority()
    }
    return OppfolgingstilfelleDag(
        dag = dag,
        priorityOppfolgingstilfelleBit = bitListForDag.firstOrNull(),
        virksomhetsnummerPreferred = bitListForDag.getVirksomhetsnummerPreferred(),
        virksomhetsnummerAll = bitListForDag.getVirksomhetsnummerAll(),
    )
}

fun List<OppfolgingstilfelleBit>.getVirksomhetsnummerPreferred() =
    this.filter { bit -> !(bit.tagList in (Tag.SYKMELDING and Tag.NY)) }
        .mapNotNull { bit -> bit.virksomhetsnummer }.distinct()

fun List<OppfolgingstilfelleBit>.getVirksomhetsnummerAll() =
    this.mapNotNull { bit -> bit.virksomhetsnummer }.distinct()

fun List<OppfolgingstilfelleBit>.containsSendtSykmeldingBit(
    oppfolgingstilfelleBit: OppfolgingstilfelleBit,
) = this.any { bit ->
    bit.ressursId == oppfolgingstilfelleBit.ressursId &&
        bit.tagList in (Tag.SYKMELDING and (Tag.SENDT or Tag.BEKREFTET))
}

fun MutableList<OppfolgingstilfelleBit>.sortByTagPriority() {
    this.sortBy { bit -> bit.findTagPriority() }
}

fun OppfolgingstilfelleBit.findTagPriority() =
    this.findTagPriorityElementOrNull()?.let {
        TAG_PRIORITY.indexOf(it)
    } ?: TAG_PRIORITY.size

fun OppfolgingstilfelleBit.findTagPriorityElementOrNull() =
    TAG_PRIORITY.find { tagPriorityElement ->
        this.tagList in tagPriorityElement
    }

fun OppfolgingstilfelleBit.tagsToString() = this.tagList.joinToString(",")

fun String.toTagList(): List<Tag> = split(',').map(String::trim).map(Tag::valueOf)

fun KafkaSyketilfellebit.toOppfolgingstilfelleBit(): OppfolgingstilfelleBit {
    return OppfolgingstilfelleBit(
        uuid = UUID.fromString(this.id),
        personIdentNumber = PersonIdentNumber(this.fnr),
        virksomhetsnummer = this.orgnummer,
        createdAt = nowUTC(),
        inntruffet = this.inntruffet,
        tagList = this.tags.map { tag -> Tag.valueOf(tag) },
        ressursId = this.ressursId,
        fom = this.fom,
        tom = this.tom,
        ready = !this.tags.containsAll(listOf(Tag.SYKMELDING.name, Tag.NY.name)),
        processed = false,
        korrigerer = this.korrigererSendtSoknad?.let { UUID.fromString(it) },
    )
}

fun OppfolgingstilfelleBit.isArbeidstakerBit(): Boolean = this.virksomhetsnummer != null

fun OppfolgingstilfelleBit.isGradert(): Boolean = this.tagList.any {
    listOf(
        Tag.GRADERT_AKTIVITET,
        Tag.FULL_AKTIVITET,
    ).contains(it)
}

fun OppfolgingstilfelleBit.toOppfolgingstilfellePerson(
    oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    dodsdato: LocalDate? = null,
) = OppfolgingstilfellePerson(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    personIdentNumber = this.personIdentNumber,
    oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList(),
    referanseTilfelleBitUuid = this.uuid,
    referanseTilfelleBitInntruffet = this.inntruffet,
    dodsdato = dodsdato,
)
