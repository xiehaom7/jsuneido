/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import com.google.common.collect.AbstractIterator;

/**
 * Memory mapped file access.
 * <li>data is aligned to multiples of ALIGN (8)
 * <li>maximum allocation is CHUNK_SIZE (64mb)
 * <li>allocations cannot straddle chunks and will be bumped to next chunk
 * <li>offsets are divided by ALIGN and passed as int,
 * to reduce the space to store them
 * <li>therefore maximum file size is unsigned int max * ALIGN (32gb)
 * <p>
 * NOTE: When opening, trailing zero bytes are ignored.
 */
@ThreadSafe
public class MmapFile implements Storage {
	private static final int SHIFT = 3;
	private static final long MAX_SIZE = 0xffffffffL << SHIFT;
	private static final int ALIGN = (1 << SHIFT); // must be power of 2
	private static final int MASK = ALIGN - 1;
	private static final int MB = 1024 * 1024;
	private static final int CHUNK_SIZE = 64 * MB;
	private static final int MAX_CHUNKS = (int) (MAX_SIZE / CHUNK_SIZE + 1);
	private final FileChannel.MapMode mode;
	private final RandomAccessFile fin;
	private final FileChannel fc;
	@GuardedBy("this")
	private long file_size;
	private final MappedByteBuffer[] fm = new MappedByteBuffer[MAX_CHUNKS];

	/** @param mode Must be "r" or "rw" */
	public MmapFile(String filename, String mode) {
		this(new File(filename), mode);
	}

	/** @param mode Must be "r" or "rw" */
	public MmapFile(File file, String mode) {
		if ("r".equals(mode)) {
			if (!file.canRead())
				throw new RuntimeException("can't open " + file + " read-only");
			this.mode = FileChannel.MapMode.READ_ONLY;
		} else if ("rw".equals(mode)) {
			if (file.exists() && (! file.canRead() || ! file.canWrite()))
				throw new RuntimeException("can't open " + file);
			this.mode = FileChannel.MapMode.READ_WRITE;
		} else
			throw new RuntimeException("invalid mode " + mode);
		try {
			fin = new RandomAccessFile(file, mode);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("can't open or create " + file, e);
		}
		fc = fin.getChannel();
		findEnd();
	}

	/** handle zero padding caused by memory mapping */
	private void findEnd() {
		file_size = fileLength();
		if (file_size == 0)
			return;
		ByteBuffer buf = map(file_size - 1);
		int i = (int) ((file_size - 1) % CHUNK_SIZE) + 1;
		while (i > 0 && buf.getLong(i - 8) == 0)
			i -= 8;
		int chunk = (int) ((file_size - 1) / CHUNK_SIZE);
		file_size = (long) chunk * CHUNK_SIZE + align(i);
	}

	private long fileLength() {
		try {
			return fin.length();
		} catch (IOException e) {
			throw new RuntimeException("can't get file length", e);
		}
	}

	public synchronized int alloc(int n) {
		assert n < CHUNK_SIZE;
		n = align(n);

		// if insufficient room in this chunk, advance to next
		int remaining = CHUNK_SIZE - (int) (file_size % CHUNK_SIZE);
		if (n > remaining)
			file_size += remaining;

		long offset = file_size;
		file_size += n;
		return longToInt(offset);
	}

	public static int align(int n) {
		// requires ALIGN to be power of 2
		return ((n - 1) | (ALIGN - 1)) + 1;
	}

	/** @returns A unique instance of a ByteBuffer
	 * extending from the offset to the end of the chunk */
	public ByteBuffer buffer(int adr) {
		long offset = adr < 0 ? file_size + adr : intToLong(adr);
		return buf(offset);
	}

	private ByteBuffer buf(long offset) {
		ByteBuffer fmbuf = map(offset);
		synchronized(fmbuf) {
			fmbuf.position((int) (offset % CHUNK_SIZE));
			return fmbuf.slice();
		}
	}

	/** @return the file mapping containing the specified offset */
	private synchronized ByteBuffer map(long offset) {
		assert 0 <= offset && offset <= file_size;
		int chunk = (int) (offset / CHUNK_SIZE);
		if (fm[chunk] == null)
			try {
				fm[chunk] = fc.map(mode, (long) chunk * CHUNK_SIZE, CHUNK_SIZE);
				fm[chunk].order(ByteOrder.BIG_ENDIAN);
			} catch (IOException e) {
				throw new RuntimeException("MmapFile can't map chunk " + chunk, e);
			}
		return fm[chunk];
	}

	public void close() {
		Arrays.fill(fm, null); // might help gc
		try {
			fc.close();
			fin.close();
		} catch (IOException e) {
			throw new RuntimeException("MmapFile close failed", e);
		}
		// should truncate file but probably can't
		// since memory mappings won't all be finalized
		// so file size will be rounded up to chunk size
		// this is handled when re-opening
	}

	private static int longToInt(long n) {
		assert (n & MASK) == 0;
		assert n <= MAX_SIZE;
		return (int) (n >>> SHIFT) + 1; // +1 to avoid 0
	}

	private static long intToLong(int n) {
		return ((n - 1) & 0xffffffffL) << SHIFT;
	}

	@Override
	public Iterator<ByteBuffer> iterator(int adr) {
		return new Iter(adr);
	}

	private class Iter extends AbstractIterator<ByteBuffer> {
		private long offset;

		public Iter(int adr) {
			offset = intToLong(adr);
		}

		@Override
		protected ByteBuffer computeNext() {
			if (offset < file_size) {
				ByteBuffer buf = buf(offset);
				if (file_size - offset < CHUNK_SIZE) // last chunk
					buf.limit((int) (file_size - offset));
				offset += buf.remaining();
				return buf;
			}
			return endOfData();
		}

	}

	@Override
	public int sizeFrom(int adr) {
		return (int) (file_size - intToLong(adr));
	}

}
