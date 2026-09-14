package io.github.sixzleo.tabfold.projection;

/** Angle-led fade before the real switch, bounded layout wait, then time-led reveal. */
public final class ScreenFade {
    public static final float LEAD_DEGREES=8;
    public static final long REVEAL_MS=120,CANCEL_MS=80,MAX_WAIT_MS=900;
    private static final int IDLE=0,APPROACH=1,WAIT=2,REVEAL=3,CANCEL=4;
    private int phase,direction,open=60,close=120;
    private float extreme=Float.NaN,dark,cancelFrom;
    private boolean initialized,previousInner,backing,targetInner;
    private long serial,token,switchedAt,phaseAt,crossedAt=-1;
    public void configure(int opening,int closing){
        opening=Math.max(10,Math.min(170,opening));closing=Math.max(10,Math.min(170,closing));
        if(open!=opening||close!=closing){open=opening;close=closing;direction=0;extreme=Float.NaN;
            if(phase==APPROACH){phase=CANCEL;cancelFrom=dark;phaseAt=-1;}}
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private void track(float angle){
        if(angle<=3||angle>=175){direction=angle<=3?1:-1;extreme=angle;return;}
        if(!Float.isFinite(extreme))extreme=angle;
        if(direction>0){extreme=Math.max(extreme,angle);if(extreme-angle>=3){direction=-1;extreme=angle;}}
        else if(direction<0){extreme=Math.min(extreme,angle);if(angle-extreme>=3){direction=1;extreme=angle;}}
        else if(angle-extreme>=3){direction=1;extreme=angle;}
        else if(extreme-angle>=3){direction=-1;extreme=angle;}
    }
    public void update(long now,float angle,boolean inner,boolean enabled,boolean held){
        boolean switched=initialized&&previousInner!=inner;
        initialized=true;previousInner=inner;
        if(!enabled||!Float.isFinite(angle)){
            phase=IDLE;dark=0;backing=false;direction=0;extreme=Float.NaN;return;
        }
        track(angle);
        if(switched){phase=WAIT;token=++serial;switchedAt=phaseAt=now;dark=1;backing=true;crossedAt=-1;}
        if(phase==WAIT){
            dark=1;
            if(now-switchedAt>=MAX_WAIT_MS){phase=REVEAL;phaseAt=now;}
            else return;
        }
        if(phase==REVEAL){
            dark=1-smooth((now-phaseAt)/(float)REVEAL_MS);
            if(dark==0){phase=IDLE;backing=false;}
            else return;
        }
        if(phase==CANCEL){
            if(phaseAt<0)phaseAt=now;
            dark=cancelFrom*(1-smooth((now-phaseAt)/(float)CANCEL_MS));
            if(dark==0){phase=IDLE;backing=false;}else return;
        }
        boolean toward=inner?direction<0:direction>0;
        float lead=inner?Math.min(LEAD_DEGREES,175-close):Math.min(LEAD_DEGREES,open-3);
        float progress=inner?(close+lead-angle)/lead:(angle-(open-lead))/lead;
        if(phase==APPROACH&&(!toward||held||targetInner==inner)){
            phase=CANCEL;phaseAt=now;cancelFrom=dark;return;
        }
        if(phase==IDLE&&toward&&!held&&progress>0){phase=APPROACH;targetInner=!inner;crossedAt=-1;}
        if(phase==APPROACH){
            dark=smooth(progress);backing=dark>=1;
            if(dark>=1){
                if(crossedAt<0)crossedAt=now;
                if(now-crossedAt>=MAX_WAIT_MS){phase=CANCEL;phaseAt=now;cancelFrom=dark;direction=0;extreme=angle;}
            }else crossedAt=-1;
        }else dark=0;
    }
    public long token(){return phase==WAIT?token:0;}
    public long startedAt(){return switchedAt;}
    public float darkness(){return dark;}
    public int mode(){return phase;}
    public long phaseStartedAt(){return phaseAt;}
    public float cancelFrom(){return cancelFrom;}
    public static float sample(long now,int mode,long at,float fallback,float from){
        if(mode==REVEAL)return 1-smooth((now-at)/(float)REVEAL_MS);
        if(mode==CANCEL&&at>=0)return from*(1-smooth((now-at)/(float)CANCEL_MS));
        return fallback;
    }
    public boolean active(){return phase!=IDLE;}
    public boolean backing(){return backing;}
    public boolean contentReady(long expected,long now){
        if(phase!=WAIT||token!=expected)return false;
        phase=REVEAL;phaseAt=now;dark=1;return true;
    }
}
