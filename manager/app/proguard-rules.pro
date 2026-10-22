# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**

# apksig's ASN.1 readers/encoders inspect annotated fields and no-arg constructors.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep @interface com.android.apksig.internal.asn1.* { *; }
-keepclassmembers,allowobfuscation @com.android.apksig.internal.asn1.Asn1Class class * {
    public <init>();
    @com.android.apksig.internal.asn1.Asn1Field <fields>;
}
