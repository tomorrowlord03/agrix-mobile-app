package com.protoprojects.agrix.ai

/**
 * Points at the exact Gemma .task file that gets downloaded automatically
 * once the farmer signs in to Hugging Face and has accepted Google's Gemma
 * license on their account (a one-time step Google requires of every app,
 * not something any app can bypass).
 *
 * Default: Gemma 3 1B IT, int4-quantized — picked because it comfortably
 * runs on mid-range phones (~550 MB, works on 4 GB RAM devices). Swap
 * MODEL_URL for the 2B build if you'd rather trade phone compatibility for
 * answer quality.
 *
 * IMPORTANT: Hugging Face file paths occasionally change when a repo is
 * re-uploaded or re-quantized. If the download starts returning 404s,
 * open https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main in a
 * browser, copy the current `.task` file's "download" link, and paste it
 * here.
 */
object HFModelSource {
    const val MODEL_REPO_PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true"
    const val MODEL_FILE_NAME = "gemma-model.task"
}
