# R8 rules for the NEXUS TV Remote release build.
# Every rule is targeted and explains why. There is no blanket "-keep class **".

# --- BouncyCastle (used by adb-kt for RSA keys, X.509 and TLS 1.3) --------------------------
# adb-kt v1.0.13 already ships these exact rules in META-INF/com.android.tools/r8/adb-kt.pro,
# and R8 applies them automatically. They are repeated here so that a future adb-kt upgrade
# that drops its consumer rules cannot silently break release builds.
# Reason: BouncyCastleProvider registers algorithm implementations by class NAME (strings
# such as "org.bouncycastle.jcajce.provider.asymmetric.RSA$Mappings") and instantiates
# them reflectively, so R8 cannot see they are used.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
# BouncyCastle references optional JDK-only APIs (LDAP / javax.naming, etc.) that do not
# exist on Android and are never called by adb-kt.
-dontwarn org.bouncycastle.**

# --- Manifest components --------------------------------------------------------------------
# AAPT2 already generates keep rules for every class named in AndroidManifest.xml. These
# explicit rules document that intent and guard against manifest-merging surprises.
-keep class com.example.nexusremote.app.NexusApplication { <init>(); }
-keep class com.example.nexusremote.app.MainActivity { <init>(); }
-keep class com.example.nexusremote.tile.RemoteTileService { <init>(); }

# --- Logging hygiene ------------------------------------------------------------------------
# Strip verbose/debug android.util.Log calls from release builds (SafeLog.event uses Log.d).
# Info/warn calls (state transitions and error codes only) are kept for field diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
