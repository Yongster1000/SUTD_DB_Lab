package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private final File f;
    private final TupleDesc td;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        int pageSize = BufferPool.getPageSize();
        byte[] data = new byte[pageSize];
        
        // RandomAccessFile allows us to jump to any byte in the file
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int offset = pid.getPageNumber() * pageSize;
            raf.seek(offset);
            raf.readFully(data);
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException e) {
            throw new IllegalArgumentException("HeapFile: Could not read page from disk");
        }
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int) (f.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(this, tid);
    }
}

/**
 * Helper class that implements DbFileIterator.
 */
class HeapFileIterator implements DbFileIterator {
    private final HeapFile hf;
    private final TransactionId tid;
    private int curPageNum;
    private Iterator<Tuple> curPageIterator;

    public HeapFileIterator(HeapFile hf, TransactionId tid) {
        this.hf = hf;
        this.tid = tid;
        this.curPageIterator = null; // Ensure it starts null
    }

    @Override
    public void open() throws DbException, TransactionAbortedException {
        curPageNum = 0;
        // Only attempt to load if there is at least one page
        if (hf.numPages() > 0) {
            curPageIterator = getPageIterator(curPageNum);
        } else {
            // Empty file: iterator stays empty
            curPageIterator = Collections.emptyIterator();
        }
    }

    private Iterator<Tuple> getPageIterator(int pageNo) 
            throws DbException, TransactionAbortedException {
        HeapPageId pid = new HeapPageId(hf.getId(), pageNo);
        // This MUST return a non-null page if BufferPool is working
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
        return page.iterator();
    }

    @Override
    public boolean hasNext() throws DbException, TransactionAbortedException {
        if (curPageIterator == null) return false;

        // While current iterator is empty, try the next page
        while (!curPageIterator.hasNext() && curPageNum < hf.numPages() - 1) {
            curPageNum++;
            curPageIterator = getPageIterator(curPageNum);
        }
        
        return curPageIterator.hasNext();
    }

    @Override
    public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
        if (!hasNext()) throw new NoSuchElementException("No more tuples");
        return curPageIterator.next();
    }

    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        open();
    }

    @Override
    public void close() {
        curPageIterator = null;
    }
}