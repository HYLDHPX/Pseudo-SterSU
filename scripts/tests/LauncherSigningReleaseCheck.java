import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

public final class LauncherSigningReleaseCheck {
    public static void main(String[] args) throws Exception {
        Class.forName("android.security.keystore2.AndroidKeyStoreProvider")
                .getMethod("install").invoke(null);
        File directory = new File(args[0]);
        String alias = "stersu-disposable-release-check-" + System.nanoTime();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        try {
            Calendar expiry = Calendar.getInstance();
            expiry.add(Calendar.DAY_OF_YEAR, 2);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new X500Principal("CN=Disposable release signing check"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(expiry.getTime()).build());
            generator.generateKeyPair();
            KeyStore.PrivateKeyEntry key = (KeyStore.PrivateKeyEntry) store.getEntry(alias, null);
            DexClassLoader loader = new DexClassLoader(new File(directory, args[1]).getPath(),
                    directory.getPath(), null, LauncherSigningReleaseCheck.class.getClassLoader());
            if (args.length > 4) {
                Class<?> model = Class.forName(args[4], false, loader);
                Class<? extends java.lang.annotation.Annotation> marker = Class.forName(
                        "com.android.apksig.internal.asn1.Asn1Class", false, loader)
                        .asSubclass(java.lang.annotation.Annotation.class);
                if (!model.isAnnotationPresent(marker)) {
                    System.out.println("Release stripped the signing library ASN.1 model annotation");
                    throw new AssertionError("Release stripped the signing library ASN.1 model annotation");
                }
                System.out.println("Release ASN.1 model annotation retained");
            }
            Class<?> signing = Class.forName(args[2], true, loader);
            java.lang.reflect.Method method = signing.getDeclaredMethod(args[3], File.class,
                    File.class, PrivateKey.class, X509Certificate.class);
            method.setAccessible(true);
            try {
                method.invoke(null, new File(directory, "unsigned.apk"),
                        new File(directory, "release-signed.apk"), key.getPrivateKey(), key.getCertificate());
                System.out.println("Actual release DEX signing and verification PASSED");
            } catch (java.lang.reflect.InvocationTargetException failure) {
                System.out.println("Actual release DEX signing FAILED");
                failure.getCause().printStackTrace(System.out);
                throw failure;
            }
        } finally {
            store.deleteEntry(alias);
            System.out.println("Disposable shell-UID release key removed");
        }
    }
}
