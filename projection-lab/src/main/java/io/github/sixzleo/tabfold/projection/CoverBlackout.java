package io.github.sixzleo.tabfold.projection;

/** Black backing stays below animation; readiness only ends the bounded layout wait. */
public final class CoverBlackout {
    public static final long MAX_WAIT_MS=900;
    private boolean initialized,wasInner,masked,backing;
    private long serial,token,started;
    public void update(long now,boolean inner,boolean enabled){
        boolean handoff=initialized&&wasInner&&!inner;
        initialized=true;wasInner=inner;
        if(!enabled||inner){masked=false;backing=false;return;}
        if(handoff){masked=true;backing=true;token=++serial;started=now;}
        if(masked&&now-started>=MAX_WAIT_MS){masked=false;backing=false;}
    }
    public long token(){return masked?token:0;}
    public long startedAt(){return started;}
    public boolean backing(){return backing;}
    public void finishBacking(){if(!masked)backing=false;}
    public boolean contentReady(long expected){
        if(!masked||token!=expected)return false;
        masked=false;return true;
    }
}
