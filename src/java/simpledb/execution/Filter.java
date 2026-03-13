package simpledb.execution;

import simpledb.transaction.TransactionAbortedException;
import simpledb.common.DbException;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;

import java.util.*;

/**
 * Filter is an operator that implements a relational select.
 */
public class Filter extends Operator {

    private static final long serialVersionUID = 1L;

    // Define variables to hold the predicate and the child operator
    private Predicate p;
    private OpIterator child;

    /**
     * Constructor accepts a predicate to apply and a child operator to read
     * tuples to filter from.
     * 
     * @param p
     *            The predicate to filter tuples with
     * @param child
     *            The child operator
     */
    public Filter(Predicate p, OpIterator child) {
        // some code goes here
        this.p = p;
        this.child = child;
    }

    public Predicate getPredicate() {
        // some code goes here
        return this.p;
    }

    public TupleDesc getTupleDesc() {
        // some code goes here
        // Filtering rows doesn't change the columns, so we just return the child's schema
        return child.getTupleDesc();
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        // some code goes here
        // We must call super.open() to initialize the Operator base class
        super.open();
        // Then we open the child to start pulling data
        child.open();
    }

    public void close() {
        // some code goes here
        // Always close super and child
        super.close();
        child.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        // Rewinding just means telling the child to start from the beginning again
        child.rewind();
    }

    /**
     * Operator.fetchNext implementation. Iterates over tuples from the
     * child operator, applying the predicate to them and returning those that
     * pass the predicate (i.e. for which the Predicate.filter() returns true.)
     * 
     * @return The next tuple that passes the filter, or null if there are no
     *         more tuples
     * @see Predicate#filter
     */
    protected Tuple fetchNext() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        // some code goes here
        
        // Loop through the child operator's tuples
        while (child.hasNext()) {
            Tuple t = child.next();
            
            // Check if the tuple passes the condition
            if (p.filter(t)) {
                // The moment we find one that passes, return it
                return t;
            }
        }
        
        // If we exhaust the child iterator and find nothing else, return null
        return null;
    }

    @Override
    public OpIterator[] getChildren() {
        // some code goes here
        return new OpIterator[] { this.child };
    }

    @Override
    public void setChildren(OpIterator[] children) {
        // some code goes here
        if (children != null && children.length > 0) {
            this.child = children[0];
        }
    }

}
