package com.brianxiadong.lsmtree;

import java.io.IOException;
import java.util.Iterator;

public interface RangeQuery {
    Iterator<KeyValue> range(String startKey, String endKey, boolean includeStart, boolean includeEnd) throws IOException;
    Iterator<KeyValue> rangeReverse(String startKey, String endKey) throws IOException;
}

