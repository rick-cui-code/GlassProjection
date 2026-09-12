package io.github.sixzleo.tabfold.projection;
public final class FrameGateTest {
    public static void main(String[] args) {
        FrameGate gate=new FrameGate();
        if(gate.observe("inner",12,10,100))throw new AssertionError("first sample");
        if(gate.observe("inner",12,10,120))throw new AssertionError("too soon");
        if(!gate.observe("inner",12,10,140))throw new AssertionError("stable geometry");
        if(gate.observe("cover",12,10,180))throw new AssertionError("panel changed");
        if(gate.observe("cover",13,10,220))throw new AssertionError("layout changed");
        if(!FrameGate.accepts(4,4,"inner","inner",2364,1672,2364,1672,true,true,120))throw new AssertionError("valid frame");
        if(FrameGate.accepts(5,4,"inner","inner",2364,1672,2364,1672,true,true,120)
            || FrameGate.accepts(4,4,"cover","inner",2364,1672,2364,1672,true,true,120)
            || FrameGate.accepts(4,4,"inner","inner",2364,1672,1672,2364,true,true,120)
            || FrameGate.accepts(4,4,"inner","inner",2364,1672,2364,1672,false,true,120)
            || FrameGate.accepts(4,4,"inner","inner",2364,1672,2364,1672,true,false,120)
            || FrameGate.accepts(4,4,"inner","inner",2364,1672,2364,1672,true,true,701))throw new AssertionError("stale frame accepted");
        System.out.println("PASS: stable layout, cancellation, panel, rotation, home, display-on and deadline gates");
    }
}
