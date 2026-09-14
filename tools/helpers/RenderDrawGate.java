package io.github.sixzleo.tabfold.probe;

/** Identical source + identical final parameters need no new output buffer. */
final class RenderDrawGate {
    private boolean initialized,inner,fade;
    private int width,height,turn;
    private float tilt,crop,opacity,darkness,strength;
    boolean parametersChanged;
    boolean draw(boolean sourceChanged,boolean force,int w,int h,int r,boolean inside,boolean fading,float t,float c,float o,float d,float s){
        parametersChanged=!initialized||width!=w||height!=h||turn!=r||inner!=inside||fade!=fading
                ||tilt!=t||crop!=c||opacity!=o||darkness!=d||strength!=s;
        initialized=true;width=w;height=h;turn=r;inner=inside;fade=fading;
        tilt=t;crop=c;opacity=o;darkness=d;strength=s;
        return sourceChanged||force||parametersChanged;
    }
    void reset(){initialized=false;}
}
