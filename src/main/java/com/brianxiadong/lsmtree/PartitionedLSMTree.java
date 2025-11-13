package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public class PartitionedLSMTree implements AutoCloseable, RangeQuery {
    private final List<LSMTree> shards;
    private final PartitionStrategy strategy;

    public PartitionedLSMTree(String dataDir, int numPartitions, int memTableMaxSize, PartitionStrategy strategy) throws IOException {
        this.strategy = strategy;
        this.shards = new ArrayList<>(numPartitions);
        for (int i = 0; i < numPartitions; i++) {
            String dir = dataDir + "/part-" + i;
            shards.add(new LSMTree(dir, memTableMaxSize));
        }
        io.micrometer.core.instrument.MeterRegistry registry = MetricsRegistry.get();
        for (int i = 0; i < shards.size(); i++) {
            final int idx = i;
            io.micrometer.core.instrument.Gauge.builder("lsm.sstable.count", this, t -> t.shards.get(idx).getSSTableCount())
                    .tag("shard", String.valueOf(idx)).register(registry);
            io.micrometer.core.instrument.Gauge.builder("lsm.memtable.size", this, t -> t.shards.get(idx).getActiveMemTableSize())
                    .tag("shard", String.valueOf(idx)).register(registry);
        }
    }

    public void put(String key, String value) throws IOException {
        int p = strategy.getPartition(key, shards.size());
        shards.get(p).put(key, value);
    }

    public void delete(String key) throws IOException {
        int p = strategy.getPartition(key, shards.size());
        shards.get(p).delete(key);
    }

    public String get(String key) {
        int p = strategy.getPartition(key, shards.size());
        return shards.get(p).get(key);
    }

    @Override
    public Iterator<KeyValue> range(String startKey, String endKey, boolean includeStart, boolean includeEnd) throws IOException {
        List<Integer> parts = strategy.getPartitionsForRange(startKey, endKey, shards.size());
        List<List<KeyValue>> sources = new ArrayList<>();
        for (Integer p : parts) {
            Iterator<KeyValue> it = shards.get(p).range(startKey, endKey, includeStart, includeEnd);
            List<KeyValue> list = new ArrayList<>();
            while (it.hasNext()) list.add(it.next());
            sources.add(list);
        }
        PriorityQueue<int[]> pq = new PriorityQueue<>((a,b)->{
            KeyValue ka = sources.get(a[0]).get(a[1]);
            KeyValue kb = sources.get(b[0]).get(b[1]);
            return ka.getKey().compareTo(kb.getKey());
        });
        for (int i = 0; i < sources.size(); i++) if (!sources.get(i).isEmpty()) pq.add(new int[]{i,0});
        List<KeyValue> out = new ArrayList<>();
        String last = null;
        while (!pq.isEmpty()) {
            int[] t = pq.poll();
            KeyValue kv = sources.get(t[0]).get(t[1]);
            if (!kv.isDeleted() && (last == null || !kv.getKey().equals(last))) {
                out.add(kv);
                last = kv.getKey();
            }
            if (t[1] + 1 < sources.get(t[0]).size()) pq.add(new int[]{t[0], t[1]+1});
        }
        return out.iterator();
    }

    @Override
    public Iterator<KeyValue> rangeReverse(String startKey, String endKey) throws IOException {
        List<KeyValue> list = new ArrayList<>();
        Iterator<KeyValue> it = range(startKey, endKey, true, true);
        while (it.hasNext()) list.add(it.next());
        list.sort((a,b)->b.getKey().compareTo(a.getKey()));
        return list.iterator();
    }

    @Override
    public void close() throws Exception {
        for (LSMTree t : shards) t.close();
    }
}
