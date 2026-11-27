# ProGuard keep rules for the macOS and Windows release builds (desktop/mac, desktop/windows).
# Compose Desktop brings its own default rules; these cover what ProGuard cannot see.

# JNA maps the IOKit, CoreFoundation, kernel32 and dwmapi interfaces by reflection.
-keep class com.sun.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Library { *; }
# The macOS Now Playing command handlers are JNA callbacks called from Objective-C.
-keep interface * extends com.sun.jna.Callback { *; }
-keep class * implements com.sun.jna.Callback { *; }
-dontwarn com.sun.jna.**

# Coroutines' Swing dispatcher is found through a service loader.
-keep class kotlinx.coroutines.swing.** { *; }
-dontwarn kotlinx.coroutines.**

# Shrink only: optimisation and renaming gain little here and make native crashes harder to read.
-dontoptimize
-dontobfuscate
