/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import com.intellij.util.containers.hash.LinkedHashMap;
import jsr166e.extra.SequenceLock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.VM;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author max
 */
public class PagedFileStorage implements Forceable {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PagedFileStorage");

  public static final int MB = 1024 * 1024;

  private final static int LOWER_LIMIT;
  private final static int UPPER_LIMIT;
  public final static int BUFFER_SIZE;
  private static final int UNKNOWN_PAGE = -1;

  static {
    final int lower = 100;
    final int upper = SystemInfo.is64Bit && !PersistentEnumeratorDelegate.useBtree() ? 500 : 200;

    BUFFER_SIZE = Math.max(1, SystemProperties.getIntProperty("idea.paged.storage.page.size", 10)) * MB;
    final long max = VM.maxDirectMemory() - 2 * BUFFER_SIZE;
    LOWER_LIMIT = (int)Math.min(lower * MB, max);
    UPPER_LIMIT = (int)Math.min(Math.max(LOWER_LIMIT, SystemProperties.getIntProperty("idea.max.paged.storage.cache", upper) * MB), max);

    LOG.info("lower=" + (LOWER_LIMIT / MB) +
             "; upper=" + (UPPER_LIMIT / MB) +
             "; buffer=" + (BUFFER_SIZE / MB) +
             "; max=" + max
    );
  }

  // It is important to have ourLock after previous static constants as it depends on them
  private static final StorageLock ourLock = new StorageLock();

  private final StorageLockContext myStorageLockContext;
  private final boolean myNativeBytesOrder;
  private int myLastPage = UNKNOWN_PAGE;
  private int myLastPage2 = UNKNOWN_PAGE;
  private int myLastPage3 = UNKNOWN_PAGE;
  private ByteBufferWrapper myLastBuffer;
  private ByteBufferWrapper myLastBuffer2;
  private ByteBufferWrapper myLastBuffer3;
  private int myLastChangeCount;
  private int myLastChangeCount2;
  private int myLastChangeCount3;
  private int myStorageIndex;
  private final Object myLastAccessedBufferCacheLock = new Object();

  private static final int MAX_PAGES_COUNT = 0xFFFF;
  private static final int MAX_LIVE_STORAGES_COUNT = 0xFFFF;
  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();

  public void lock() {
    myStorageLockContext.myLock.lock();
  }

  public void unlock() {
    myStorageLockContext.myLock.unlock();
  }

  public StorageLockContext getStorageLockContext() {
    return myStorageLockContext;
  }

