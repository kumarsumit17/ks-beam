# R8 keep rules for both Android apps (mobile/android, tv/google-tv). Libraries bring their own
# rules (Compose, Media3, coroutines); these cover only what R8 cannot see on its own.

# Line numbers in stack traces, without the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
