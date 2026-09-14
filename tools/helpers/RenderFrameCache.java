package io.github.sixzleo.tabfold.probe;

import android.os.Bundle;
import io.github.sixzleo.tabfold.projection.FrameUpdateOrder;

/** Publish immutable snapshots; render frames never allocate or merge Bundles. */
final class RenderFrameCache {
    private final FrameUpdateOrder order=new FrameUpdateOrder();
    private volatile Bundle frame;
    private volatile boolean needsFull=true;
    private volatile long pushes;
    synchronized void accept(Bundle update,boolean pushed){
        boolean full=update.getBoolean("_full");
        if(order.accept(update.getLong("_revision",-1),update.getLong("_base",-1),full)){
            Bundle next=full||frame==null?new Bundle(update):new Bundle(frame);
            if(!full)next.putAll(update);
            frame=next;if(pushed)pushes++;
        }
        needsFull=order.needsFull();
    }
    Bundle snapshot(){return frame;}
    boolean needsFull(){return needsFull;}
    long pushes(){return pushes;}
}
