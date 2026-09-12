package io.github.sixzleo.tabfold.projection;
import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.widget.TextView;
import java.io.InputStream;

/** Exercises the live prefilter using only bundled diagnostic content. */
public final class CacheProbeActivity extends Activity {
    private boolean destroyed;
    private DesktopProjection view;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setDecorFitsSystemWindows(false);
        TextView text=new TextView(this);text.setText("正在验证桌面纹理生成…");setContentView(text);
        getWindow().getInsetsController().hide(android.view.WindowInsets.Type.systemBars());
        new Thread(()->{
            try(InputStream in=getAssets().open("inner_0.png")) {
                Bitmap full=BitmapFactory.decodeStream(in);Bitmap source=Bitmap.createBitmap(full,288,288,1024,724);full.recycle();
                long start=SystemClock.uptimeMillis();GpuLayers layers;
                try{layers=GpuLayers.bake(source,true,3);}finally{source.recycle();}
                android.util.Log.i("ProjectionDesktop","CACHE_PROBE bakeMs="+(SystemClock.uptimeMillis()-start));
                runOnUiThread(()->{try{if(destroyed)layers.close();else{view=new DesktopProjection(this,layers,true,3,120);setContentView(view);}}catch(Exception e){layers.close();text.setText(e.toString());}});
            }catch(Exception e){android.util.Log.e("ProjectionDesktop","CACHE_PROBE_FAILED",e);runOnUiThread(()->text.setText(e.toString()));}
        },"cache-probe").start();
    }
    @Override public void onDestroy(){destroyed=true;if(view!=null)view.release();super.onDestroy();}
}
