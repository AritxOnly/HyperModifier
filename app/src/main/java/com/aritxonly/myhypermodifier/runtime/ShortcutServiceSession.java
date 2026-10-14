package com.aritxonly.myhypermodifier;

/** A bridge may stop only its own latest start, never a native start; existing Android bindings keep their service alive. */
final class ShortcutServiceSession {
    private boolean nativeUse;
    private int lastPrivateStart;
    void nativeUse() { nativeUse = true; }
    void privateStart(int startId) { lastPrivateStart = startId; }
    boolean canStop(int startId) { return !nativeUse && lastPrivateStart == startId; }
}
