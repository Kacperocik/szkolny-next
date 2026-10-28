/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-1.
 */

package pl.szczodrzynski.edziennik.ui.grades.models

class GradesAverages {
    var normalSum = 0f
    var normalCount = 0
    var normalWeightedSum = 0f
    var normalWeightedCount = 0f

    var pointSum = 0f

    var pointAvgSum = 0f
    var pointAvgMax = 0f

    var normalAvg: Float? = null
    var pointAvgPercent: Float? = null

    fun reset() {
        normalSum = 0f
        normalCount = 0
        normalWeightedSum = 0f
        normalWeightedCount = 0f
        pointSum = 0f
        pointAvgSum = 0f
        pointAvgMax = 0f
        normalAvg = null
        pointAvgPercent = null
    }

    fun copyFrom(other: GradesAverages) {
        normalSum = other.normalSum
        normalCount = other.normalCount
        normalWeightedSum = other.normalWeightedSum
        normalWeightedCount = other.normalWeightedCount
        pointSum = other.pointSum
        pointAvgSum = other.pointAvgSum
        pointAvgMax = other.pointAvgMax
        normalAvg = other.normalAvg
        pointAvgPercent = other.pointAvgPercent
    }
}
