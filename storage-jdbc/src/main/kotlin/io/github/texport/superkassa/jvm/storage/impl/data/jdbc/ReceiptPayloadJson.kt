package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Разбор и запись нагрузки чека (`fiscal_document.payload_bin`).
 *
 * Нагрузка — модель чека ядра как есть, а у неё бывают вычисляемые
 * свойства: Jackson пишет их в JSON наравне с полями, а прочесть обратно
 * не может. Так `ReceiptItem.sumBeforeModifiers` сделал бы каждый чек,
 * записанный на новом ядре, нечитаемым: ни досылки, ни возврата по нему.
 * Незнакомое поле поэтому пропускается — и чек, записанный другой версией
 * ядра, тоже читается.
 */
internal val receiptPayloadJson: ObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
