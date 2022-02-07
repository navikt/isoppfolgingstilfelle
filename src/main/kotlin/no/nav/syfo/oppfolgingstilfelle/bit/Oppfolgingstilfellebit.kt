package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit.Companion.TAG_PRIORITY
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebit
import no.nav.syfo.oppfolgingstilfelle.domain.*
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
            Tag.SYKMELDING and (Tag.SENDT or Tag.BEKREFTET) and Tag.PERIODE and Tag.BEHANDLINGSDAGER,
            Tag.SYKMELDING and (Tag.SENDT or Tag.BEKREFTET) and Tag.PERIODE and Tag.FULL_AKTIVITET,
            Tag.SYKMELDING and (Tag.SENDT or Tag.BEKREFTET) and Tag.PERIODE and (Tag.GRADERT_AKTIVITET or Tag.INGEN_AKTIVITET),
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
    return if (this.isEmpty()) {
        emptyList()
    } else {
        this.toOppfolgingstilfelleDagList()
            .groupOppfolgingstilfelleList()
    }
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
    }
    return bitListForDag
        .groupBy { bit -> bit.inntruffet.toLocalDateOslo() }
        .toSortedMap()
        .mapValues { entryDagBitList: Map.Entry<LocalDate, List<OppfolgingstilfelleBit>> ->
            entryDagBitList.value.findPriorityOppfolgingstilfelleBitOrNull()
        }
        .mapNotNull(Map.Entry<LocalDate, OppfolgingstilfelleBit?>::value)
        .map { bit ->
            OppfolgingstilfelleDag(
                dag = dag,
                priorityOppfolgingstilfelleBit = bit,
            )
        }
        .lastOrNull()
        ?: OppfolgingstilfelleDag(
            dag = dag,
            priorityOppfolgingstilfelleBit = null,
        )
}

fun List<OppfolgingstilfelleBit>.findPriorityOppfolgingstilfelleBitOrNull(): OppfolgingstilfelleBit? {
    TAG_PRIORITY.forEach { tagPriorityElement ->
        this.find { bit ->
            bit.tagList in tagPriorityElement
        }?.let { bit ->
            return bit
        }
    }
    return null
}

fun OppfolgingstilfelleBit.tagsToString() = this.tagList.joinToString(",")

fun String.toTagList(): List<Tag> = split(',').map(String::trim).map(Tag::valueOf)

fun KafkaSyketilfellebit.toOppfolgingstilfelleBit(): OppfolgingstilfelleBit {
    return OppfolgingstilfelleBit(
        uuid = UUID.fromString(this.id),
        personIdentNumber = PersonIdentNumber(this.fnr),
        virksomhetsnummer = this.orgnummer,
        createdAt = OffsetDateTime.now(),
        inntruffet = this.inntruffet,
        tagList = this.tags.map { tag -> Tag.valueOf(tag) },
        ressursId = this.ressursId,
        fom = this.fom,
        tom = this.tom,
    )
}

fun OppfolgingstilfelleBit.toOppfolgingstilfellePerson(
    oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
) = OppfolgingstilfellePerson(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(),
    personIdentNumber = this.personIdentNumber,
    oppfolgingstilfelleList = oppfolgingstilfelleList,
    referanseTilfelleBitUuid = this.uuid,
    referanseTilfelleBitInntruffet = this.inntruffet,
)
