package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class DesktopProjection extends View {
    private final GpuLayers layers;
    private final RuntimeShader shader;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    final boolean inner;
    final int rotation;
    float angle;
    private float smoothed;
    private long started,lastFrame;
    private boolean closed;
    DesktopProjection(Context context,GpuLayers layers,boolean inner,int rotation,float angle) throws IOException {
        super(context);this.layers=layers;this.inner=inner;this.rotation=rotation;this.angle=angle;smoothed=angle;
        try(InputStream input=getResources().openRawResource(R.raw.reference_plane)) {
            shader=new RuntimeShader(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        for(int i=0;i<8;i++) {
            BitmapShader texture=new BitmapShader(layers.bitmaps[i],Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix tile=new Matrix();tile.setTranslate(-(i%2)*(layers.width+2*GpuLayers.PAD),-(i/2)*(layers.height+2*GpuLayers.PAD));
            texture.setLocalMatrix(tile);
            texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);shader.setInputShader("layer"+i,texture);
        }
        shader.setFloatUniform("size",layers.width,layers.height);
        shader.setFloatUniform("inner",inner?1:0);shader.setFloatUniform("enabled",1);shader.setFloatUniform("diagnostic",0);
        paint.setShader(shader);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    @Override protected void onDraw(Canvas canvas) {
        if(closed)return;
        long now=SystemClock.uptimeMillis();
        if(started==0) {started=now;android.util.Log.i("ProjectionDesktop","FIRST_DRAW inner="+inner+" angle="+angle);}
        float step=lastFrame==0?1:(float)(1-Math.exp(-(now-lastFrame)/28.0));
        smoothed+=(angle-smoothed)*step;lastFrame=now;
        float entrance=Math.min(1,(now-started)/80f);
        entrance=entrance*entrance*(3-2*entrance);
        shader.setFloatUniform("tilt",(float)Math.toRadians(ProjectionMath.tilt(smoothed,inner))*entrance);
        float endpoint=ProjectionMath.endpointOpacity(angle,inner,AnimationSettings.startAngle,ProjectionService.foldPose.blocksProjection());
        paint.setAlpha(Math.round(255*endpoint*entrance));
        int save=canvas.save();
        canvas.concat(GpuLayers.screenMatrix(canvas.getWidth(),canvas.getHeight(),inner,rotation,layers.width,layers.height));
        // The physical right half remains the live system desktop, including all widgets.
        canvas.drawRect(0,0,inner?layers.width*.5f:layers.width,layers.height,paint);
        canvas.restoreToCount(save);postInvalidateOnAnimation();
    }
    void release() {closed=true;layers.close();}
}
