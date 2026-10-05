# Farmer UX & Multilingual Design

## 1. Designing for Low-Literacy, Regional Farmers

Traditional software interfaces rely heavily on dense text, complex navigation, and technical jargon. In rural agriculture, farmers need **intuitive visual cues, voice interaction, and immediate clarity**.

### Core UX Principles in AgriX
1. **Visual Advisory Cards:** Never expose raw JSON or technical jargon to the farmer. The `ResultArea` and `JsonPrettyView` format results into clean glass cards with clear status indicators, glowing confidence bars, and action checklists.
2. **Real-Time Token Streaming:** Rather than staring at a frozen spinner for 10-15 seconds, farmers see the advisory generate token-by-token in real time, increasing perceived responsiveness.
3. **High-Contrast Dark Theme with Neon Accents:** Designed for sunlight visibility in dusty outdoor field conditions using `AgrixNeonGreen` (#00E676), `AgrixWarnRed` (#FF5252), and crisp typography.

---

## 2. Voice-First & Multilingual Roadmap

### Regional Language Support
* **Languages Supported in Profile:** English, Hindi (हिन्दी), Marathi (मराठी), Telugu (తెలుగు), Chhattisgarhi.
* **Prompt Translation:** Prompts format instructions requesting advice in the farmer's preferred dialect.

### Speech-to-Text & Text-to-Speech Integration
To make AgriX fully accessible to farmers who cannot read or write:
1. **Google ML Kit Digital Ink & Speech Recognition:** Runs fully on-device without cloud APIs, transcribing spoken Hindi/Marathi/Telugu queries into prompt strings.
2. **Android TextToSpeech (TTS):** When diagnosis completes, a speaker button reads aloud the recommendation and action plan in the regional language.

---

## 3. UI Component Hierarchy (`FeatureScreenCommon.kt`)

```
[ Feature Screen (e.g. Irrigation Advisory) ]
  ├── [ Input Form / Live Sensor Tile ]
  ├── [ Submit Button ("Generate Advisory") ]
  └── [ ResultArea ]
        ├── [ QueryUiState.Idle ]     -> Hidden
        ├── [ QueryUiState.Loading ]  -> Pulsing Spinner + "Analyzing on-device..."
        ├── [ QueryUiState.Streaming]-> Live Token Typing Animation
        ├── [ QueryUiState.Error ]    -> Red Glass Card with Actionable Recovery
        └── [ QueryUiState.Success ]  -> Diagnosis Card + Confidence Bar + Pretty JSON Cards
```
