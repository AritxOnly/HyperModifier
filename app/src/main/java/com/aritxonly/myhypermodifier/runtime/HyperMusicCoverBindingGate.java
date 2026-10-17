package com.aritxonly.myhypermodifier;

/** One attempt per lifecycle event; deferred startup is distinct from permanent incompatibility. */
final class HyperMusicCoverBindingGate {
    private long generation, attempted = -1;
    private boolean running, complete, failed;
    synchronized boolean begin() {
        if (running || complete || failed || attempted == generation) return false;
        attempted = generation; running = true; return true;
    }
    synchronized void sourceReady() { generation++; }
    synchronized boolean defer() { running = false; return generation != attempted; }
    synchronized void complete() { running = false; complete = true; }
    synchronized void fail() { running = false; failed = true; }
}
