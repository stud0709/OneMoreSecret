# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information so crash reports from release builds are readable.
# Deobfuscate them with build/outputs/mapping/<variant>/mapping.txt (retrace).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class fr.acinq.secp256k1.** { *; }

# ML Kit ('standard' flavor) discovers its components by reflectively calling the no-arg
# constructor of every ComponentRegistrar. firebase-components ships
# "-keep class * implements ComponentRegistrar", which under R8 full mode keeps the class but
# not its default constructor, so discovery dies with NoSuchMethodException, the component
# graph stays empty and BarcodeScanning.getClient() then NPEs on a null internal component.
# Self-contained on purpose: -keepclassmembers alone would be a silent no-op if that upstream
# rule ever narrows, and the crash would come back with no build-time signal.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# KeyboardLayout subclasses appear only as Class literals in KeyboardLayout.knownSubclasses and
# are built with getDeclaredConstructor().newInstance() in OutputViewModel.initializeKeyboardLayouts.
# R8 sees no constructor call and drops the default constructor. Their names are also persisted
# in SharedPreferences (PROP_LAST_SELECTED_KEYBOARD_LAYOUT) and compared against javaClass.name,
# so the names have to stay stable across builds too - hence -keep rather than -keepclassmembers.
-keep class * extends com.onemoresecret.bt.layout.KeyboardLayout {
    <init>();
}
