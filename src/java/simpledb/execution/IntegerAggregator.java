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
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    // State variables from constructor
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;

    // Maps to store our running group calculations.
    // Key: The group by Field (null if NO_GROUPING)
    // Value: The current aggregate value (or running sum for AVG)
    private Map<Field, Integer> aggregates;
    
    // We need a separate map to keep track of counts specifically for the AVG operator
    private Map<Field, Integer> counts;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        
        this.aggregates = new HashMap<>();
        this.counts = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        
        // 1. Figure out which group this tuple belongs to
        Field groupField = null;
        if (gbfield != Aggregator.NO_GROUPING) {
            groupField = tup.getField(gbfield);
        }
        
        // 2. Extract the integer value we need to aggregate
        IntField aggField = (IntField) tup.getField(afield);
        int value = aggField.getValue();
        
        // 3. If this is a brand new group we haven't seen before, initialize it
        if (!aggregates.containsKey(groupField)) {
            if (what == Op.COUNT) {
                aggregates.put(groupField, 1);
            } else {
                // For MIN, MAX, SUM, and AVG, the first value is just the value itself
                aggregates.put(groupField, value);
                counts.put(groupField, 1);
            }
        } 
        // 4. If we already have a running total for this group, update it
        else {
            int currentAgg = aggregates.get(groupField);
            
            switch (what) {
                case MIN:
                    aggregates.put(groupField, Math.min(currentAgg, value));
                    break;
                case MAX:
                    aggregates.put(groupField, Math.max(currentAgg, value));
                    break;
                case SUM:
                    aggregates.put(groupField, currentAgg + value);
                    break;
                case AVG:
                    // For AVG, we add to the sum and increment the count
                    aggregates.put(groupField, currentAgg + value);
                    counts.put(groupField, counts.get(groupField) + 1);
                    break;
                case COUNT:
                    aggregates.put(groupField, currentAgg + 1);
                    break;
            }
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        
        // 1. Build the correct TupleDesc based on whether we are grouping or not
        TupleDesc td;
        if (gbfield == Aggregator.NO_GROUPING) {
            td = new TupleDesc(new Type[]{Type.INT_TYPE});
        } else {
            td = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
        }
        
        // 2. Build a list of Tuples to hold our final calculated answers
        ArrayList<Tuple> tuples = new ArrayList<>();
        
        // 3. Loop through our HashMaps and generate the final rows
        for (Map.Entry<Field, Integer> entry : aggregates.entrySet()) {
            Field groupField = entry.getKey();
            int aggValue = entry.getValue();
            
            // If it's AVG, we need to divide the total sum by the count before outputting
            if (what == Op.AVG) {
                aggValue = aggValue / counts.get(groupField);
            }
            
            Tuple t = new Tuple(td);
            
            if (gbfield == Aggregator.NO_GROUPING) {
                t.setField(0, new IntField(aggValue));
            } else {
                t.setField(0, groupField);
                t.setField(1, new IntField(aggValue));
            }
            
            tuples.add(t);
        }
        
        // 4. Return an iterator over our newly created result tuples
        // TupleIterator is a SimpleDB utility class that turns an Iterable into an OpIterator
        return new TupleIterator(td, tuples);
    }

}
