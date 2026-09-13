package io.github.sixzleo.tabfold.projection;

import java.util.ArrayDeque;

/** A full sliding window, not the difference between just its endpoints. */
final class FoldHoldGate {
    private long windowMs=3000;
    private static final float SWING=10f;
    private static final class Sample {
        final long time;final float angle;
        Sample(long time,float angle){this.time=time;this.angle=angle;}
    }
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private boolean held;
    private float anchor;
    private long last=-1;
    void configure(int seconds){
        long next=Math.max(1,Math.min(10,seconds))*1000L;
        if(next!=windowMs){windowMs=next;samples.clear();last=-1;}
    }
    boolean restore(long now,float angle,boolean eligible){
        if(!eligible||held||!Float.isFinite(angle)||angle<=3||angle>=175)return false;
        held=true;anchor=angle;last=now;samples.clear();return true;
    }
    boolean update(long now,float angle,boolean eligible){
        if(!eligible||!Float.isFinite(angle)||angle<=3||angle>=175){reset();return false;}
        if(last>=0&&(now<last||now-last>1000))reset();
        last=now;
        if(held){
            if(Math.abs(angle-anchor)<=SWING)return true;
            held=false;samples.clear();
        }
        samples.addLast(new Sample(now,angle));
        long cutoff=now-windowMs;
        // Keep the sample in effect at the left edge of the window.
        while(samples.size()>1){
            Sample first=samples.removeFirst();
            if(samples.peekFirst().time>cutoff){samples.addFirst(first);break;}
        }
        if(now-samples.peekFirst().time<windowMs)return false;
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(Sample s:samples){min=Math.min(min,s.angle);max=Math.max(max,s.angle);}
        if(max-min<=SWING){held=true;anchor=angle;samples.clear();}
        return held;
    }
    private void reset(){samples.clear();held=false;last=-1;}
}
