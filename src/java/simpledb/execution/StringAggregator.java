package simpledb.execution;

import simpledb.common.Type;
import simpledb.storage.Field;
import simpledb.storage.IntField;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;
import simpledb.storage.TupleIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    // State variables
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    
    // We only need one HashMap here because we only support COUNT
    private Map<Field, Integer> counts;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        if (what != Op.COUNT) {
            throw new IllegalArgumentException("StringAggregator only supports the COUNT operator.");
        }
        
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        
        this.counts = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field groupField = null;
        if (gbfield != Aggregator.NO_GROUPING) {
            groupField = tup.getField(gbfield);
        }
        
        // Since we only do COUNT, we just increment the value for this group.
        // If the group doesn't exist yet, getOrDefault starts it at 0, then we add 1.
        counts.put(groupField, counts.getOrDefault(groupField, 0) + 1);
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        
        // 1. Build the TupleDesc based on grouping
        TupleDesc td;
        if (gbfield == Aggregator.NO_GROUPING) {
            td = new TupleDesc(new Type[]{Type.INT_TYPE});
        } else {
            td = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
        }
        
        // 2. Build the result tuples
        ArrayList<Tuple> tuples = new ArrayList<>();
        for (Map.Entry<Field, Integer> entry : counts.entrySet()) {
            Tuple t = new Tuple(td);
            
            if (gbfield == Aggregator.NO_GROUPING) {
                t.setField(0, new IntField(entry.getValue()));
            } else {
                t.setField(0, entry.getKey());
                t.setField(1, new IntField(entry.getValue()));
            }
            
            tuples.add(t);
        }
        
        // 3. Return the iterator
        return new TupleIterator(td, tuples);
    }

}
