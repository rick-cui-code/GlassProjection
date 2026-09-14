package io.github.sixzleo.tabfold.projection;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class UpdateSecurityTest {
    private static int mode,attempts;
    private static byte[] good="verified update".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final List<String> seen=new ArrayList<>();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private interface Work{void run()throws Exception;}
    private static void rejects(Work work,String message)throws Exception{try{work.run();}catch(Exception expected){return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(UpdateTrust.compareVersion("v0.4.10","0.4.9")>0,"numeric version order");
        check(UpdateTrust.compareVersion("v0.4.25","0.4.25.0")==0,"equivalent versions");
        check(UpdateTrust.compareVersion("0.4.17","0.4.25")<0,"older release never offered");
        rejects(()->UpdateTrust.compareVersion("v0.4.25-beta","0.4.25"),"ambiguous version");
        check(!UpdateTrust.ownDownload(UpdateTrust.REPO+"/releases/download/../file.apk"),"tag traversal");
        check(!UpdateTrust.ownDownload("https://attacker/file.apk"),"asset URL injection");
        check(!UpdateTrust.ownRelease(UpdateTrust.REPO+"/releases/tag/v1?redirect=elsewhere"),"release confinement");
        Path root=Files.createTempDirectory("glass-update-test");
        try{
            URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
                protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
                    public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
                    public int getResponseCode(){seen.add(url.toString());attempts++;return mode==1||mode==0&&attempts==1?503:mode==3?302:200;}
                    public String getHeaderField(String name){return mode==3&&name.equals("Location")?"http://invalid/download":null;}
                    public long getContentLengthLong(){return -1;}
                    public InputStream getInputStream(){return new ByteArrayInputStream(mode==0&&attempts==2?"corrupt".getBytes():good);}
                };}
            }:null);
            File reference=root.resolve("reference").toFile();Files.write(reference.toPath(),good);String hash=UpdateTrust.sha256(reference);
            File output=root.resolve("download").toFile();String url=UpdateTrust.REPO+"/releases/download/v1/GlassProjection-1.apk";
            mode=0;attempts=0;seen.clear();
            new UpdateTransport().download(url,output,good.length,100,file->UpdateTrust.checkFile(file,good.length,hash),message->{});
            check(attempts==3&&seen.get(0).equals(url)&&seen.get(1).startsWith("https://ghfast.top/")&&seen.get(2).startsWith("https://gh-proxy.com/"),"failover order and reject corrupt mirror");
            check(Arrays.equals(Files.readAllBytes(output.toPath()),good),"only verified content accepted");
            mode=1;attempts=0;rejects(()->new UpdateTransport().download(url,output,-1,100,file->{},message->{}),"all sources fail");
            check(attempts==4&&!new File(output+".partial").exists(),"all sources exhausted and partial removed");
            check(Arrays.equals(Files.readAllBytes(output.toPath()),good),"failed update keeps previous file");
            mode=2;rejects(()->new UpdateTransport().download(url,output,good.length+1,100,file->{},message->{}),"truncated response");
            rejects(()->new UpdateTransport().download(url,output,-1,2,file->{},message->{}),"size cap");
            mode=3;rejects(()->new UpdateTransport().download(url,output,-1,100,file->{},message->{}),"HTTPS downgrade");
            UpdateTransport cancelled=new UpdateTransport();cancelled.cancel();rejects(()->cancelled.download(url,output,-1,100,file->{},message->{}),"cancelled download");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("PASS: numeric versions, repository confinement, corrupt mirror failover, all-source failure, size/truncation, HTTPS and cancellation");
    }
}
