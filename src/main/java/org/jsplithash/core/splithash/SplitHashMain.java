package org.jsplithash.core.splithash;

/**
 * Main class to run the SplitHash test and compare with Scala version.
 */
public class SplitHashMain {
    
    public static void main(String[] args) {
        System.out.println("=== Java SplitHash Implementation ===");
        
        SHNode<Integer> s1 = null;
        SHNode<Integer> s2 = null;
        SHNode<Integer> s3 = null;
        
        int n = 50000;
        
        long startTime = System.currentTimeMillis();
        
        // Concatenation phase
        for (int i = 0; i < n; i++) {
            IntNode k1 = SplitHash.intNode(i);       // forwards
            IntNode k2 = SplitHash.intNode(n - i - 1); // backwards
            IntNode k3 = SplitHash.intNode(i % 63);  // repetitions
            
            s1 = SplitHash.concat(s1, k1);
            s2 = SplitHash.concat(k2, s2);
            s3 = SplitHash.concat(s3, k3);
            
            if ((i + 1) % 1000 == 0) {
                System.out.println("concat i: " + (i + 1));
                s1 = s1.chunk();
                s2 = s2.chunk();
                s3 = s3.chunk();
            }
        }
        
        long concatTime = System.currentTimeMillis() - startTime;
        System.out.println("Concat phase completed in " + concatTime + "ms");
        
        // Check s1 == s2
        if (!nodesEqual(s1, s2)) {
            throw new RuntimeException("Internal inconsistency: s1 != s2");
        }
        
        // Split phase
        startTime = System.currentTimeMillis();
        for (int i = 1; i < n; i++) {
            // split into left and right
            SHNode.SplitResult<Integer> split1 = s1.split(i);
            // concatenate left and right -> should return original (unsplit) version
            SHNode<Integer> cc = SplitHash.concat(split1.left(), split1.right()).chunk();
            
            if (!nodesEqual(cc, s1)) {
                System.out.println("MISMATCH at position " + i);
                System.out.println("s1.hashCode=" + s1.hashCode() + ", cc.hashCode=" + cc.hashCode());
                System.out.println("s1.size=" + s1.size() + ", cc.size=" + cc.size());
                System.out.println("s1.first=" + s1.first() + ", cc.first=" + cc.first());
                System.out.println("s1.last=" + s1.last() + ", cc.last=" + cc.last());
                System.out.println("s1.height=" + s1.height() + ", cc.height=" + cc.height());
                System.out.println("split1.left()=" + (split1.left() != null ? split1.left().getClass().getSimpleName() + " size=" + split1.left().size() : "null"));
                System.out.println("split1.right()=" + (split1.right() != null ? split1.right().getClass().getSimpleName() + " size=" + split1.right().size() : "null"));
                throw new RuntimeException("Internal inconsistency at split position " + i);
            }
            
            // split repetition into left and right
            SHNode.SplitResult<Integer> split3 = s3.split(i);
            SHNode<Integer> ccc = SplitHash.concat(split3.left(), split3.right()).chunk();
            
            if (!nodesEqual(ccc, s3)) {
                System.out.println("s3 MISMATCH at position " + i);
                System.out.println("s3.hashCode=" + s3.hashCode() + ", ccc.hashCode=" + ccc.hashCode());
                System.out.println("s3.size=" + s3.size() + ", ccc.size=" + ccc.size());
                System.out.println("s3.first=" + s3.first() + ", ccc.first=" + ccc.first());
                System.out.println("s3.last=" + s3.last() + ", ccc.last=" + ccc.last());
                System.out.println("s3.class=" + s3.getClass().getSimpleName() + ", ccc.class=" + ccc.getClass().getSimpleName());
                if (s3 instanceof ChunkedNode && ccc instanceof ChunkedNode) {
                    ChunkedNode<Integer> cn1 = (ChunkedNode<Integer>) s3;
                    ChunkedNode<Integer> cn2 = (ChunkedNode<Integer>) ccc;
                    SHNode<Integer>[] cn1Nodes = cn1.getNodes();
                    SHNode<Integer>[] cn2Nodes = cn2.getNodes();
                    System.out.println("cn1.nodes.length=" + cn1Nodes.length + ", cn2.nodes.length=" + cn2Nodes.length);
                    System.out.println("cn1.tree.length=" + cn1.getTree().length + ", cn2.tree.length=" + cn2.getTree().length);
                    for (int j = 0; j < Math.min(cn1Nodes.length, cn2Nodes.length); j++) {
                        SHNode<Integer> n1 = cn1Nodes[j];
                        SHNode<Integer> n2 = cn2Nodes[j];
                        boolean eq = n1.equalTo(n2);
                        System.out.println("  nodes[" + j + "]: " + n1.getClass().getSimpleName() + " hash=" + n1.hashCode() + " vs " + n2.getClass().getSimpleName() + " hash=" + n2.hashCode() + " eq=" + eq);
                    }
                }
                System.out.println("split3.left()=" + (split3.left() != null ? split3.left().getClass().getSimpleName() + " size=" + split3.left().size() : "null"));
                System.out.println("split3.right()=" + (split3.right() != null ? split3.right().getClass().getSimpleName() + " size=" + split3.right().size() + " first=" + split3.right().first() + " last=" + split3.right().last() : "null"));
                throw new RuntimeException("Internal inconsistency at repetition split position " + i);
            }
            
            if ((i + 1) % 1000 == 0) {
                System.out.println("split i: " + (i + 1));
            }
        }
        
        long splitTime = System.currentTimeMillis() - startTime;
        System.out.println("Split phase completed in " + splitTime + "ms");
        
        // Block-based concatenation phase
        int b = Math.max(1, n / 50);
        n = n / b;
        if (n == 0) n = 1; // guard against n=0
        SHNode<Integer> ss = null;
        
        startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            @SuppressWarnings("unchecked")
            SHNode<Integer>[] block = new SHNode[b];
            int o = i * b;
            
            for (int ii = 0; ii < b; ii++) {
                block[ii] = SplitHash.intNode(o + ii);
            }
            
            // Build the block
            while (block.length > 1) {
                block = SplitHash.doRound(block);
            }
            
            SHNode<Integer> cs = block[0].chunk();
            ss = SplitHash.concat(ss, cs);
            ss = ss.chunk();
        }
        
        long blockTime = System.currentTimeMillis() - startTime;
        System.out.println("Block phase completed in " + blockTime + "ms");
        
        if (!nodesEqual(s1, ss)) {
            throw new RuntimeException("Internal inconsistency: s1 != ss after block concat");
        }
        
        System.out.println("unlikely > 64 bit consumption: " + BinNode.getUnlikelyCount());
        System.out.println("\n=== All tests passed! ===");
        System.out.println("Total time: concat=" + concatTime + "ms, split=" + splitTime + "ms, block=" + blockTime + "ms");
    }
    
    private static <X> boolean nodesEqual(SHNode<X> a, SHNode<X> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // Structural equality like Scala's == for case classes
        return a.hashCode() == b.hashCode() && a.size() == b.size() && a.equalTo(b);
    }
}
