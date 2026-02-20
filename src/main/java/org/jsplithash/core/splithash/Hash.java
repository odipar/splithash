package org.jsplithash.core.splithash;

/**
 * An 'infinitely' indexable and expandable Hash that *must* obey the following property:
 * The chance that two (slightly) different objects have equal hashes at index i
 * *must* exponentially decrease at higher indices.
 * 
 * Hashes that don't exhibit this property may cause SplitHash to get stuck in an infinite loop.
 */
public interface Hash {
    int hashAt(int i);
}
