package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** App-private, non-backed-up identity. Pairing codes never go to disk or logs. */
final class LocalAdbIdentity extends AbsAdbConnectionManager {
    private final PrivateKey key;
    private final Certificate certificate;
    LocalAdbIdentity(Context context)throws Exception {
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"local-adb-identity"));
        if(file.getBaseFile().exists()){
            try(DataInputStream in=new DataInputStream(file.openRead())){
                int length=in.readInt();if(length<256||length>8192)throw new IOException("Invalid identity");
                byte[] encoded=new byte[length];in.readFully(encoded);
                key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
                certificate=CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        }else{
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
            KeyPair pair=generator.generateKeyPair();key=pair.getPrivate();
            long now=System.currentTimeMillis();X500Name name=new X500Name("CN=Glass Projection");
            byte[] encoded=new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()),
                new Date(now-86400000L),new Date(now+3650L*86400000L),name,pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(key)).getEncoded();
            certificate=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
            FileOutputStream stream=null;
            try{
                stream=file.startWrite();DataOutputStream out=new DataOutputStream(stream);
                byte[] privateBytes=key.getEncoded();out.writeInt(privateBytes.length);out.write(privateBytes);out.write(encoded);out.flush();
                file.finishWrite(stream);
            }catch(IOException error){if(stream!=null)file.failWrite(stream);throw error;}
        }
        setApi(Build.VERSION.SDK_INT);setHostAddress("127.0.0.1");setTimeout(8,TimeUnit.SECONDS);setThrowOnUnauthorised(true);
    }
    @Override protected PrivateKey getPrivateKey(){return key;}
    @Override protected Certificate getCertificate(){return certificate;}
    @Override protected String getDeviceName(){return "GlassProjection";}
}
