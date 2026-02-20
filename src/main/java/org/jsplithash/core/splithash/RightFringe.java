package org.jsplithash.core.splithash;

import java.util.List;

/**
 * Represents a right fringe of a canonical tree for left-catenable operations.
 *
 * @param height  the height of the top node
 * @param top     the top-level nodes at this height
 * @param fringes the collected fringe arrays at each level below
 * @param <X> the type of elements stored in the tree
 */
public record RightFringe<X>(int height, SHNode<X>[] top, List<SHNode<X>[]> fringes) {}