  private final byte[] myTypedIOBuffer;
  private volatile boolean isDirty = false;
  private final File myFile;
  protected volatile long mySize = -1;
  protected final int myPageSize;
  protected final boolean myValuesAreBufferAligned;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file, StorageLock lock, int pageSize, boolean valuesAreBufferAligned) throws IOException {
    this(file, lock.myDefaultStorageLockContext, pageSize, valuesAreBufferAligned);
  }

  public PagedFileStorage(File file, @Nullable StorageLockContext storageLockContext, int pageSize, boolean valuesAreBufferAligned) throws IOException {
    this(file, storageLockContext, pageSize, valuesAreBufferAligned, false);
  }

  public PagedFileStorage(File file,
                          @Nullable StorageLockContext storageLockContext,
                          int pageSize,
                          boolean valuesAreBufferAligned,
                          boolean nativeBytesOrder) throws IOException {
    myFile = file;
    myStorageLockContext = storageLockContext != null ? storageLockContext:ourLock.myDefaultStorageLockContext;
    myPageSize = Math.max(pageSize > 0 ? pageSize : BUFFER_SIZE, Page.PAGE_SIZE);
    myValuesAreBufferAligned = valuesAreBufferAligned;
    myStorageIndex = myStorageLockContext.myStorageLock.registerPagedFileStorage(this);
    myTypedIOBuffer = valuesAreBufferAligned ? null:new byte[8];
    myNativeBytesOrder = nativeBytesOrder;
  }
  public PagedFileStorage(File file, StorageLock lock) throws IOException {
    this(file, lock, BUFFER_SIZE, false);
  }

  public File getFile() {
    return myFile;
  }

  public void putInt(int addr, int value) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putInt(page_offset, value);
    } else {
      Bits.putInt(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 4);
    }
  }

  public int getInt(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page, false).getInt(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 4);
      return Bits.getInt(myTypedIOBuffer, 0);
    }
  }

  public final void putShort(int addr, short value) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putShort(page_offset, value);
    } else {
      Bits.putShort(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 2);
    }
  }

  int getOffsetInPage(int addr) {
    return addr % myPageSize;
  }

  ByteBuffer getByteBuffer(int address, boolean modify) {
    return getBuffer(address / myPageSize, modify);
  }

  public final short getShort(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page, false).getShort(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 2);
      return Bits.getShort(myTypedIOBuffer, 0);
    }
  }

  public void putLong(int addr, long value) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putLong(page_offset, value);
    } else {
      Bits.putLong(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 8);
    }
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void putByte(final int addr, final byte b) {
    put(addr, b);
  }

  public byte getByte(int addr) {
    return get(addr);
  }

  public long getLong(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page, false).getLong(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 8);
      return Bits.getLong(myTypedIOBuffer, 0);
    }
  }

  public byte get(int index) {
    int page = index / myPageSize;
    int offset = index % myPageSize;

    return getBuffer(page, false).get(offset);
  }

  public void put(int index, byte value) {
    int page = index / myPageSize;
    int offset = index % myPageSize;

    getBuffer(page).put(offset, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / myPageSize;
      int page_offset = i % myPageSize;

      int page_len = Math.min(l, myPageSize - page_offset);
      final ByteBuffer buffer = getBuffer(page, false);
      try {
        buffer.position(page_offset);
      }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("can't position buffer to offset " + page_offset + ", " +
                                           "buffer.limit=" + buffer.limit() + ", " +
                                           "page=" + page + ", " +
                                           "file=" + myFile.getName() + ", "+
                                           "file.length=" + length());
      }
      buffer.get(dst, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(int index, byte[] src, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / myPageSize;
      int page_offset = i % myPageSize;

      int page_len = Math.min(l, myPageSize - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      try {
        buffer.position(page_offset);
      }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("can't position buffer to offset " + page_offset);
      }
      buffer.put(src, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void close() {
    try {
      force();
    }
    finally {
      unmapAll();
      myStorageLockContext.myStorageLock.myIndex2Storage.remove(myStorageIndex);
      myStorageIndex = -1;
    }
  }

  private void unmapAll() {
    myStorageLockContext.myStorageLock.unmapBuffersForOwner(myStorageIndex, myStorageLockContext);

    synchronized (myLastAccessedBufferCacheLock) {
      myLastPage = UNKNOWN_PAGE;
      myLastPage2 = UNKNOWN_PAGE;
      myLastPage3 = UNKNOWN_PAGE;
      myLastBuffer = null;
      myLastBuffer2 = null;
      myLastBuffer3 = null;
    }
  }

  public void resize(int newSize) throws IOException {
    int oldSize = (int)myFile.length();
    if (oldSize == newSize && oldSize == length()) return;

    final long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    myStorageLockContext.myStorageLock.invalidateBuffer(myStorageIndex | (oldSize / myPageSize));
    //unmapAll(); // we do not need it since all page alighned buffers can be reused
    final long unmapAllFinished = IOStatistics.DEBUG ? System.currentTimeMillis():0;

    resizeFile(newSize);

    // it is not guaranteed that new partition will consist of null
    // after resize, so we should fill it manually
    int delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Resized "+myFile + " from " + oldSize + " to " + newSize + " for " + (finished - started) + ", unmap all:" + (finished - unmapAllFinished));
      }
    }
  }

  private void resizeFile(int newSize) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(myFile, RW);
    mySize = -1;
    try {
      raf.setLength(newSize);
    }
    finally {
      raf.close();
    }
    mySize = newSize;
  }

  private final static int MAX_FILLER_SIZE = 8192;
  private void fillWithZeros(int from, int length) {
    byte[] buff = new byte[MAX_FILLER_SIZE];
    Arrays.fill(buff, (byte)0);

    while (length > 0) {
      final int filled = Math.min(length, MAX_FILLER_SIZE);
      put(from, buff, 0, filled);
      length -= filled;
      from += filled;
    }
  }

  public final long length() {
    long size = mySize;
    if (size == -1) {
      mySize = size = myFile.length();
    }
    return size;
  }

  private ByteBuffer getBuffer(int page) {
    return getBuffer(page, true);
  }

  private ByteBuffer getBuffer(int page, boolean modify) {
    synchronized (myLastAccessedBufferCacheLock) {
      if (myLastPage == page) {
        ByteBuffer buf = myLastBuffer.getCachedBuffer();
        if (buf != null && myLastChangeCount == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer);
          return buf;
        }
      } else if (myLastPage2 == page) {
        ByteBuffer buf = myLastBuffer2.getCachedBuffer();
        if (buf != null && myLastChangeCount2 == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer2);
          return buf;
        }
      } else if (myLastPage3 == page) {
        ByteBuffer buf = myLastBuffer3.getCachedBuffer();
        if (buf != null && myLastChangeCount3 == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer3);
          return buf;
        }
      }
    }

    try {
      assert page >= 0 && page <= MAX_PAGES_COUNT:page;

      if (myStorageIndex == -1) {
        myStorageIndex = myStorageLockContext.myStorageLock.registerPagedFileStorage(this);
      }
      ByteBufferWrapper byteBufferWrapper = myStorageLockContext.myStorageLock.get(myStorageIndex | page);
      if (modify) markDirty(byteBufferWrapper);
      ByteBuffer buf = byteBufferWrapper.getBuffer();
      if (myNativeBytesOrder && buf.order() != ourNativeByteOrder) {
        buf.order(ourNativeByteOrder);
      }

      synchronized (myLastAccessedBufferCacheLock) {
        if (myLastPage != page) {
          myLastPage3 = myLastPage2;
          myLastBuffer3 = myLastBuffer2;
          myLastChangeCount3 = myLastChangeCount2;

          myLastPage2 = myLastPage;
          myLastBuffer2 = myLastBuffer;
          myLastChangeCount2 = myLastChangeCount;

          myLastBuffer = byteBufferWrapper;
          myLastPage = page;
        } else {
          myLastBuffer = byteBufferWrapper;
        }

        myLastChangeCount = myStorageLockContext.myStorageLock.myMappingChangeCount;
      }

      return buf;
    }
    catch (IOException e) {
      throw new MappingFailedException("Cannot map buffer", e);
    }
  }

  private void markDirty(ByteBufferWrapper buffer) {
    if (!isDirty) isDirty = true;
    buffer.markDirty();
  }

  public void force() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    if (isDirty) {
      myStorageLockContext.myStorageLock.flushBuffersForOwner(myStorageIndex, myStorageLockContext);
      isDirty = false;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Flushed "+myFile + " for " + (finished - started));
      }
    }
  }

  public boolean isDirty() {
    return isDirty;
  }

  public static class StorageLock {
    private static final int FILE_INDEX_MASK = 0xFFFF0000;
    private static final int FILE_INDEX_SHIFT = 16;
    public final StorageLockContext myDefaultStorageLockContext;
    private final ConcurrentIntObjectMap<PagedFileStorage> myIndex2Storage = new StripedLockIntObjectConcurrentHashMap<PagedFileStorage>();

    private final LinkedHashMap<Integer, ByteBufferWrapper> mySegments;
    private final SequenceLock mySegmentsAccessLock = new SequenceLock(); // protects map operations of mySegments, needed for LRU order, mySize and myMappingChangeCount
    // todo avoid locking for access

    private final SequenceLock mySegmentsAllocationLock = new SequenceLock();
    private final ConcurrentLinkedQueue<ByteBufferWrapper> mySegmentsToRemove = new ConcurrentLinkedQueue<ByteBufferWrapper>();
    private volatile long mySize;
    private volatile long mySizeLimit;
    private volatile int myMappingChangeCount;

    public StorageLock() {
      this(true);
    }

    public StorageLock(boolean checkThreadAccess) {
      myDefaultStorageLockContext = new StorageLockContext(this, checkThreadAccess);

      mySizeLimit = UPPER_LIMIT;
      mySegments = new LinkedHashMap<Integer, ByteBufferWrapper>(10, 0.75f) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ByteBufferWrapper> eldest) {
          return mySize > mySizeLimit;
        }

        @Nullable
        @Override
        public ByteBufferWrapper remove(Object key) {
          // this method can be called after removeEldestEntry
          ByteBufferWrapper wrapper = super.remove(key);
          if (wrapper != null) {
            ++myMappingChangeCount;
            mySegmentsToRemove.offer(wrapper);
            mySize -= wrapper.myLength;
          }
          return wrapper;
        }
      };
    }

    public void lock() {
      myDefaultStorageLockContext.myLock.lock();
    }

    public void unlock() {
      myDefaultStorageLockContext.myLock.unlock();
    }

    private int registerPagedFileStorage(@NotNull PagedFileStorage storage) {
      int registered = myIndex2Storage.size();
      assert registered <= MAX_LIVE_STORAGES_COUNT;
      int value = registered << FILE_INDEX_SHIFT;
      while(myIndex2Storage.cacheOrGet(value, storage) != storage) {
        ++registered;
        assert registered <= MAX_LIVE_STORAGES_COUNT;
        value = registered << FILE_INDEX_SHIFT;
      }
      return value;
    }

    private PagedFileStorage getRegisteredPagedFileStorageByIndex(int index) {
      return myIndex2Storage.get(index);
    }

    private ByteBufferWrapper get(Integer key) {
      ByteBufferWrapper wrapper;
      try {         // fast path
        mySegmentsAccessLock.lock();
        wrapper = mySegments.get(key);
        if (wrapper != null) return wrapper;
      }
      finally {
        mySegmentsAccessLock.unlock();
      }

      mySegmentsAllocationLock.lock();
      try {
        // check if anybody cared about our segment
        mySegmentsAccessLock.lock();
        try {
          wrapper = mySegments.get(key);
          if (wrapper != null) return wrapper;
        } finally {
          mySegmentsAccessLock.unlock();
        }

        long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;
        wrapper = createValue(key);

        if (IOStatistics.DEBUG) {
          long finished = System.currentTimeMillis();
          if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
            IOStatistics.dump(
              "Mapping " + wrapper.myLength + " from " + wrapper.myPosition + " file:" + wrapper.myFile + " for " + (finished - started));
          }
        }

        mySegmentsAccessLock.lock();
        try {
          mySegments.put(key, wrapper);
          mySize += wrapper.myLength;
        }
        finally {
          mySegmentsAccessLock.unlock();
        }

        ensureSize(mySizeLimit);

        return wrapper;
      }
      finally {
        mySegmentsAllocationLock.unlock();
      }
    }

    private void disposeRemovedSegments() {
      if (mySegmentsToRemove.isEmpty()) return;

      assert mySegmentsAllocationLock.isHeldByCurrentThread();
      Iterator<ByteBufferWrapper> iterator = mySegmentsToRemove.iterator();
      while(iterator.hasNext()) {
        iterator.next().dispose();
        iterator.remove();
      }
    }

    private void ensureSize(long sizeLimit) {
      assert mySegmentsAllocationLock.isHeldByCurrentThread();

      try {
        mySegmentsAccessLock.lock();
        while (mySize > sizeLimit) {
          // we still have to drop something
          mySegments.doRemoveEldestEntry();
        }
      } finally {
        mySegmentsAccessLock.unlock();
      }

      disposeRemovedSegments();
    }

    @NotNull
    private ByteBufferWrapper createValue(Integer key) {
      final int storageIndex = key & FILE_INDEX_MASK;
      PagedFileStorage owner = getRegisteredPagedFileStorageByIndex(storageIndex);
      assert owner != null: "No storage for index " + storageIndex;
      checkThreadAccess(owner.myStorageLockContext);
      int off = (key & MAX_PAGES_COUNT) * owner.myPageSize;
      if (off > owner.length()) {
        throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + owner.length());
      }

      int min = Math.min((int)(owner.length() - off), owner.myPageSize);
      ByteBufferWrapper wrapper = ByteBufferWrapper.readWriteDirect(owner.myFile, off, min);
      Throwable oome = null;
      while (true) {
        try {
          // ensure it's allocated
          wrapper.getBuffer();
          if (oome != null) {
            LOG.info("Successfully recovered OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MB + "MB " +
                     "new size limit: " + mySizeLimit / MB + "MB " +
                     "trying to allocate " + wrapper.myLength + " block");
          }
          return wrapper;
        }
        catch (IOException e) {
          throw new MappingFailedException("Cannot map buffer", e);
        } catch (OutOfMemoryError e) {
          oome = e;
          if (mySizeLimit > LOWER_LIMIT) {
            mySizeLimit -= owner.myPageSize;
          }
          long newSize = mySize - owner.myPageSize;
          if (newSize < 0) {
            LOG.info("Currently allocated:"+mySize);
            LOG.info("Mapping failed due to OOME. Current buffers: " + mySegments);
            LOG.info(oome);
            try {
              Class<?> aClass = Class.forName("java.nio.Bits");
              Field reservedMemory = aClass.getDeclaredField("reservedMemory");
              reservedMemory.setAccessible(true);
              Field maxMemory = aClass.getDeclaredField("maxMemory");
              maxMemory.setAccessible(true);
              synchronized (aClass) {
                LOG.info("Max memory:"+maxMemory.get(null) + ", reserved memory:" + reservedMemory.get(null));
              }
            }
            catch (Throwable t) {}
            throw new MappingFailedException(
              "Cannot recover from OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MB + "MB " +
              "new size limit: " + mySizeLimit / MB + "MB " +
              "trying to allocate " + wrapper.myLength + " block", e);
          }
          ensureSize(newSize); // next try
        }
      }
    }

    private void checkThreadAccess(StorageLockContext storageLockContext) {
      if (storageLockContext.myCheckThreadAccess && !storageLockContext.myLock.isHeldByCurrentThread()) {
        throw new IllegalStateException("Must hold StorageLock lock to access PagedFileStorage");
      }
    }

    private @Nullable Map<Integer, ByteBufferWrapper> getBuffersOrderedForOwner(int index, StorageLockContext storageLockContext) {
      mySegmentsAccessLock.lock();
      try {
        checkThreadAccess(storageLockContext);
        Map<Integer, ByteBufferWrapper> mineBuffers = null;
        for (Map.Entry<Integer, ByteBufferWrapper> entry : mySegments.entrySet()) {
          if ((entry.getKey() & FILE_INDEX_MASK) == index) {
            if (mineBuffers == null) {
              mineBuffers = new TreeMap<Integer, ByteBufferWrapper>(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                  return o1 - o2;
                }
              });
            }
            mineBuffers.put(entry.getKey(), entry.getValue());
          }
        }
        return mineBuffers;
      }
      finally {
        mySegmentsAccessLock.unlock();
      }
    }

    private void unmapBuffersForOwner(int index, StorageLockContext storageLockContext) {
      final Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext);

      if (buffers != null) {
        mySegmentsAccessLock.lock();
        try {
          for (Integer key : buffers.keySet()) {
            mySegments.remove(key);
          }
        }
        finally {
          mySegmentsAccessLock.unlock();
        }

        mySegmentsAllocationLock.lock();
        try {
          disposeRemovedSegments();
        } finally {
          mySegmentsAllocationLock.unlock();
        }
      }
    }

    private void flushBuffersForOwner(int index, StorageLockContext storageLockContext) {
      Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext);

      if (buffers != null) {
        mySegmentsAllocationLock.lock();
        try {
          for(ByteBufferWrapper buffer:buffers.values()) {
            buffer.flush();
          }
        }
        finally {
          mySegmentsAllocationLock.unlock();
        }
      }
    }

    public void invalidateBuffer(int page) {
      mySegmentsAccessLock.lock();
      try {
        mySegments.remove(page);
      } finally {
        mySegmentsAccessLock.unlock();
      }
      mySegmentsAllocationLock.lock();
      try {
        disposeRemovedSegments();
      }
      finally {
        mySegmentsAllocationLock.unlock();
      }
    }
  }

  public static class StorageLockContext {
    private final boolean myCheckThreadAccess;
    private final SequenceLock myLock;
    private final StorageLock myStorageLock;

    @Deprecated
    public StorageLockContext(StorageLock lock) {
      this(lock, true);
    }

    private StorageLockContext(StorageLock lock, boolean checkAccess) {
      myLock = new SequenceLock();
      myStorageLock = lock;
      myCheckThreadAccess = checkAccess;
    }

    public StorageLockContext(boolean checkAccess) {
      this(ourLock, checkAccess);
    }
  }
}
