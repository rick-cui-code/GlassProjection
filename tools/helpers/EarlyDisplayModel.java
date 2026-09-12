package io.github.sixzleo.tabfold.probe;

/** Directional single-panel handoff: opening 60, closing 120. -1 releases override. */
public final class EarlyDisplayModel {
    private int state=-1;
    private int direction;
    private float extreme=Float.NaN;
    private int openAngle=60,closeAngle=120;
    public void configure(int open,int close) {
        open=Math.max(10,Math.min(170,open));close=Math.max(10,Math.min(170,close));
        if(open==openAngle&&close==closeAngle)return;
        openAngle=open;closeAngle=close;state=-1;direction=0;extreme=Float.NaN;
    }
    public int update(float angle,boolean allowed,boolean primaryInner) {
        if(!allowed || !Float.isFinite(angle)) {
            direction=0;
            extreme=Float.NaN;
            return state=-1;
        }
        if(angle<=3 || angle>=175) {
            direction=angle<=3?1:-1;
            extreme=angle;
            return state=-1;
        }
        if(state<0) state=primaryInner?2:0;
        if(!Float.isFinite(extreme)) extreme=angle;
        // Confirm reversals over three degrees so noise in the overlapping
        // 60-120 degree interval cannot alternate display requests at rest.
        if(direction>0) {
            extreme=Math.max(extreme,angle);
            if(extreme-angle>=3) { direction=-1; extreme=angle; }
        } else if(direction<0) {
            extreme=Math.min(extreme,angle);
            if(angle-extreme>=3) { direction=1; extreme=angle; }
        } else if(angle-extreme>=3) {
            direction=1; extreme=angle;
        } else if(extreme-angle>=3) {
            direction=-1; extreme=angle;
        }
        if(direction>0 && angle>=openAngle) state=2;
        else if(direction<0 && angle<=closeAngle) state=0;
        return state;
    }
}
