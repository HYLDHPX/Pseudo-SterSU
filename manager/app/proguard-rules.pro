# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**

# apksig reflects on annotated classes, generic field types and no-arg constructors.
# R8 full mode also requires the class itself to be kept to retain its annotation.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature
-keep @interface com.android.apksig.internal.asn1.* { *; }
-keep,allowobfuscation @com.android.apksig.internal.asn1.Asn1Class class * {
    public <init>();
    @com.android.apksig.internal.asn1.Asn1Field <fields>;
}
