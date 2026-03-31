package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.Permissions;
import simpledb.common.DbException;
import simpledb.common.DeadlockException;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;

    private final int numPages;
    
    /** * Use LinkedHashMap with accessOrder=true to implement LRU.
     * We synchronize on the BufferPool instance for thread safety.
     */
    private final LinkedHashMap<PageId, Page> pageStore;

    // Lab 3 Exercise 1: Lock Manager instance
    private final LockManager lockManager;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        // initialCapacity, loadFactor, accessOrder = true
        this.pageStore = new LinkedHashMap<>(numPages, 0.75f, true);
        this.lockManager = new LockManager();
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
        BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        
        // Lab 3 Exercise 1: Acquire the lock BEFORE accessing the page.
        lockManager.acquireLock(tid, pid, perm);

        synchronized (this) {
            // 1. Check if we already have it. LinkedHashMap moves it to end (MRU) automatically.
            if (pageStore.containsKey(pid)) {
                return pageStore.get(pid);
            }

            // 2. If not, check if we have space to add a new one
            if (pageStore.size() >= numPages) {
                evictPage();
            }

            // 3. Fetch from disk
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page page = file.readPage(pid);

            // 4. Store in cache and return
            pageStore.put(pid, page);
            return page;
        }
    }

    /**
     * Releases the lock on a page.
     */
    public void unsafeReleasePage(TransactionId tid, PageId pid) {
        // Lab 3 Exercise 1: Delegate to lock manager
        lockManager.releaseLock(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     */
    public void transactionComplete(TransactionId tid) {
        // Lab 3 Exercise 4: Default to commit
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // Lab 3 Exercise 1: Check with lock manager
        return lockManager.holdsLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // Lab 3 Exercise 4: Atomic Commit/Abort Logic
        synchronized (this) {
            if (commit) {
                try {
                    flushPages(tid);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to flush pages for transaction " + tid);
                }
            } else {
                // ABORT logic: Revert dirty pages for this tid to their before-images
                java.util.Set<PageId> pids = new java.util.HashSet<>(pageStore.keySet());
                for (PageId pid : pids) {
                    Page p = pageStore.get(pid);
                    if (p != null && tid.equals(p.isDirty())) {
                        pageStore.put(pid, p.getBeforeImage());
                    }
                }
            }
        }
        // Always release locks at the end of the transaction
        lockManager.releaseAllLocks(tid);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        DbFile file = Database.getCatalog().getDatabaseFile(tableId);
        List<Page> modifiedPages = file.insertTuple(tid, t);
        
        synchronized(this) {
            for (Page p : modifiedPages) {
                p.markDirty(true, tid);
                pageStore.put(p.getId(), p);
            }
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile file = Database.getCatalog().getDatabaseFile(tableId);
        List<Page> modifiedPages = file.deleteTuple(tid, t);
        
        synchronized(this) {
            for (Page p : modifiedPages) {
                p.markDirty(true, tid);
                pageStore.put(p.getId(), p);
            }
        }
    }

    public synchronized void flushAllPages() throws IOException {
        java.util.Set<PageId> pids = new java.util.HashSet<>(pageStore.keySet());
        for (PageId pid : pids) {
            flushPage(pid);
        }
    }

    public synchronized void discardPage(PageId pid) {
        pageStore.remove(pid);
    }

    private synchronized  void flushPage(PageId pid) throws IOException {
        Page p = pageStore.get(pid);
        if (p != null && p.isDirty() != null) {
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
            file.writePage(p);
            p.markDirty(false, null);
        }
    }

    /** Write all pages of the specified transaction to disk. */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        // Lab 3 Exercise 4: FORCE policy implementation
        
        // FIX: Create a snapshot of the entries to avoid ConcurrentModificationException
        // when flushPage() accesses the pageStore and reorders the LinkedHashMap.
        List<Page> pagesToCheck = new ArrayList<>(pageStore.values());
        
        for (Page p : pagesToCheck) {
            // If the page is dirty and was modified by THIS transaction...
            if (p.isDirty() != null && p.isDirty().equals(tid)) {
                flushPage(p.getId());
                // Update before image so subsequent aborts don't roll back too far
                p.setBeforeImage();
            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Under NO STEAL, we must NEVER evict a dirty page.
     */
    private synchronized void evictPage() throws DbException {
        // Lab 3 Exercise 3: NO STEAL Eviction logic
        PageId evictPid = null;
        java.util.Iterator<Map.Entry<PageId, Page>> it = pageStore.entrySet().iterator();
        
        while (it.hasNext()) {
            Map.Entry<PageId, Page> entry = it.next();
            if (entry.getValue().isDirty() == null) {
                evictPid = entry.getKey();
                break; 
            }
        }

        if (evictPid == null) {
            throw new DbException("NO STEAL: All pages in buffer pool are dirty. Cannot evict.");
        }

        pageStore.remove(evictPid);
    }

    // ====================================================================
    // Lab 3 Exercise 1 & 5: Internal Lock Manager Class
    // ====================================================================
    private class LockManager {
        private final Map<PageId, PageLock> lockMap = new ConcurrentHashMap<>();
        // Lab 3 Exercise 5: Dependency tracking for Wait-For Graph
        private final Map<TransactionId, PageId> waitingFor = new ConcurrentHashMap<>();

        public void acquireLock(TransactionId tid, PageId pid, Permissions perm) 
                throws TransactionAbortedException {
            
            PageLock lock = lockMap.computeIfAbsent(pid, k -> new PageLock());

            while (true) {
                synchronized (this) {
                    synchronized (lock) {
                        if (lock.tryAcquire(tid, perm)) {
                            waitingFor.remove(tid);
                            return;
                        }
                        waitingFor.put(tid, pid);
                    }

                    // Lab 3 Exercise 5: Cycle detection (Deadlock check)
                    if (isDeadlock(tid)) {
                        waitingFor.remove(tid);
                        throw new TransactionAbortedException();
                    }
                }

                try {
                    Thread.sleep(10); // Wait and retry
                } catch (InterruptedException e) {
                    throw new TransactionAbortedException();
                }
            }
        }

        // Lab 3 Exercise 5: DFS-based Cycle Detection
        private boolean isDeadlock(TransactionId tid) {
            return hasCycle(tid, tid, new HashSet<>());
        }

        private boolean hasCycle(TransactionId start, TransactionId current, Set<TransactionId> visited) {
            visited.add(current);
            PageId nextPid = waitingFor.get(current);
            if (nextPid == null) return false;

            PageLock lock = lockMap.get(nextPid);
            if (lock == null) return false;

            for (TransactionId holder : lock.getAllHolders()) {
                if (holder.equals(start)) return true;
                if (!visited.contains(holder)) {
                    if (hasCycle(start, holder, visited)) return true;
                }
            }
            return false;
        }

        public synchronized void releaseAllLocks(TransactionId tid) {
            for (PageId pid : lockMap.keySet()) {
                releaseLock(tid, pid);
            }
            waitingFor.remove(tid);
        }

        public synchronized void releaseLock(TransactionId tid, PageId pid) {
            PageLock lock = lockMap.get(pid);
            if (lock != null) {
                synchronized (lock) {
                    lock.release(tid);
                }
            }
        }

        public synchronized boolean holdsLock(TransactionId tid, PageId pid) {
            PageLock lock = lockMap.get(pid);
            return lock != null && lock.isHolder(tid);
        }
    }

    private class PageLock {
        private TransactionId exclusiveHolder;
        private final Set<TransactionId> sharedHolders;

        public PageLock() {
            this.exclusiveHolder = null;
            this.sharedHolders = new HashSet<>();
        }

        // Lab 3 Exercise 5: Helper for deadlock detection
        public Set<TransactionId> getAllHolders() {
            Set<TransactionId> holders = new HashSet<>(sharedHolders);
            if (exclusiveHolder != null) holders.add(exclusiveHolder);
            return holders;
        }

        public boolean tryAcquire(TransactionId tid, Permissions perm) {
            if (exclusiveHolder != null && exclusiveHolder.equals(tid)) return true;

            if (perm == Permissions.READ_ONLY) {
                if (exclusiveHolder == null) {
                    sharedHolders.add(tid);
                    return true;
                }
                return false;
            } else {
                if (exclusiveHolder == null && 
                   (sharedHolders.isEmpty() || (sharedHolders.size() == 1 && sharedHolders.contains(tid)))) {
                    exclusiveHolder = tid;
                    sharedHolders.remove(tid);
                    return true;
                }
                return false;
            }
        }

        public void release(TransactionId tid) {
            if (exclusiveHolder != null && exclusiveHolder.equals(tid)) {
                exclusiveHolder = null;
            }
            sharedHolders.remove(tid);
        }

        public boolean isHolder(TransactionId tid) {
            return (exclusiveHolder != null && exclusiveHolder.equals(tid)) || sharedHolders.contains(tid);
        }
    }
}