plugins {
    id("openhealth.kotlin.library")
}

// Importers and exporters land in B8/B11; their Android implementations arrive with them.
// The API half stays free of android.* and androidx.* (Tech-Stack.md §3).
