package io.github.sixzleo.tabfold.probe;

public final class RenderIdleGateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        RenderIdleGate gate=new RenderIdleGate();
        check(gate.update(0,false,false,0,true)&&gate.clearFrame,"closed startup clears even before first texture");
        check(!gate.detach(199)&&gate.detach(200),"short reversals retain warm capture; quiet endpoint detaches");
        for(int t=10;t<1000;t+=10){check(gate.update(t,false,false,0,false),"closed stays idle");check(!gate.clearFrame,"closed tilt must not keep submitting transparent buffers");}
        check(!gate.update(1000,true,false,0,false)&&!gate.detach(1000),"opening resumes immediately without a timed animation");
        check(gate.update(1001,false,false,0,false)&&gate.clearFrame,"reclosure clears last visible buffer");
        check(!gate.detach(1199),"reclosure starts a new grace period");
        check(gate.detach(1201),"grace expires");
        check(gate.update(1300,false,false,0,true)&&gate.clearFrame,"geometry change clears a new output layout");
        check(!gate.update(1400,false,true,0,false),"screen fade still needs source even with zero projection opacity");
        check(!gate.update(1500,false,false,12,false),"cover handoff still needs source/readiness frames");
        check(!gate.update(1600,true,false,0,false),"hold return and flat tail finish while still visible");
        check(gate.update(1800,false,false,0,false)&&gate.clearFrame,"completed hold/flat tail can idle");
        RenderWakeSignal wake=new RenderWakeSignal();
        long seen=wake.version();wake.signal();
        long start=System.nanoTime();wake.await(seen,1000);
        check(System.nanoTime()-start<500000000L,"change delivered before waiting cannot be lost");
        System.out.println("PASS: closed clear-once, warm reversal, immediate resume, repeated closure, scene clear, fade/cover exclusion, completed tails and no lost wakeup");
    }
}
