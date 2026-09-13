package io.github.sixzleo.tabfold.projection;

public final class FoldHoldGateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        FoldHoldGate gate=new FoldHoldGate();
        for(int t=0;t<3000;t+=20)check(!gate.update(t,120,true),"early hold "+t);
        check(gate.update(3000,120,true),"stationary sensor must expire via ticks");
        check(gate.update(3020,130,true),"inclusive resume tolerance");
        check(!gate.update(3040,130.1f,true),"resume beyond 10 degrees");
        for(int t=3060;t<6040;t+=20)check(!gate.update(t,130.1f,true),"new full window");
        check(gate.update(6040,130.1f,true),"hold again");
        check(!gate.update(6060,130.1f,false),"screen-off/scope reset");
        check(!gate.update(6080,130.1f,true),"no old timer on wake");

        gate=new FoldHoldGate();
        for(int t=0;t<=6000;t+=20){
            float a=120+(float)Math.sin(t*Math.PI/1000)*8;
            check(!gate.update(t,a,true),"round trip range, not endpoint difference");
        }
        gate=new FoldHoldGate();
        for(int t=0;t<=3000;t+=20){
            boolean held=gate.update(t,115+(t%40==0?0:10),true);
            check(held==(t==3000),"inclusive 10 degree swing");
        }
        gate=new FoldHoldGate();
        for(int t=0;t<3000;t+=20)gate.update(t,120,true);
        check(!gate.update(3000,Float.NaN,true),"invalid sample");
        check(!gate.update(3020,120,true),"invalid sample clears timer");
        check(!gate.update(10000,120,true),"scheduler gap clears timer");
        for(int t=10020;t<13000;t+=20)gate.update(t,120,true);
        check(gate.update(13000,120,true),"hold after new uninterrupted window");
        check(!gate.update(13020,180,true),"flat endpoint resets");
        check(!gate.update(13040,170,true),"new fold starts immediately");
        for(int seconds:new int[]{1,3,10}){
            gate=new FoldHoldGate();gate.configure(seconds);
            for(int t=0;t<seconds*1000;t+=20)check(!gate.update(t,110,true),"configured early hold "+seconds);
            check(gate.update(seconds*1000,110,true),"configured deadline "+seconds);
        }
        gate=new FoldHoldGate();gate.configure(10);
        check(!gate.restore(0,120,false),"ignore scroll outside scope");
        check(!gate.restore(0,Float.NaN,true),"ignore invalid scroll pose");
        check(!gate.restore(0,180,true),"ignore flat scrolling");
        gate.update(0,120,true);
        check(gate.restore(100,120,true),"scroll restores before timeout");
        check(gate.update(120,120,true),"restored state persists");
        check(!gate.restore(140,128,true),"repeated scrolling must not move anchor");
        check(!gate.update(160,131,true),"fold resumes relative to original anchor");
        gate=new FoldHoldGate();
        for(int t=0;t<2000;t+=20)gate.update(t,120,true);
        gate.configure(1);
        check(!gate.update(2000,120,true),"time change starts new window");
        for(int t=2020;t<3000;t+=20)check(!gate.update(t,120,true),"new timer too early");
        check(gate.update(3000,120,true),"new timer expires");
        gate.configure(10);
        check(gate.update(3020,120,true),"time change does not reanimate restored screen");
        gate=new FoldHoldGate();gate.configure(1,1);
        for(int t=0;t<1000;t+=20)check(!gate.update(t,2,true),"low angle full hold window");
        check(gate.update(1000,2,true),"new 1 degree onset supports low-angle hold");
        check(!gate.update(1020,1,true),"configured endpoint clears hold");
        check(!gate.restore(1040,1,true),"no swipe restore at invisible endpoint");
        check(gate.restore(1060,2,true),"swipe restore in new low-angle animation range");
        gate.configure(1,10);
        check(!gate.update(1080,2,true),"raising onset clears out-of-range hold");
        for(int t=1100;t<2100;t+=20)check(!gate.update(t,12,true),"fresh hold window above new onset");
        check(gate.update(2100,12,true),"hold at adjusted onset");
        gate.configure(1,1);
        check(gate.update(2120,12,true),"lowering onset preserves restored pose");
        System.out.println("PASS: 3 second range, jitter, round trips, resume, endpoints, sleep and invalid samples");
        System.out.println("PASS: 1/3/10 second windows, timer edits, scroll restore, ignored events and stable resume anchor");
    }
}
