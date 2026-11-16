# Jogo 3D (OpenGL): o renderer é chamado pelo GLSurfaceView e os dados do motor atravessam módulos; não se removem nem renomeiam.
-keep class pt.futebolcarreira.aovivo.** { *; }
-keep class pt.futebolcarreira.nucleo.aovivo.** { *; }
-keep class * implements android.opengl.GLSurfaceView$Renderer { *; }

# Godot (protótipo C0): o motor nativo chama o Java por nome (JNI) e os plugins por reflexão.
-keep class org.godotengine.** { *; }
-keepclassmembers class * { @org.godotengine.godot.plugin.UsedByGodot <methods>; }
-dontwarn org.godotengine.**
