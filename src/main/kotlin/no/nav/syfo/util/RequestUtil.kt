package no.nav.syfo.util

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

const val NAV_PERSONIDENT_HEADER = "nav-personident"

const val NAV_VIRKSOMHETSNUMMER = "nav-virksomhetsnummer"

fun bearerHeader(token: String) = "Bearer $token"
