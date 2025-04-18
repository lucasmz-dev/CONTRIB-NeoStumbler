package xyz.malkki.neostumbler.scanner.source

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import xyz.malkki.neostumbler.domain.CellTower

fun interface CellInfoSource {
    fun getCellInfoFlow(interval: Flow<Duration>): Flow<List<CellTower>>
}
