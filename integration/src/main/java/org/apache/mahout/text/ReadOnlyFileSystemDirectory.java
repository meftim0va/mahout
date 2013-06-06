/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.text;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.BufferedIndexInput;
import org.apache.lucene.store.BufferedIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;

import java.io.IOException;
import java.util.Collection;

//NOCOMMIT: Not sure if there isn't a better way of doing this in 4.x.  Don't we have a Hadoop Directory impl somewhere?

/**
 * This class implements a Lucene Directory on top of a general FileSystem.
 * Currently it does not support locking.
 * <p/>
 * // TODO: Rename to FileSystemReadOnlyDirectory
 */
public class ReadOnlyFileSystemDirectory extends Directory {

  private final FileSystem fs;
  private final Path directory;
  private final int ioFileBufferSize;

  /**
   * Constructor
   *
   * @param fs
   * @param directory
   * @param create
   * @param conf
   * @throws IOException
   */
  public ReadOnlyFileSystemDirectory(FileSystem fs, Path directory, boolean create,
                                     Configuration conf) throws IOException {

    this.fs = fs;
    this.directory = directory;
    this.ioFileBufferSize = conf.getInt("io.file.buffer.size", 4096);

    if (create) {
      create();
    }

    boolean isDir = false;
    try {
      FileStatus status = fs.getFileStatus(directory);
      if (status != null) {
        isDir = status.isDir();
      }
    } catch (IOException e) {
      // file does not exist, isDir already set to false
    }
    if (!isDir) {
      throw new IOException(directory + " is not a directory");
    }
  }


  private void create() throws IOException {
    if (!fs.exists(directory)) {
      fs.mkdirs(directory);
    }

    boolean isDir = false;
    try {
      FileStatus status = fs.getFileStatus(directory);
      if (status != null) {
        isDir = status.isDir();
      }
    } catch (IOException e) {
      // file does not exist, isDir already set to false
    }
    if (!isDir) {
      throw new IOException(directory + " is not a directory");
    }

    // clear old index files
    FileStatus[] fileStatus =
            fs.listStatus(directory, LuceneIndexFileNameFilter.getFilter());
    for (int i = 0; i < fileStatus.length; i++) {
      if (!fs.delete(fileStatus[i].getPath(), true)) {
        throw new IOException("Cannot delete index file "
                + fileStatus[i].getPath());
      }
    }
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#list()
  */
  public String[] list() throws IOException {
    FileStatus[] fileStatus =
            fs.listStatus(directory, LuceneIndexFileNameFilter.getFilter());
    String[] result = new String[fileStatus.length];
    for (int i = 0; i < fileStatus.length; i++) {
      result[i] = fileStatus[i].getPath().getName();
    }
    return result;
  }

  @Override
  public String[] listAll() throws IOException {
    return list();
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#fileExists(java.lang.String)
  */
  public boolean fileExists(String name) throws IOException {
    return fs.exists(new Path(directory, name));
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#fileModified(java.lang.String)
  */
  public long fileModified(String name) {
    throw new UnsupportedOperationException();
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#touchFile(java.lang.String)
  */
  public void touchFile(String name) {
    throw new UnsupportedOperationException();
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#fileLength(java.lang.String)
  */
  public long fileLength(String name) throws IOException {
    return fs.getFileStatus(new Path(directory, name)).getLen();
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#deleteFile(java.lang.String)
  */
  public void deleteFile(String name) throws IOException {
    if (!fs.delete(new Path(directory, name), true)) {
      throw new IOException("Cannot delete index file " + name);
    }
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#renameFile(java.lang.String, java.lang.String)
  */
  public void renameFile(String from, String to) throws IOException {
    fs.rename(new Path(directory, from), new Path(directory, to));
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    //nocommit What should we be doing with the IOContext here?
    Path file = new Path(directory, name);
    if (fs.exists(file) && !fs.delete(file, true)) {
      // delete the existing one if applicable
      throw new IOException("Cannot overwrite index file " + file);
    }

    return new FileSystemIndexOutput(file, ioFileBufferSize);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {

  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    return new FileSystemIndexInput(new Path(directory, name), ioFileBufferSize);
  }


  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#makeLock(java.lang.String)
  */
  public Lock makeLock(final String name) {
    return new Lock() {
      public boolean obtain() {
        return true;
      }

      public void release() {
      }

      public boolean isLocked() {
        throw new UnsupportedOperationException();
      }

      public String toString() {
        return "Lock@" + new Path(directory, name);
      }
    };
  }

  /* (non-Javadoc)
  * @see org.apache.lucene.store.Directory#close()
  */
  public void close() throws IOException {
    // do not close the file system
  }

  /* (non-Javadoc)
  * @see java.lang.Object#toString()
  */
  public String toString() {
    return this.getClass().getName() + "@" + directory;
  }

  private class FileSystemIndexInput extends BufferedIndexInput {

    // shared by clones
    private class Descriptor {
      public final FSDataInputStream in;
      public long position; // cache of in.getPos()

      public Descriptor(Path file, int ioFileBufferSize) throws IOException {
        this.in = fs.open(file, ioFileBufferSize);
      }
    }

    private final Path filePath; // for debugging
    private final Descriptor descriptor;
    private final long length;
    private boolean isOpen;
    private boolean isClone;

    public FileSystemIndexInput(Path path, int ioFileBufferSize)
            throws IOException {
      super("FSII_" + path.getName(), ioFileBufferSize);
      filePath = path;
      descriptor = new Descriptor(path, ioFileBufferSize);
      length = fs.getFileStatus(path).getLen();
      isOpen = true;
    }

    protected void readInternal(byte[] b, int offset, int len)
            throws IOException {
      long position = getFilePointer();
      if (position != descriptor.position) {
        descriptor.in.seek(position);
        descriptor.position = position;
      }
      int total = 0;
      do {
        int i = descriptor.in.read(b, offset + total, len - total);
        if (i == -1) {
          throw new IOException("Read past EOF");
        }
        descriptor.position += i;
        total += i;
      } while (total < len);
    }

    public void close() throws IOException {
      if (!isClone) {
        if (isOpen) {
          descriptor.in.close();
          isOpen = false;
        } else {
          throw new IOException("Index file " + filePath + " already closed");
        }
      }
    }

    protected void seekInternal(long position) {
      // handled in readInternal()
    }

    public long length() {
      return length;
    }

    protected void finalize() throws IOException {
      if (!isClone && isOpen) {
        close(); // close the file
      }
    }

    public BufferedIndexInput clone() {
      FileSystemIndexInput clone = (FileSystemIndexInput) super.clone();
      clone.isClone = true;
      return clone;
    }
  }

  private class FileSystemIndexOutput extends BufferedIndexOutput {

    private final Path filePath; // for debugging
    private final FSDataOutputStream out;
    private boolean isOpen;

    public FileSystemIndexOutput(Path path, int ioFileBufferSize)
            throws IOException {
      filePath = path;
      // overwrite is true by default
      out = fs.create(path, true, ioFileBufferSize);
      isOpen = true;
    }

    public void flushBuffer(byte[] b, int offset, int size) throws IOException {
      out.write(b, offset, size);
    }

    public void close() throws IOException {
      if (isOpen) {
        super.close();
        out.close();
        isOpen = false;
      } else {
        throw new IOException("Index file " + filePath + " already closed");
      }
    }

    public void seek(long pos) throws IOException {
      throw new UnsupportedOperationException();
    }

    public long length() throws IOException {
      return out.getPos();
    }

    protected void finalize() throws IOException {
      if (isOpen) {
        close(); // close the file
      }
    }
  }

}