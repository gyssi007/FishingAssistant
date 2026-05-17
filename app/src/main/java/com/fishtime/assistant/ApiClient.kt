package com.fishtime.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ApiClient {

    private val client =
        OkHttpClient()

    fun getSeatSummary(

        cookie: String,

        merId: String,

        seat: String

    ): SeatSummary? {

        return try {

            val url =
                "https://fishing.gysssi.com/api/fishing/history/seat" +
                "?mer_id=$merId" +
                "&seat_number=$seat" +
                "&days=15"

            val request =
                Request.Builder()
                    .url(url)
                    .addHeader(
                        "Cookie",
                        cookie
                    )
                    .build()

            val response =
                client.newCall(request)
                    .execute()

            val body =
                response.body?.string()
                    ?: return null

            val json =
                JSONObject(body)

            val summary =
                json.getJSONObject(
                    "summary"
                )

            SeatSummary(

                totalWeight =
                    summary.optDouble(
                        "totalWeight",
                        0.0
                    ),

                fishRate =
                    summary.optString(
                        "fishRate",
                        "0"
                    ).toDouble(),

                avgWeight =
                    summary.optDouble(
                        "avgWeight",
                        0.0
                    ),

                fishingDays =
                    summary.optInt(
                        "fishingDays",
                        0
                    )
            )

        } catch (e: Exception) {

            e.printStackTrace()

            null
        }
    }
}
