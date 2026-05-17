package com.fishtime.assistant

object SeatAnalyzer {

    fun calculateScore(
        summary: SeatSummary
    ): Double {

        return (

            summary.totalWeight * 0.35

        ) + (

            summary.fishRate * 2.5

        ) + (

            summary.avgWeight * 15

        ) + (

            summary.fishingDays * 8
        )
    }

    fun analyze(

        seatA: String,

        summaryA: SeatSummary,

        seatB: String,

        summaryB: SeatSummary

    ): String {

        val scoreA =
            calculateScore(summaryA)

        val scoreB =
            calculateScore(summaryB)

        return if (

            scoreA >= scoreB

        ) {

            seatA

        } else {

            seatB
        }
    }
}
