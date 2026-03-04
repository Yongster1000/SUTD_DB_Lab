package simpledb.storage;

import simpledb.common.Type;

import java.io.Serializable;
import java.util.*;
import java.util.Iterator;

/**
 * TupleDesc describes the schema of a tuple.
 */
public class TupleDesc implements Serializable {

    /**
     * A help class to facilitate organizing the information of each field
     * */
    public static class TDItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The type of the field
         * */
        public final Type fieldType;
        
        /**
         * The name of the field
         * */
        public final String fieldName;

        public TDItem(Type t, String n) {
            this.fieldName = n;
            this.fieldType = t;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }

    private final List<TDItem> tdItems;

    public Iterator<TDItem> iterator() {
        // some code goes here
        return this.tdItems.iterator();
    }
    
    private static final long serialVersionUID = 1L;

    /**
     * Create a new TupleDesc with typeAr.length fields with fields of the
     * specified types, with associated named fields.
     * * @param typeAr
     * array specifying the number of and types of fields in this
     * TupleDesc. It must contain at least one entry.
     * @param fieldAr
     * array specifying the names of the fields. Note that names may
     * be null.
     */
    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        this.tdItems = new ArrayList<>();
        for (int i = 0; i < typeAr.length; i++) {
            this.tdItems.add(new TDItem(typeAr[i], fieldAr[i]));
        }
    }

    /**
     * Constructor. Create a new tuple desc with typeAr.length fields with
     * fields of the specified types, with anonymous (unnamed) fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     */
    public TupleDesc(Type[] typeAr) {
        this.tdItems = new ArrayList<>();
        for (int i = 0; i < typeAr.length; i++) {
            this.tdItems.add(new TDItem(typeAr[i], null));
        }
    }

    /**
     * @return the number of fields in this TupleDesc
     */
    public int numFields() {
        // some code goes here
        return this.tdItems.size();
    }

    /**
     * Gets the (possibly null) field name of the ith field of this TupleDesc.
     * 
     * @param i
     *            index of the field name to return. It must be a valid index.
     * @return the name of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public String getFieldName(int i) throws NoSuchElementException {
        // some code goes here
        if (i < 0 || i >= this.tdItems.size()) {
            throw new NoSuchElementException("Index " + i + " is not a valid field reference.");
        }
        return this.tdItems.get(i).fieldName;
    }

    /**
     * Gets the type of the ith field of this TupleDesc.
     * 
     * @param i
     *            The index of the field to get the type of. It must be a valid
     *            index.
     * @return the type of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public Type getFieldType(int i) throws NoSuchElementException {
        if (i < 0 || i >= this.tdItems.size()) {
            throw new NoSuchElementException("Index " + i + " is not a valid field reference.");
        }
        return this.tdItems.get(i).fieldType;
    }

    /**
     * Find the index of the field with a given name.
     * 
     * @param name
     *            name of the field.
     * @return the index of the field that is first to have the given name.
     * @throws NoSuchElementException
     *             if no field with a matching name is found.
     */
    public int fieldNameToIndex(String name) throws NoSuchElementException {
        if (name == null) {
            throw new NoSuchElementException("null is not a valid field name.");
        }
        
        for (int i = 0; i < this.tdItems.size(); i++) {
            String currentFieldName = this.tdItems.get(i).fieldName;
            // Make sure the current field actually has a name before comparing
            if (currentFieldName != null && currentFieldName.equals(name)) {
                return i;
            }
        }
        
        // If we get through the whole loop and find nothing, throw the error
        throw new NoSuchElementException("Field name '" + name + "' was not found.");
    }

    /**
     * @return The size (in bytes) of tuples corresponding to this TupleDesc.
     *         Note that tuples from a given TupleDesc are of a fixed size.
     */
    public int getSize() {
        int size = 0;
        for (TDItem item : this.tdItems) {
            size += item.fieldType.getLen();
        }
        return size;
    }

    /**
     * Merge two TupleDescs into one, with td1.numFields + td2.numFields fields,
     * with the first td1.numFields coming from td1 and the remaining from td2.
     * 
     * @param td1
     *            The TupleDesc with the first fields of the new TupleDesc
     * @param td2
     *            The TupleDesc with the last fields of the TupleDesc
     * @return the new TupleDesc
     */

    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        // some code goes here
        // 1. Figure out the total size of the new merged schema
        int totalFields = td1.numFields() + td2.numFields();
        
        // 2. Create arrays to hold the merged types and names
        Type[] mergedTypes = new Type[totalFields];
        String[] mergedNames = new String[totalFields];
        
        // 3. Copy over all the fields from the first TupleDesc (td1)
        for (int i = 0; i < td1.numFields(); i++) {
            mergedTypes[i] = td1.getFieldType(i);
            mergedNames[i] = td1.getFieldName(i);
        }
        
        // 4. Copy over all the fields from the second TupleDesc (td2)
        int offset = td1.numFields();
        for (int i = 0; i < td2.numFields(); i++) {
            mergedTypes[offset + i] = td2.getFieldType(i);
            mergedNames[offset + i] = td2.getFieldName(i);
        }
        
        // 5. Use our existing constructor to create the new TupleDesc
        return new TupleDesc(mergedTypes, mergedNames);
    }

    /**
     * Compares the specified object with this TupleDesc for equality. Two
     * TupleDescs are considered equal if they have the same number of items
     * and if the i-th type in this TupleDesc is equal to the i-th type in o
     * for every i.
     * 
     * @param o
     *            the Object to be compared for equality with this TupleDesc.
     * @return true if the object is equal to this TupleDesc.
     */

    public boolean equals(Object o) {
        // 1. Is 'o' even a TupleDesc?
        if (!(o instanceof TupleDesc)) {
            return false;
        }

        TupleDesc other = (TupleDesc) o;

        // 2. Do they have the same number of fields?
        if (this.numFields() != other.numFields()) {
            return false;
        }

        // 3. Is every single field type identical in the same order?
        for (int i = 0; i < this.numFields(); i++) {
            if (!this.getFieldType(i).equals(other.getFieldType(i))) {
                return false;
            }
        }

        return true;
    }

    public int hashCode() {
        // If you want to use TupleDesc as keys for HashMap, implement this so
        // that equal objects have equals hashCode() results
        // We use the field types to generate a hash because 
        // our equals() method only cares about field types.
        int hash = 7;
        for (TDItem item : tdItems) {
            hash = 31 * hash + item.fieldType.hashCode();
        }
        return hash;
    }

    /**
     * Returns a String describing this descriptor. It should be of the form
     * "fieldType[0](fieldName[0]), ..., fieldType[M](fieldName[M])", although
     * the exact format does not matter.
     * 
     * @return String describing this descriptor.
     */
    public String toString() {
        // some code goes here
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tdItems.size(); i++) {
            sb.append(tdItems.get(i).toString());
            // Add a comma and space between items, but not after the last one
            if (i < tdItems.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
