package io.github.sixzleo.tabfold.projection;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class ResumeDownloadTest {
    private static final byte[] GOOD="a real signed APK fixture".getBytes();
    private static final List<String> ranges=new ArrayList<>();
    private static int mode,requests;
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private interface Work{void run()throws Exception;}
    private static void rejects(Work work)throws Exception{try{work.run();}catch(IOException expected){return;}throw new AssertionError("expected failure");}
    public static void main(String[] args)throws Exception {
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
                int ordinal,offset;
                public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
                public int getResponseCode(){
                    ordinal=++requests;String range=getRequestProperty("Range");ranges.add(range);
                    offset=range==null?0:Integer.parseInt(range.substring(6,range.length()-1));
                    if(mode==1&&ordinal>1||mode==4&&ordinal>1)return 503;
                    if(mode==5&&ordinal==1)return 416;
                    return (mode==2||mode==4)&&offset>0?206:200;
                }
                public long getContentLengthLong(){return -1;}
                public String getHeaderField(String name){
                    if(!name.equals("Content-Range"))return null;
                    return "bytes "+(mode==4?offset+1:offset)+"-"+(GOOD.length-1)+"/"+GOOD.length;
                }
                public InputStream getInputStream(){
                    if(mode==1&&ordinal==1)return new InputStream(){int at;
                        public int read()throws IOException{if(at==5)throw new IOException("network lost");return GOOD[at++];}
                        public int read(byte[] out,int begin,int length)throws IOException{if(at==5)throw new IOException("network lost");int n=5-at;System.arraycopy(GOOD,at,out,begin,n);at+=n;return n;}
                    };
                    return new ByteArrayInputStream((mode==2&&offset>0)?Arrays.copyOfRange(GOOD,offset,GOOD.length):GOOD);
                }
            };}
        }:null);
        Path root=Files.createTempDirectory("resume-update");
        try{
            File target=root.resolve("verified.apk").toFile(),partial=new File(target+".partial"),identity=new File(target+".identity");
            File reference=root.resolve("reference").toFile();Files.write(reference.toPath(),GOOD);String hash=UpdateTrust.sha256(reference);
            String url=UpdateTrust.REPO+"/releases/download/v1.0/GlassProjection-1.0.apk";
            UpdateTransport.Verify verifier=file->UpdateTrust.checkFile(file,GOOD.length,hash);
            UpdateTransport.Progress progress=message->{};
            mode=1;requests=0;rejects(()->new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress));
            check(partial.length()==5,"network failure preserves the downloaded prefix");
            mode=2;requests=0;ranges.clear();new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check("bytes=5-".equals(ranges.get(0)),"new downloader instance resumes persisted bytes");
            check(Arrays.equals(GOOD,Files.readAllBytes(target.toPath()))&&!partial.exists(),"resumed file verifies before replacing target");
            Files.writeString(identity.toPath(),url+"\n"+GOOD.length+"\n"+hash);
            Files.write(partial.toPath(),Arrays.copyOf(GOOD,5));
            mode=3;requests=0;new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check(target.length()==GOOD.length,"Range ignored with 200 replaces instead of appending");
            Files.writeString(identity.toPath(),url+"\n"+GOOD.length+"\n"+hash);Files.write(partial.toPath(),Arrays.copyOf(GOOD,5));
            mode=4;requests=0;rejects(()->new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress));
            check(partial.length()==5,"incorrect Content-Range never appends data");
            mode=5;requests=0;ranges.clear();new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check(ranges.size()==2&&ranges.get(1)==null,"416 retries a fresh full response on the same source");
            Files.writeString(identity.toPath(),url+"\n"+GOOD.length+"\n"+hash);Files.write(partial.toPath(),new byte[5]);
            mode=2;requests=0;ranges.clear();new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check(requests==2&&ranges.get(1)==null,"corrupt persisted prefix is discarded after verification and next source starts clean");
            Files.writeString(identity.toPath(),"another release");Files.write(partial.toPath(),new byte[5]);
            requests=0;ranges.clear();new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check(ranges.get(0)==null,"release identity change never reuses another APK prefix");
            ResumableUpdate cancelled=new ResumableUpdate();requests=0;
            rejects(()->cancelled.download(url,target,GOOD.length,hash,verifier,new UpdateTransport.Progress(){
                public void show(String message){}public void bytes(long count,long total){if(count>0)cancelled.cancel();}
            }));
            check(partial.length()==GOOD.length,"pause preserves received bytes, including completion before verification");
            requests=0;new ResumableUpdate().download(url,target,GOOD.length,hash,verifier,progress);
            check(requests==0,"complete persisted partial is verified without redownloading");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("PASS: persistent resume, 206 ranges, ignored Range, 416 restart, corrupt-prefix rejection, release identity and pause/restart");
    }
}
