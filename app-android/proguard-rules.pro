# R8 rules for the release build (Tech-Stack.md §11).
#
# The app has no reflection of its own. These keeps are for libraries that look names up
# at runtime: SQLDelight's generated query classes, SQLCipher's native bridge, Koin's
# module DSL, and the serializers the export format instantiates by name.

# SQLDelight generates a query class per .sq file and dispatches through it.
-keep class app.openhealth.data.db.** { *; }

# SQLCipher loads its native library and reflects into a handful of support classes.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Bouncy Castle is used for Argon2id through its provider mechanism.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# kotlinx.serialization: the generated serializers are referenced from companions.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin resolves definitions from its DSL; keep the declarations, not the whole library.
-keep class org.koin.** { *; }

# Health Connect permission contracts are instantiated by the platform.
-keep class androidx.health.connect.client.permission.** { *; }

# Keep line numbers for a readable stack trace, but no source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
