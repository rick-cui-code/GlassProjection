package io.github.sixzleo.tabfold.projection;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.regex.*;

/** Persistent APK partials, bound to a single release; every completed file is verified. */
public final class ResumableUpdate {
    private static final Object FILE_LOCK=new Object();
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private void checkCancelled()throws InterruptedIOException {
        if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已暂停，进度已保存");
    }
    private static void discard(File file)throws IOException{Files.deleteIfExists(file.toPath());}
    public File download(String url,File target,long size,String hash,UpdateTransport.Verify verify,UpdateTransport.Progress progress)throws Exception {
        synchronized(FILE_LOCK){checkCancelled();return downloadLocked(url,target,size,hash,verify,progress);}
    }
    private File downloadLocked(String url,File target,long size,String hash,UpdateTransport.Verify verify,UpdateTransport.Progress progress)throws Exception {
        if(!UpdateTrust.ownDownload(url)||size<=0||size>UpdateTrust.MAX_APK)throw new IOException("APK 下载信息无效");
        File partial=new File(target+".partial"),identity=new File(target+".identity");
        String key=url+"\n"+size+"\n"+hash;
        if(!identity.isFile()||!new String(Files.readAllBytes(identity.toPath()),java.nio.charset.StandardCharsets.UTF_8).equals(key)||partial.length()>size){
            discard(partial);Files.write(identity.toPath(),key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Exception last=null;
        for(int source=0;source<UpdateTrust.SOURCES.length;source++){
            checkCancelled();
            progress.show("正在连接 "+UpdateTrust.SOURCE_NAMES[source]+"（"+(source+1)+"/"+UpdateTrust.SOURCES.length+"）");
            progress.bytes(partial.length(),size);
            try{
                if(partial.length()<size)fetch(UpdateTrust.SOURCES[source]+url,partial,size,progress);
                checkCancelled();progress.show("下载完成，正在校验 APK");
                try{verify.check(partial);}catch(Exception invalid){discard(partial);throw invalid;}
                checkCancelled();Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
                discard(identity);return target;
            }catch(Exception failed){checkCancelled();last=failed;}
        }
        throw new IOException("所有下载源暂不可用，进度已保存，可稍后继续。"+(last==null?"":"\n"+last.getMessage()),last);
    }
    private void fetch(String address,File partial,long expected,UpdateTransport.Progress progress)throws Exception {
        long started=System.nanoTime(),base=partial.length();
        URL url=new URL(address);boolean restarted=false;
        for(int redirect=0;redirect<=5;redirect++){
            checkCancelled();if(!url.getProtocol().equals("https"))throw new IOException("只接受 HTTPS 更新源");
            long offset=partial.length();
            HttpURLConnection current=(HttpURLConnection)url.openConnection();connection=current;
            current.setConnectTimeout(4000);current.setReadTimeout(6000);current.setInstanceFollowRedirects(false);
            current.setRequestProperty("User-Agent","GlassProjection-Updater/1");current.setRequestProperty("Accept-Encoding","identity");
            if(offset>0)current.setRequestProperty("Range","bytes="+offset+"-");
            try{
                int code=current.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=current.getHeaderField("Location");if(location==null)throw new IOException("无效重定向");url=new URL(url,location);continue;
                }
                if(code==416&&!restarted){discard(partial);base=0;restarted=true;redirect--;continue;}
                if(code!=200&&code!=206)throw new IOException("HTTP "+code);
                long responseSize=expected;
                if(code==206){
                    Matcher range=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(String.valueOf(current.getHeaderField("Content-Range")));
                    if(!range.matches()||Long.parseLong(range.group(1))!=offset||Long.parseLong(range.group(3))!=expected
                        ||Long.parseLong(range.group(2))!=expected-1)throw new IOException("续传范围不符");
                    responseSize=expected-offset;
                }else if(offset>0){
                    // A source may ignore Range. Replace the partial instead of appending a full response.
                    discard(partial);offset=0;base=0;progress.bytes(0,expected);
                }
                long length=current.getContentLengthLong();
                if(length>=0&&length!=responseSize)throw new IOException("下载长度不符");
                progress.show(offset>0?"正在继续下载 APK":"正在下载 APK");
                long total=offset,lastProgress=0;
                try(InputStream in=current.getInputStream();FileOutputStream out=new FileOutputStream(partial,offset>0)){
                    byte[] buffer=new byte[32768];int n;
                    try{
                        while((n=in.read(buffer))!=-1){
                            checkCancelled();
                            if(total+n>expected)throw new IOException("下载大小超限");
                            out.write(buffer,0,n);total+=n;
                            long now=System.nanoTime(),elapsed=now-started;
                            if(now-lastProgress>500_000_000L){progress.bytes(total,expected);lastProgress=now;}
                            if(elapsed>90_000_000_000L)throw new IOException("当前源下载超时");
                            if(expected>1024*1024&&elapsed>12_000_000_000L&&(total-base)/(elapsed/1_000_000_000.0)<65536)
                                throw new IOException("当前源持续低速，尝试其他下载源");
                        }
                    }finally{out.getFD().sync();progress.bytes(total,expected);}
                }
                if(total!=expected)throw new IOException("下载中断，已保存进度");return;
            }finally{current.disconnect();if(connection==current)connection=null;}
        }
        throw new IOException("重定向次数过多");
    }
}
