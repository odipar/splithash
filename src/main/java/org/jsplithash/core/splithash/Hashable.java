package org.jsplithash.core.splithash;

/**
 * A Hashable object that can provide a hash and its parts.
 */
public interface Hashable {
    Hash hash();
    Hashable[] parts();
}
