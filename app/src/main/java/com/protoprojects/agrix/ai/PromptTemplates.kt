package com.protoprojects.agrix.ai

import com.protoprojects.agrix.data.FarmerProfile

/**
 * These mirror the prompts that used to live in the original project's
 * `src/ai/flows` directory (each a `.ts` file) and were sent to Gemini
 * through Genkit. Gemma-on-device is far smaller than Gemini,
 * so every prompt below is:
 *   - shorter and more directive (small models follow short, explicit
 *     instructions much better than long free-form ones),
 *   - asked to answer ONLY with JSON matching a fixed shape, so the app can
 *     parse it the same way it parsed the old Genkit structured output.
 *
 * Pair these with JsonExtractor.extractJson() when reading the model's reply,
 * since small on-device models occasionally wrap JSON in stray text.
 */
object PromptTemplates {

    private fun jsonInstruction(shape: String) =
        "\n\nRespond with ONLY a single valid JSON object, no extra words, no markdown fences, matching exactly this shape:\n$shape"

    fun suggestCropsProfit(profile: FarmerProfile, seasonalInfo: String): String = """
        You are an expert agricultural advisor helping a farmer in India.

        Farmer profile:
        Name: ${profile.name}
        Location: ${profile.location}
        Farm size: ${profile.farmSize} acres
        Soil type: ${profile.soilType}
        Main crops grown: ${profile.mainCrops}
        Season: $seasonalInfo

        Suggest the 3 best crops to plant, considering expected profit margin, and give one short reason for each.
        Also give an overall confidence score (0 to 1) reflecting how well-suited this advice is given the
        information provided.
    """.trimIndent() + jsonInstruction(
        """{"suggestions": [{"crop": "string", "expectedProfitMargin": "string", "reason": "string"}], "confidence": number}"""
    )

    fun predictMarketPrice(cropName: String, location: String, timeHorizon: String): String = """
        You are an agricultural market analyst for Chhattisgarh, India.
        Forecast the market price for this crop.

        Crop: $cropName
        Location: $location, Chhattisgarh
        Time horizon: $timeHorizon

        Give a predicted price range in Rupees (₹) per quintal, brief reasoning covering demand/supply,
        season, and MSP if relevant, and a confidence score from 0 to 1.
    """.trimIndent() + jsonInstruction(
        """{"minPrice": number, "maxPrice": number, "reasoning": "string", "confidence": number}"""
    )

    fun soilBasedRecommendations(soilSummary: String): String = """
        You are an expert agronomist advising farmers in Chhattisgarh, India.
        Here is a summary of a farmer's soil test report (pH, N, P, K, and any other values found):
        $soilSummary

        Suggest 2-3 crops well suited to this soil, and one concrete profit-boosting strategy for each
        (e.g. inter-cropping, direct-to-market selling, timing of sowing). Give an overall confidence
        score (0 to 1) for these recommendations.
    """.trimIndent() + jsonInstruction(
        """{"recommendations": [{"crop": "string", "whyThisCrop": "string", "profitStrategy": "string"}], "confidence": number}"""
    )

    fun pestControlAdvice(cropName: String, pestDescription: String): String = """
        You are an expert entomologist for Indian agriculture, advising a farmer from Chhattisgarh.

        Crop: $cropName
        Pest / symptom description: $pestDescription

        Give clear, simple, actionable advice a farmer can follow, covering both an organic option
        and a chemical option if relevant. Include a confidence score (0 to 1) for your pest identification.
    """.trimIndent() + jsonInstruction(
        """{"likelyPest": "string", "organicControl": "string", "chemicalControl": "string", "notes": "string", "confidence": number}"""
    )

    fun detectCropDiseaseFromDescription(cropDescription: String): String = """
        You are an expert plant pathologist. A farmer describes their crop's symptoms below
        (no photo available, so rely on the description only).

        Crop / symptom description: $cropDescription

        Diagnose the most likely disease (or say "None" if it doesn't sound like disease), suggest
        treatment, and give a confidence level from 0 to 1.
    """.trimIndent() + jsonInstruction(
        """{"diseaseName": "string", "treatment": "string", "confidence": number}"""
    )

    /** Used from the Disease Detector screen (text-only symptom description). */

    fun gradeProduceFromDescription(produceType: String): String = """
        You are an expert in agricultural quality control for produce from Chhattisgarh, India.
        The farmer describes their harvest below: $produceType

        Assign a grade (Premium Quality, Grade A, Grade B, or Feed Grade) and give 2-3 short reasons
        based on the described size, color, uniformity, and defects. Include a confidence score (0 to 1).
    """.trimIndent() + jsonInstruction(
        """{"grade": "string", "reasons": ["string"], "confidence": number}"""
    )

    fun profitImprovementAdvice(profile: FarmerProfile, currentSituation: String): String = """
        You are a farm business advisor. Help this farmer improve profit.

        Farmer profile:
        Name: ${profile.name}
        Location: ${profile.location}
        Farm size: ${profile.farmSize} acres
        Soil type: ${profile.soilType}
        Main crops: ${profile.mainCrops}

        Current situation: $currentSituation

        Give 3 concrete, prioritized suggestions to increase profit this season, and an overall
        confidence score (0 to 1).
    """.trimIndent() + jsonInstruction(
        """{"suggestions": [{"title": "string", "detail": "string"}], "confidence": number}"""
    )

    fun irrigationAdvice(cropName: String, soilType: String, weatherSummary: String): String = """
        You are a precision irrigation expert for Indian farms.

        Crop: $cropName
        Soil type: $soilType
        Weather: $weatherSummary

        Recommend an irrigation schedule (frequency and approximate amount) and one water-saving tip.
        Include a confidence score (0 to 1).
    """.trimIndent() + jsonInstruction(
        """{"schedule": "string", "waterSavingTip": "string", "confidence": number}"""
    )

    fun livestockAdvice(animalType: String, issueDescription: String): String = """
        You are a veterinary advisor for Indian smallholder livestock farmers.

        Animal: $animalType
        Issue: $issueDescription

        Give simple, safe, actionable advice. Recommend contacting a local vet for anything serious.
        Include a confidence score (0 to 1).
    """.trimIndent() + jsonInstruction(
        """{"advice": "string", "seeVetRecommended": boolean, "confidence": number}"""
    )

    fun communityPostDraft(topic: String, languagePreference: String): String = """
        Write a short, friendly community post (2-3 sentences) in $languagePreference for Indian
        farmers about: $topic
    """.trimIndent() + jsonInstruction("""{"post": "string"}""")
}
