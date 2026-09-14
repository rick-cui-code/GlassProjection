package io.github.sixzleo.tabfold.probe;

/** Keep short endpoint reversals warm; detach capture only after a quiet endpoint. */
final class RenderIdleGate {
    static final long DETACH_DELAY_MS=200;
    private long quietAt=-1;
    private boolean clear;
    boolean clearFrame;
    boolean update(long now,boolean visible,boolean fadeActive,long coverToken,boolean sceneChanged){
        boolean idle=!visible&&!fadeActive&&coverToken==0;
        clearFrame=false;
        if(!idle){quietAt=-1;clear=false;return false;}
        if(quietAt<0)quietAt=now;
        clearFrame=!clear||sceneChanged;clear=true;
        return true;
    }
    boolean detach(long now){return quietAt>=0&&now-quietAt>=DETACH_DELAY_MS;}
}
