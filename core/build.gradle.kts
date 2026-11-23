plugins {
    id("openhealth.kotlin.library")
}

// Domain types, units (UCUM), formulas, policies and validators land in B2.
// This module must stay free of android.* and androidx.* (Tech-Stack.md §3).
