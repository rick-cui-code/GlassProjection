package io.github.sixzleo.tabfold.probe;

/** Generation checks prevent losing a pose change between polling and sleeping. */
final class RenderWakeSignal {
    private long generation;
    synchronized long version(){return generation;}
    synchronized void signal(){generation++;notifyAll();}
    synchronized void await(long observed,long timeoutMs)throws InterruptedException{
        if(generation==observed)wait(timeoutMs);
    }
}
