package io.github.sixzleo.tabfold.projection;

import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;

/** One-time GPU prefiltering. Retains acquired images so their buffers cannot be reused. */
final class GpuLayers implements AutoCloseable {
    static final int PAD=288;
    final Bitmap[] bitmaps=new Bitmap[8];
    private final ImageReader[] readers=new ImageReader[8];
    private final Image[] images=new Image[8];
    final int width,height;
    private GpuLayers(int w,int h) { width=w;height=h; }
    static Matrix screenMatrix(float w,float h,boolean inner,int rotation,int tw,int th) {
        int turn=ProjectionMath.turn(inner,rotation);
        Matrix m=new Matrix();
        if(turn==1) {m.setTranslate(0,h);m.preRotate(-90);}
        else if(turn==2) {m.setTranslate(w,h);m.preRotate(180);}
        else if(turn==3) {m.setTranslate(w,0);m.preRotate(90);}
        m.preScale((turn%2==0?w:h)/tw,(turn%2==0?h:w)/th);
        return m;
    }
    static GpuLayers bake(Bitmap screenshot,boolean inner,int rotation) {
        int turn=ProjectionMath.turn(inner,rotation),w=inner?1024:512;
        int cw=turn%2==0?screenshot.getWidth():screenshot.getHeight();
        int ch=turn%2==0?screenshot.getHeight():screenshot.getWidth();
        int h=Math.round(w*(float)ch/cw),pw=w+2*PAD,ph=h+2*PAD;
        GpuLayers result=new GpuLayers(w,h);
        HardwareRenderer renderer=new HardwareRenderer();
        RenderNode node=new RenderNode("ReferencePlane-prefilter-atlas");node.setPosition(0,0,pw*2,ph*4);
        RenderNode[] tiles=new RenderNode[8];
        RuntimeShader decode=new RuntimeShader("uniform shader source; half4 main(float2 p) { half4 c=source.eval(p); float3 v=float3(c.rgb); return half4(mix(v/12.92,pow((v+0.055)/1.055,float3(2.4)),step(float3(0.04045),v)),c.a); }");
        RuntimeShader encode=new RuntimeShader("uniform shader source; half4 main(float2 p) { half4 c=source.eval(p); float3 v=max(float3(c.rgb),float3(0)); return half4(mix(v*12.92,1.055*pow(v,float3(1.0/2.4))-0.055,step(float3(0.0031308),v)),1); }");
        float[] sigmas={0,1.5f,3,6,12,24,48,96};
        try {
            Matrix inverse=new Matrix();
            screenMatrix(screenshot.getWidth(),screenshot.getHeight(),inner,rotation,w,h).invert(inverse);
            for(int i=0;i<8;i++) {
                RenderNode tile=new RenderNode("ReferencePlane-sigma-"+i);tiles[i]=tile;
                tile.setPosition((i%2)*pw,(i/2)*ph,(i%2+1)*pw,(i/2+1)*ph);
                RecordingCanvas canvas=tile.beginRecording();
                canvas.drawColor(Color.BLACK);canvas.translate(PAD,PAD);canvas.concat(inverse);
                canvas.drawBitmap(screenshot,null,new Rect(0,0,screenshot.getWidth(),screenshot.getHeight()),new Paint(Paint.FILTER_BITMAP_FLAG));
                tile.endRecording();
                RenderEffect effect=null;
                if(i>0) {
                    RenderEffect linear=RenderEffect.createRuntimeShaderEffect(decode,"source");
                    // Android blur radius r maps to sigma = 0.57735*r + 0.5.
                    float radius=(sigmas[i]-.5f)/.57735f;
                    RenderEffect blur=RenderEffect.createBlurEffect(radius,radius,linear,Shader.TileMode.CLAMP);
                    effect=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(encode,"source"),blur);
                }
                tile.setRenderEffect(effect);
            }
            // All eight kernels in one submission, avoiding eight frame waits.
            RecordingCanvas atlas=node.beginRecording();atlas.drawColor(Color.BLACK);
            for(RenderNode tile:tiles)atlas.drawRenderNode(tile);
            node.endRecording();renderer.setContentRoot(node);renderer.setOpaque(true);
            ImageReader reader=ImageReader.newInstance(pw*2,ph*4,PixelFormat.RGBA_8888,2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE|HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            result.readers[0]=reader;renderer.setSurface(reader.getSurface());
            int sync=renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if((sync & (HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND|HardwareRenderer.SYNC_CONTEXT_IS_STOPPED))!=0)
                throw new IllegalStateException("GPU cache render unavailable: "+sync);
            Image image=null;long until=SystemClock.uptimeMillis()+150;
            while(image==null && SystemClock.uptimeMillis()<until) {image=reader.acquireLatestImage();if(image==null)SystemClock.sleep(2);}
            if(image==null)throw new IllegalStateException("GPU cache image timed out");
            result.images[0]=image;
            try(HardwareBuffer buffer=image.getHardwareBuffer()) {
                if(buffer==null)throw new IllegalStateException("Missing GPU buffer");
                Bitmap bitmap=Bitmap.wrapHardwareBuffer(buffer,ColorSpace.get(ColorSpace.Named.SRGB));
                if(bitmap==null)throw new IllegalStateException("Cannot wrap GPU cache");
                java.util.Arrays.fill(result.bitmaps,bitmap);
            }
            return result;
        } catch(RuntimeException error) { result.close();throw error; }
        finally {renderer.destroy();node.discardDisplayList();for(RenderNode tile:tiles)if(tile!=null)tile.discardDisplayList();}
    }
    @Override public void close() {
        for(Bitmap b:bitmaps)if(b!=null && !b.isRecycled())b.recycle();
        for(Image image:images)if(image!=null)image.close();
        for(ImageReader reader:readers)if(reader!=null)reader.close();
    }
}
