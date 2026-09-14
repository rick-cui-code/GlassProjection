package io.github.sixzleo.tabfold.probe;

public final class RenderDrawGateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static boolean draw(RenderDrawGate g,boolean source,boolean force,float tilt,float dark){return g.draw(source,force,1168,1712,0,false,false,tilt,.1f,1,dark,1);}
    public static void main(String[] args){
        RenderDrawGate gate=new RenderDrawGate();
        check(draw(gate,false,false,3,0)&&gate.parametersChanged,"first frame initializes parameters");
        for(int i=0;i<100;i++)check(!draw(gate,false,false,3,0)&&!gate.parametersChanged,"identical output reuses its buffer");
        check(draw(gate,true,false,3,0)&&!gate.parametersChanged,"new source redraws without resending identical uniforms");
        check(draw(gate,false,false,3.001f,0)&&gate.parametersChanged,"even a small animation step renders");
        check(draw(gate,false,false,3.001f,.2f),"fade progression renders");
        check(draw(gate,false,true,3.001f,.2f),"capture and cover acknowledgements force a frame");
        check(gate.draw(false,false,2364,1672,3,true,true,3.001f,.1f,1,.2f,1),"panel/geometry change renders");
        gate.reset();check(draw(gate,false,false,3,0)&&gate.parametersChanged,"idle clear invalidates retained output parameters");
        System.out.println("PASS: identical output reuse, source-only redraw, subpixel animation/fade, forced capture/cover, geometry and idle reset");
    }
}
