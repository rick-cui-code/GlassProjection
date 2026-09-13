package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class ProjectionRenderer {
    private final RuntimeShader[] shaders=new RuntimeShader[2];
    private final Bitmap[][] bitmaps=new Bitmap[2][8];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    ProjectionRenderer(Context context) throws IOException {
        String code;
        try(InputStream input=context.getResources().openRawResource(R.raw.reference_plane)) {
            code=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        try {
            for(int panel=0;panel<2;panel++) {
                RuntimeShader shader=new RuntimeShader(code);shaders[panel]=shader;
                String role=panel==0?"cover":"inner";
                for(int i=0;i<8;i++) {
                    try(InputStream input=context.getAssets().open(role+"_"+i+".png")) {
                        BitmapFactory.Options options=new BitmapFactory.Options();
                        options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                        Bitmap b=BitmapFactory.decodeStream(input,null,options);
                        if(b==null) throw new IOException("Cannot load "+role+"_"+i);
                        bitmaps[panel][i]=b;
                        BitmapShader texture=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                        texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                        shader.setInputShader("layer"+i,texture);
                    }
                }
                shader.setFloatUniform("size",panel==0?512:1024,panel==0?752:724);
                shader.setFloatUniform("inner",panel);
                shader.setFloatUniform("spillFraction",ProjectionMath.INNER_SPILL_FRACTION);
                shader.setFloatUniform("hingeDistanceFraction",ProjectionMath.OUTER_HINGE_DISTANCE_FRACTION);
                shader.setFloatUniform("liveBackdrop",0);
            }
        } catch(IOException|RuntimeException error) { close();throw error; }
    }
    void draw(Canvas canvas,boolean inner,int rotation,float angle,boolean enabled,boolean diagnostic) {
        int turn=ProjectionMath.turn(inner,rotation);
        float w=canvas.getWidth(),h=canvas.getHeight();
        float cw=turn%2==0?w:h,ch=turn%2==0?h:w;
        int save=canvas.save();
        if(turn==1) { canvas.translate(0,h);canvas.rotate(-90); }
        else if(turn==2) { canvas.translate(w,h);canvas.rotate(180); }
        else if(turn==3) { canvas.translate(w,0);canvas.rotate(90); }
        canvas.scale(cw/(inner?1024:512),ch/(inner?724:752));
        RuntimeShader shader=shaders[inner?1:0];
        shader.setFloatUniform("tilt",(float)Math.toRadians(ProjectionMath.tilt(angle,inner)));
        shader.setFloatUniform("crop",ProjectionMath.cropFraction(angle,inner,AnimationSettings.stretchPercent));
        shader.setFloatUniform("enabled",enabled?1:0);
        shader.setFloatUniform("diagnostic",diagnostic?1:0);
        paint.setShader(shader);
        canvas.drawRect(0,0,inner?1024:512,inner?724:752,paint);
        canvas.restoreToCount(save);
    }
    void close() {
        for(Bitmap[] panel:bitmaps) for(Bitmap b:panel) if(b!=null && !b.isRecycled()) b.recycle();
    }
}
