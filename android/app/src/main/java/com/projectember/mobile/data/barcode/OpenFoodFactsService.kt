package com.projectember.mobile.data.barcode

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

interface OpenFoodFactsService {
    // Fields param limits the JSON payload to only what we need.
    @GET("api/v0/product/{barcode}.json?fields=product_name,brands,quantity,serving_size,nutriments")
    suspend fun getProduct(@Path("barcode") barcode: String): OFFResponse
}

data class OFFResponse(
    @Json(name = "status") val status: Int = 0,
    @Json(name = "product") val product: OFFProduct? = null
)

data class OFFProduct(
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "quantity") val quantity: String? = null,
    @Json(name = "serving_size") val servingSize: String? = null,
    @Json(name = "nutriments") val nutriments: OFFNutriments? = null
)

data class OFFNutriments(
    // Per-100g fields — the canonical resolution for food; matches Ingredient convention.
    @Json(name = "energy-kcal_100g") val energyKcal100g: Double? = null,
    @Json(name = "proteins_100g") val proteins100g: Double? = null,
    @Json(name = "fat_100g") val fat100g: Double? = null,
    @Json(name = "carbohydrates_100g") val carbs100g: Double? = null,
    @Json(name = "fiber_100g") val fiber100g: Double? = null,
    // OFF stores sodium as g/100g; we convert to mg/100g on read.
    @Json(name = "sodium_100g") val sodium100g: Double? = null
)
