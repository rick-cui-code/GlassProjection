package io.github.sixzleo.tabfold.projection;
interface IHelperHost {
    int ensureRunning() = 1;
    void stopHelpers() = 2;
    String status() = 3;
    boolean observeTouch(IBinder connection, boolean enabled) = 4;
    void destroy() = 16777114;
}
