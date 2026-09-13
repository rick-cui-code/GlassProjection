package io.github.sixzleo.tabfold.projection;
public final class FingerSwipeGateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        FingerSwipeGate g=new FingerSwipeGate();
        check(!g.move(1,0,100,100,10,20),"no down");
        g.down(1,0,100,100,10);
        check(!g.move(1,0,110,110,20,20),"tap noise");
        check(g.move(1,0,120,100,30,20),"horizontal on static page");
        check(!g.move(1,0,200,100,40,20),"once per gesture");
        g.down(1,0,100,100,50);check(g.move(1,0,100,70,60,20),"vertical");
        g.down(1,0,100,100,70);check(!g.move(2,0,0,0,80,20),"pointer changed");
        g.down(1,0,100,100,90);check(!g.move(1,1,0,0,100,20),"display changed");
        g.down(1,0,100,100,100);g.reset();check(!g.move(1,0,0,0,110,20),"cancel or multi touch");
        g.down(1,0,100,100,120);check(!g.move(1,0,0,0,110,20),"old event");
        g.down(1,0,100,100,130);check(!g.move(1,0,0,0,2000,20),"lost stream");
        g.down(1,0,100,100,2010);check(!g.move(1,0,Float.NaN,0,2020,20),"invalid coordinate");
        g.down(1,0,0,0,2030);check(g.move(1,0,15,15,2040,20),"diagonal displacement");
        System.out.println("FingerSwipeGateTest passed");
    }
}
