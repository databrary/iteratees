package org.databrary.iteratee

import scala.concurrent.{ExecutionContext,Future}
import java.io.File
import java.util.zip._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumeratee.CheckDone

object ZipFile {
  private type Bytes = Array[Byte]
  /** Zip files are represented by a set of Left(ZipEntry) each followed by any number of Right(data) blocks. */
  type Stream = Either[ZipEntry, Bytes]

  /** Specialization of ZipEntry that also provides the contents of the file, as used by [[flatZip]]. */
  trait StreamEntry extends ZipEntry {
    def getContents : Enumerator[Bytes]
  }

  /** More convenient interface for constructing ZipEntry. */
  class Entry(name : String, size : Option[Long] = None, crc : Option[Long] = None, time : Long = System.currentTimeMillis, comment : String = "", method : Option[Int] = None)
    extends ZipEntry(name) {
    size.foreach(setSize(_))
    crc.foreach(setCrc(_))
    setTime(time)
    setComment(comment)
    method.foreach(setMethod(_))
  }
  /** A [[StreamEntry]] representing a directory (and so having no contents).
    * @param name Path of the directory within the archive. A trailing slash will be added if missing. */
  final class DirEntry(name : String, time : Long = System.currentTimeMillis, comment : String = "")
    extends Entry(name + (if (name.endsWith("/")) "" else "/"), time = time, comment = comment) with StreamEntry {
    def getContents = Enumerator.empty[Bytes]
  }
  /** An [[Entry]] to be archived using the STORED method, which requires knowing the size and crc beforehand. */
  class StoredEntry(name : String, size : Long, crc : Long, time : Long = System.currentTimeMillis, comment : String = "")
    extends Entry(name, Some(size), Some(crc), time, comment, Some(ZipEntry.STORED))
  /** An [[Entry]] to be archived using the DEFLATED method. */
  class DeflatedEntry(name : String, time : Long = System.currentTimeMillis, comment : String = "")
    extends Entry(name, None, None, time, comment, Some(ZipEntry.DEFLATED))

  class DeflatedStreamEntry(name : String, val getContents : Enumerator[Bytes], time : Long = System.currentTimeMillis, comment : String = "")
    extends DeflatedEntry(name, time, comment) with StreamEntry

  /** Create a [[StreamEntry]] from a file or directory on disk.
    * @param root Base directory in which to find file, defaulting to the current directory
    * @param name Path to regular file or directory (within root if relative) and name to use in zip file. If name is a directory the result is an (empty) [[DirEntry]].
    * @param stored Store file uncompressed rather than deflated. While this may be more computationally efficient, it requires reading the whole file twice in order to calculate the checksum, which causes this call to block (and may fail if the file changes before being zipped).
    */
  def fileEntry(root : File = new File("."), name : String, comment : String = "", stored : Boolean = false)(implicit ec : ExecutionContext) : StreamEntry = {
    val nf = new File(name)
    val file = if (nf.isAbsolute) nf else new File(root, name)
    if (file.isDirectory)
      new DirEntry(name, file.lastModified, comment)
    else if (file.isFile) {
      if (stored) {
	val size = file.length
	val f = new java.io.FileInputStream(file)
	val buf = new Bytes(8192)
	val crc = new CRC32
	var r = f.read(buf)
	while (r >= 0) {
	  crc.update(buf, 0, r)
	  r = f.read(buf)
	}
	f.close
	new StoredEntry(name, size, crc.getValue, file.lastModified, comment) with StreamEntry {
	  def getContents = Enumerator.fromFile(file)
	}
      } else
	new DeflatedEntry(name, file.lastModified, comment) with StreamEntry {
	  def getContents = Enumerator.fromFile(file)
	}
    } else
      throw new java.io.FileNotFoundException(file.getPath)
  }


  /** Produce a zip file out of a [[Stream]] sequence.
    * See ZipOutputStream for the meaning of the arguments. */
  def zip(charset : java.nio.charset.Charset = java.nio.charset.Charset.defaultCharset, comment : String = "", level : Int = Deflater.DEFAULT_COMPRESSION, method : Int = ZipOutputStream.DEFLATED)(implicit ec : ExecutionContext) : Enumeratee[Stream, Bytes] =
    Enumeratee.filterOutputStream[ZipOutputStream, Stream]({ out =>
      val zip = new ZipOutputStream(out, charset)
      zip.setComment(comment)
      zip.setLevel(level)
      zip.setMethod(method)
      zip
    }, { (zip, data) =>
      data.fold(zip.putNextEntry, zip.write)
    })

  private def flatten(implicit ec : ExecutionContext) : Enumeratee[StreamEntry, Stream] =
    /* slightly more efficient version of:
    Enumeratee.mapFlatten[StreamEntry].apply[Stream] {
      se => Enumerator(Left(se)) >>> se.getContents.map(Right(_))
    }
    */
    new CheckDone[StreamEntry, Stream] {
      def step[A](k: K[Stream, A]): K[StreamEntry, Iteratee[Stream, A]] = {
        case Input.El(se) => applyOn(Iteratee.flatten(se.getContents.map[Stream](Right(_)).apply(k(Input.El(Left(se))))))
        case Input.Empty => applyOn(k(Input.Empty))
        case Input.EOF => Done(Cont(k), Input.EOF)
      }
      def continue[A](k: K[Stream, A]) = Cont(step(k))
    }

  /** Produce a zip file out of a [[StreamEntry]] sequence.
    * Equivalent to [[zip]] with [[StreamEntry]].getContents flattened out into the stream. */
  def flatZip(charset : java.nio.charset.Charset = java.nio.charset.Charset.defaultCharset, comment : String = "", level : Int = Deflater.DEFAULT_COMPRESSION, method : Int = ZipOutputStream.DEFLATED)(implicit ec : ExecutionContext) : Enumeratee[StreamEntry, Bytes] =
    flatten ><> zip(charset, comment, level, method)
}
