package com.brianxiadong.lsmtree.tools;

import com.brianxiadong.lsmtree.KeyValue;
import com.brianxiadong.lsmtree.LSMTree;

import java.util.Iterator;

public class RangeBenchmarkRunner {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "./bench-range";
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        LSMTree tree = new LSMTree(dir, 2048);
        for (int i = 0; i < n; i++) {
            String k = String.format("k%06d", i);
            tree.put(k, "v" + i);
        }
        System.gc();
        long start = System.nanoTime();
        Iterator<KeyValue> it = tree.range("k000000", "k999999", true, true);
        int c = 0;
        while (it.hasNext()) { it.next(); c++; }
        long end = System.nanoTime();
        double ms = (end - start)/1_000_000.0;
        System.out.println("Range count=" + c + " time_ms=" + ms);
        tree.close();
    }
}

