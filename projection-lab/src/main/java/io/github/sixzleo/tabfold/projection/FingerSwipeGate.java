package io.github.sixzleo.tabfold.projection;

/** Single-finger displacement, independent of scrolling or accessibility node events. */
final class FingerSwipeGate {
    private int pointer=-1,display;
    private float x,y;
    private long last;
    void reset(){pointer=-1;}
    void down(int id,int screen,float px,float py,long time){pointer=Float.isFinite(px)&&Float.isFinite(py)?id:-1;display=screen;x=px;y=py;last=time;}
    boolean move(int id,int screen,float px,float py,long time,float threshold){
        if(pointer<0)return false;
        if(id!=pointer||screen!=display||time<last||time-last>1500||!Float.isFinite(px)||!Float.isFinite(py)){reset();return false;}
        last=time;float dx=px-x,dy=py-y;
        if(dx*dx+dy*dy<threshold*threshold)return false;
        reset();return true;
    }
}
