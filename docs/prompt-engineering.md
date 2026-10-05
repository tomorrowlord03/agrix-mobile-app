# Precision Agriculture Prompt Engineering for Edge LLMs

## 1. Principles for Small On-Device Models (Gemma 3 1B)

Unlike large cloud models (e.g. Gemini 1.5 Pro, Claude 3.5 Sonnet) that possess hundreds of billions of parameters and robust chain-of-thought capabilities, edge models like **Gemma 3 1B IT** operate within tight parameter budgets (~1 billion parameters, int4 quantized).

Key design rules enforced across `PromptTemplates.kt`:
1. **Brevity & Directness:** Avoid long conversational preambles. Use explicit, authoritative role assignment ("You are an expert agronomist for Indian agriculture").
2. **Fixed Output Schema:** Demand a strict JSON contract with explicit key names.
3. **Zero Markdown Fences:** Instruct the model: `"Respond with ONLY a single valid JSON object, no extra words, no markdown fences"`.
4. **Structured Recovery (`JsonExtractor.kt`):** If the model returns preamble text, regex extraction isolates the JSON payload. If parsing fails, a single targeted retry is triggered.

---

## 2. Sensor-Integrated Agronomy Prompting

The core breakthrough in AgriX is passing live sensor telemetry directly into Gemma's prompt window:

### Example: Live Fertigation Evaluation
```kotlin
fun irrigationAndFertilizerAdvice(cropName: String, telemetry: FarmTelemetry): String = """
    You are an expert precision irrigation and fertigation agronomist for Indian agriculture.
    Here is live telemetry acquired from the farmer's field sensors and ESP32 gateway:

    Crop: $cropName
    Field Telemetry:
    ${telemetry.toPromptSummary()}

    Based on the measured soil moisture, water flow rate, fertilizer flow, and soil NPK levels:
    1. Evaluate if current water application is adequate or if over/under-watering is occurring.
    2. Advise whether fertilizer dosing rate should be continued, adjusted, or stopped.
    3. Provide actionable steps for the next irrigation and fertigation cycle.
""".trimIndent() + jsonInstruction(
    """{"irrigationStatus": "string", "fertigationStatus": "string", "recommendedAction": "string", "nextCycleHours": number, "confidence": number}"""
)
```

### Telemetry Formatter Output
```text
• Water Flow: 12.4 L/min (Total applied: 1540 L)
• Fertilizer Flow: 0.82 L/min (Total applied: 45.2 L)
• Soil Moisture: 38.5%
• Soil pH: 6.75, EC: 820 µS/cm
• Soil NPK: N=145, P=42, K=190 mg/kg
• Climate: 29.2°C, 62% Humidity
```

### Edge Model Output
```json
{
  "irrigationStatus": "Optimal root-zone hydration maintained.",
  "fertigationStatus": "NPK ratio balanced; reduce nitrogen dosing by 15% to avoid vegetative excess.",
  "recommendedAction": "Maintain drip line pressure at 1.5 bar for 45 minutes, then flush lines with clear water.",
  "nextCycleHours": 18,
  "confidence": 0.92
}
```
