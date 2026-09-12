package io.github.sixzleo.tabfold.projection;

/** Geometry must agree before and after an asynchronous screenshot/bake. */
public final class FrameGate {
    private String key="";
    private long signature,since;
    private int samples;
    boolean observe(String next,long shape,int nodes,long now) {
        if(!next.equals(key) || shape!=signature || nodes<8) {key=next;signature=shape;since=now;samples=1;return false;}
        samples++;return samples>=2 && now-since>=40;
    }
    static boolean accepts(int generation,int requested,String scene,String captured,int w,int h,int cw,int ch,
                           boolean home,boolean on,long age) {
        return generation==requested && scene.equals(captured) && w==cw && h==ch && home && on && age<=700;
    }
}
