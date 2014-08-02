package org.databrary.iteratee

import scala.concurrent.{ExecutionContext,Future}
import java.util.zip._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumeratee.CheckDone

object ZipFile {
  private type Bytes = Array[Byte]
  type Stream = Either[ZipEntry, Bytes]

  /** Specialization of [[java.util.zip.ZipEntry]] that also provides the contents of the file, as used by [[flatZip]]. */
  trait StreamEntry extends ZipEntry {
    def getContents : Enumerator[Bytes]
  }

  /** More convenient interface to constructing [[java.util.zip.ZipEntry]]. */
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
  /** An [[Entry]] to be archived using the [[java.util.zip.ZipEntry.STORED]] method, which requires knowing the size and crc beforehand. */
  class StoredEntry(name : String, size : Long, crc : Long, time : Long = System.currentTimeMillis, comment : String = "")
    extends Entry(name, Some(size), Some(crc), time, comment, Some(ZipEntry.STORED))
  /** An [[Entry]] to be archived using the [[java.util.zip.ZipEntry.DEFLATED]] method. */
  class DeflatedEntry(name : String, time : Long = System.currentTimeMillis, comment : String = "")
    extends Entry(name, None, None, time, comment, Some(ZipEntry.DEFLATED))


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

  def flatZip(charset : java.nio.charset.Charset = java.nio.charset.Charset.defaultCharset, comment : String = "", level : Int = Deflater.DEFAULT_COMPRESSION, method : Int = ZipOutputStream.DEFLATED)(implicit ec : ExecutionContext) : Enumeratee[StreamEntry, Bytes] =
    flatten ><> zip(charset, comment, level, method)
}
