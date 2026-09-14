package io.github.sixzleo.tabfold.projection;

import com.android.apksig.ApkVerifier;
import org.json.*;
import java.io.*;
import java.nio.file.Files;

/** Read-only app behavior smoke test; runs as shell and never installs or changes settings. */
public final class UpdateSmoke {
    private static void check(boolean okay,String label){if(!okay)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);if(!root.isDirectory()&&!root.mkdir())throw new IOException("test directory");
        UpdateTransport transfer=new UpdateTransport();final ApkRelease[] found=new ApkRelease[1];
        File metadata=transfer.download(UpdateTrust.API,new File(root,"release.json"),-1,1024*1024,
            file->found[0]=new ApkRelease(Files.readAllBytes(file.toPath())),System.out::println);
        ApkRelease release=found[0];System.out.println("RELEASE "+release.tag+" asset="+release.name);
        check(UpdateTrust.ownDownload(release.url)&&UpdateTrust.ownRelease(release.page),"project URLs");
        JSONObject invalid=new JSONObject(Files.readString(metadata.toPath()));invalid.put("draft",true);
        boolean rejected=false;try{new ApkRelease(invalid.toString().getBytes());}catch(IOException expected){rejected=true;}
        check(rejected,"draft rejected");
        JSONObject alternate=new JSONObject(Files.readString(metadata.toPath()));
        JSONObject apk=null;JSONArray assets=alternate.getJSONArray("assets");
        for(int i=0;i<assets.length();i++)if(assets.getJSONObject(i).getString("name").equals(release.name))apk=assets.getJSONObject(i);
        check(apk!=null,"APK asset exists");apk.put("name","release.apk");alternate.put("assets",new JSONArray().put(apk));
        check(new ApkRelease(alternate.toString().getBytes()).name.equals("release.apk"),"single APK fallback");
        apk.put("browser_download_url","https://untrusted.example/app.apk");rejected=false;
        try{new ApkRelease(alternate.toString().getBytes());}catch(IOException expected){rejected=true;}
        check(rejected,"foreign download rejected");
        File downloaded=transfer.download(release.url,new File(root,"download.apk"),release.size,UpdateTrust.MAX_APK,
            file->{UpdateTrust.checkFile(file,release.size,release.hash);check(new ApkVerifier.Builder(file).setMinCheckedPlatformVersion(33).build().verify().isVerified(),"authentic APK signature");},System.out::println);
        try(RandomAccessFile corrupt=new RandomAccessFile(downloaded,"rw")){corrupt.seek(1000);int value=corrupt.read();corrupt.seek(1000);corrupt.write(value^1);}
        check(!new ApkVerifier.Builder(downloaded).setMinCheckedPlatformVersion(33).build().verify().isVerified(),"modified APK rejected");
        Files.delete(downloaded.toPath());
        System.out.println("PASS: live GitHub metadata, release parsing, URL confinement, real APK download/hash/signature, modified APK rejection; no installation performed");
    }
}
