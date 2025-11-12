package no.nav.syfo.infrastructure.kafka.sykmeldingstatus

import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.util.and
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.*

class SykmeldingstatusConsumer(
    private val tilfellebitRepository: TilfellebitRepository,
) {
    fun pollAndProcessRecords(
        kafkaConsumerStatusendring: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    ) {
        val records = kafkaConsumerStatusendring.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_READ.increment(records.count().toDouble())
            val (tombstoneRecords, relevantRecords) = records.partition {
                it.value() == null
            }
            COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_TOMBSTONE.increment(tombstoneRecords.size.toDouble())
            processRelevantRecords(relevantRecordList = relevantRecords)
            kafkaConsumerStatusendring.commitSync()
        }
    }

    private fun processRelevantRecords(
        relevantRecordList: List<ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>>,
    ) {
        relevantRecordList.forEach { consumerRecord ->
            val kafkaSykmeldingStatus = consumerRecord.value()
            val inntruffet = kafkaSykmeldingStatus.event.timestamp
            if (inntruffet.isAfter(STATUS_ENDRING_CUTOFF) && kafkaSykmeldingStatus.event.statusEvent == StatusEndring.STATUS_AVBRUTT.value) {
                val oppfolgingstilfelleBitList = tilfellebitRepository.getOppfolgingstilfelleBitForRessursId(
                    ressursId = kafkaSykmeldingStatus.event.sykmeldingId,
                )
                oppfolgingstilfelleBitList.filter {
                    it.tagList in (Tag.SYKMELDING and Tag.NY)
                }.forEach { pOppfolgingstilfelleBit ->
                    tilfellebitRepository.createOppfolgingstilfelleBitAvbrutt(
                        pOppfolgingstilfelleBit = pOppfolgingstilfelleBit,
                        inntruffet = inntruffet,
                    )
                    val latestProcessedTilfelleBit =
                        tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(
                            personIdentNumber = pOppfolgingstilfelleBit.personIdentNumber,
                            includeAvbrutt = true,
                        ).firstOrNull()
                    // Set the newest tilfelleBit to unprocessed so that oppfolgingstilfelle is updated by cronjob
                    tilfellebitRepository.setProcessedOppfolgingstilfelleBit(
                        uuid = latestProcessedTilfelleBit?.uuid ?: pOppfolgingstilfelleBit.uuid,
                        processed = false,
                    )
                }
            }
        }
    }

    companion object {
        private val STATUS_ENDRING_CUTOFF: OffsetDateTime = OffsetDateTime.of(
            LocalDate.of(2023, 1, 1),
            LocalTime.MIN,
            ZoneOffset.UTC,
        )
    }
}
