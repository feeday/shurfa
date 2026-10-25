package com.feeday.ime;

/** Reject late recognition callbacks after cancellation or a change of editor. */
public final class VoiceSession {
    private long generation;
    private Object target;
    public long start(Object target) { this.target = target; return ++generation; }
    public boolean accepts(long id, Object currentTarget) {
        return target != null && generation == id && target == currentTarget;
    }
    public void cancel() { target = null; generation++; }
}
