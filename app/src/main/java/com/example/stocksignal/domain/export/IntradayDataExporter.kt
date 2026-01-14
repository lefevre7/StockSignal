package com.example.stocksignal.domain.export

import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import com.example.stocksignal.data.local.repository.IntradayDataCacheRepository
import com.example.stocksignal.data.repository.PriceCandleJson
import com.example.stocksignal.domain.model.PriceCandle
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export accumulated intraday data to CSV format.
 * Provides users with their historical 10-minute candle data.
 */
@Singleton
class IntradayDataExporter @Inject constructor(
    private val intradayCache: IntradayDataCacheRepository
) {

    /**
     * Export all accumulated intraday data for a symbol to CSV.
     * Returns the file path if successful, null otherwise.
     */
    suspend fun exportToCSV(
        symbol: String,
        outputFile: File
    ): ExportResult {
        return try {
            val entities = intradayCache.getAllCandlesForSymbol(symbol)
            if (entities.isEmpty()) {
                return ExportResult.NoData
            }

            val allCandles = entities.flatMap { entity ->
                parseCandlesFromEntity(entity)
            }.sortedBy { it.time }

            if (allCandles.isEmpty()) {
                return ExportResult.NoData
            }

            outputFile.bufferedWriter().use { writer ->
                // Write CSV header
                writer.write("Timestamp,Open,High,Low,Close,Volume\n")

                // Write data rows
                allCandles.forEach { candle ->
                    writer.write(formatCandleToCSV(candle))
                    writer.write("\n")
                }
            }

            ExportResult.Success(
                filePath = outputFile.absolutePath,
                rowCount = allCandles.size,
                dateRange = allCandles.first().time.toLocalDate() to allCandles.last().time.toLocalDate()
            )
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Unknown error during export")
        }
    }

    /**
     * Get export statistics for a symbol without writing to file.
     */
    suspend fun getExportStats(symbol: String): ExportStats {
        return try {
            val entities = intradayCache.getAllCandlesForSymbol(symbol)
            if (entities.isEmpty()) {
                return ExportStats(
                    symbol = symbol,
                    daysAvailable = 0,
                    totalCandles = 0,
                    earliestDate = null,
                    latestDate = null
                )
            }

            val totalCandles = entities.sumOf { entity ->
                parseCandlesFromEntity(entity).size
            }

            ExportStats(
                symbol = symbol,
                daysAvailable = entities.size,
                totalCandles = totalCandles,
                earliestDate = intradayCache.getEarliestDate(symbol),
                latestDate = intradayCache.getLatestDate(symbol)
            )
        } catch (e: Exception) {
            ExportStats(
                symbol = symbol,
                daysAvailable = 0,
                totalCandles = 0,
                earliestDate = null,
                latestDate = null
            )
        }
    }

    private fun parseCandlesFromEntity(entity: IntradayDataCacheEntity): List<PriceCandle> {
        return try {
            PriceCandleJson.fromJson(entity.candlesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatCandleToCSV(candle: PriceCandle): String {
        val timestamp = candle.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return "$timestamp,${candle.open},${candle.high},${candle.low},${candle.close},${candle.volume}"
    }

    companion object {
        private const val TAG = "IntradayDataExporter"
    }
}

sealed class ExportResult {
    data class Success(
        val filePath: String,
        val rowCount: Int,
        val dateRange: Pair<java.time.LocalDate, java.time.LocalDate>
    ) : ExportResult()

    data object NoData : ExportResult()
    data class Error(val message: String) : ExportResult()
}

data class ExportStats(
    val symbol: String,
    val daysAvailable: Int,
    val totalCandles: Int,
    val earliestDate: java.time.LocalDate?,
    val latestDate: java.time.LocalDate?
)
