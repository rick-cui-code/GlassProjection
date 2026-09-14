package io.github.sixzleo.tabfold.projection;

/** Ordered full snapshots/deltas; a missed base must be repaired before merging. */
public final class FrameUpdateOrder {
    private long revision=-1;
    private boolean repair=true;
    public boolean accept(long next,long base,boolean full){
        if(next<0||next<revision||!full&&next==revision)return false;
        if(!full&&(repair||base!=revision)){repair=true;return false;}
        revision=next;repair=false;return true;
    }
    public boolean needsFull(){return repair;}
}
