package io.github.sixzleo.tabfold.projection;

import java.io.*;
import java.net.*;

/** Sequential bounded attempts, with validation inside each attempt before accepting a mirror. */
public final class UpdateTransport {
    public interface Progress {
        void show(String message);
        default void bytes(long downloaded,long total){show(total>0?"正在下载 · "+(downloaded*100/total)+"%":"正在读取更新说明");}
    }
    public interface Verify {void check(File file)throws Exception;}
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public void cancel(){cancelled=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();}
    public void checkCancelled()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消");}
    public File download(String original,File destination,long expected,long maximum,Verify verify,Progress progress)throws Exception {
        if(!original.equals(UpdateTrust.API)&&!UpdateTrust.ownDownload(original))throw new IOException("非本项目更新地址");
        Exception last=null;
        for(int i=0;i<UpdateTrust.SOURCES.length;i++){
            checkCancelled();progress.show("正在连接 "+UpdateTrust.SOURCE_NAMES[i]+"（"+(i+1)+"/"+UpdateTrust.SOURCES.length+"）");
            if(expected>0)progress.bytes(0,expected);
            File pending=new File(destination.getPath()+".partial");
            try{
                fetch(UpdateTrust.SOURCES[i]+original,pending,expected,maximum,progress);
                checkCancelled();if(expected>0)progress.show("下载完成，正在校验 APK");verify.check(pending);checkCancelled();
                java.nio.file.Files.move(pending.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return destination;
            }catch(Exception failed){checkCancelled();last=failed;}
            finally{java.nio.file.Files.deleteIfExists(pending.toPath());}
        }
        throw new IOException("GitHub 和所有镜像均未取得通过校验的文件。"+(last==null?"":"\n"+last.getMessage()),last);
    }
    private void fetch(String address,File target,long expected,long maximum,Progress progress)throws Exception {
        long started=System.nanoTime(),deadline=started+90_000_000_000L;URL url=new URL(address);
        for(int redirect=0;redirect<=5;redirect++){
            checkCancelled();if(!"https".equals(url.getProtocol()))throw new IOException("只接受 HTTPS 更新源");
            HttpURLConnection current=(HttpURLConnection)url.openConnection();connection=current;
            current.setConnectTimeout(4000);current.setReadTimeout(6000);current.setInstanceFollowRedirects(false);
            current.setRequestProperty("User-Agent","GlassProjection-Updater/1");current.setRequestProperty("Accept-Encoding","identity");
            try{
                int code=current.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=current.getHeaderField("Location");if(location==null)throw new IOException("无效重定向");url=new URL(url,location);continue;
                }
                if(code!=200)throw new IOException("HTTP "+code);
                long length=current.getContentLengthLong();
                if(length>maximum||expected>0&&length>=0&&length!=expected)throw new IOException("下载长度不符");
                progress.show(expected>0?"正在下载 APK":"正在读取更新说明");
                long total=0,lastProgress=0;
                try(InputStream in=current.getInputStream();FileOutputStream out=new FileOutputStream(target)){
                    byte[] buffer=new byte[32768];int n;
                    while((n=in.read(buffer))!=-1){
                        checkCancelled();total+=n;
                        if(total>maximum||expected>0&&total>expected)throw new IOException("下载大小超限");
                        if(System.nanoTime()>deadline)throw new IOException("当前源下载超时");
                        long elapsed=System.nanoTime()-started;
                        if(expected>1024*1024&&elapsed>12_000_000_000L&&total/(elapsed/1_000_000_000.0)<65536)
                            throw new IOException("当前源持续低速，尝试其他下载源");
                        out.write(buffer,0,n);
                        long now=System.nanoTime();if(now-lastProgress>500_000_000L){
                            if(expected>0)progress.bytes(total,expected);lastProgress=now;
                        }
                    }
                    out.getFD().sync();
                }
                if(expected>0&&total!=expected)throw new IOException("下载不完整");
                if(expected>0)progress.bytes(total,expected);return;
            }finally{current.disconnect();if(connection==current)connection=null;}
        }
        throw new IOException("重定向次数过多");
    }
}
