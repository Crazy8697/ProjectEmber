package com.projectember.mobile.data.barcode

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class BarcodeProductLookupRepository {

    private val service: OpenFoodFactsService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsService::class.java)
    }

    /**
     * Look up a barcode against Open Food Facts.
     * Returns null on any network failure, timeout, or when no product is found.
     * Never throws — callers can treat null as "no result available".
     */
    suspend fun lookup(barcode: String): BarcodeProductResult? {
        return try {
            val response = service.getProduct(barcode)
            if (response.status != 1 || response.product == null) return null
            val p = response.product
            val name = p.productName?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val n = p.nutriments
            BarcodeProductResult(
                barcode = barcode,
                name = name,
                brand = p.brands?.trim()?.takeIf { it.isNotBlank() },
                servingSizeNote = p.servingSize?.trim()?.takeIf { it.isNotBlank() }
                    ?: p.quantity?.trim()?.takeIf { it.isNotBlank() },
                caloriesKcal = n?.energyKcal100g,
                proteinG = n?.proteins100g,
                fatG = n?.fat100g,
                totalCarbsG = n?.carbs100g,
                fiberG = n?.fiber100g,
                sodiumMg = n?.sodium100g?.let { it * 1000.0 }   // g → mg
            )
        } catch (_: Exception) {
            null
        }
    }
}
