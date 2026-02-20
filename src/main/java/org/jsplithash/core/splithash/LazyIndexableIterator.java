package org.jsplithash.core.splithash;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Lazily consume the Iterator, whilst caching its elements at a certain index.
 * 
 * @param <X> the type of elements stored in the tree
 */
public final class LazyIndexableIterator<X> {
    
    private final Iterator<SHNode<X>> it;
    @SuppressWarnings("unchecked")
    private SHNode<X>[] values = new SHNode[8];
    private int size = 0;
    
    public LazyIndexableIterator(Iterator<SHNode<X>> it) {
        this.it = it;
    }
    
    public SHNode<X> get(int i) {
        if (i >= size) {
            if (i >= values.length) {
                // array exhausted, so extend it
                values = Arrays.copyOf(values, values.length * 2);
            }
            int ii = size;
            while (ii <= i && it.hasNext()) {
                values[ii] = it.next();
                ii++;
            }
            size = ii;
        }
        
        if (i >= size) return null;
        return values[i];
    }
    
    @SuppressWarnings("unchecked")
    public SHNode<X>[] firstReversed(int s) {
        SHNode<X>[] firstR = new SHNode[s];
        for (int i = 0; i < s; i++) {
            firstR[i] = values[s - i - 1];
        }
        return firstR;
    }
}
