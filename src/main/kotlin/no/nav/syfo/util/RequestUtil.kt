package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

const val NAV_PERSONIDENT_HEADER = "nav-personident"

const val NAV_VIRKSOMHETSNUMMER = "nav-virksomhetsnummer"

const val ALLE_TEMA_HEADERVERDI = "GEN"
const val TEMA_HEADER = "Tema"

fun bearerHeader(token: String) = "Bearer $token"

private val kafkaCounter = AtomicInteger(0)

fun kafkaCallId(): String =
    "${
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-HHmm"))
    }-syfosyketilfelle-kafka-${kafkaCounter.incrementAndGet()}"
