package org.databrary.iteratee

import java.util.zip._
import concurrent.Await
import concurrent.duration.Duration
import play.api.libs.iteratee._

object ZipSpec extends org.specs2.mutable.Specification {
  def crc(string : String) = {
    val crc = new CRC32
    crc.update(string.getBytes)
    crc.getValue
  }

  "zip" should {
    def test(stream : ZipFile.Stream*) = {
      val buf = new java.io.ByteArrayOutputStream()
      val zip = new ZipOutputStream(buf)
      stream.foreach(_.fold(e => zip.putNextEntry(e.clone.asInstanceOf[ZipEntry]), zip.write))
      zip.close

      Await.result(
	Enumerator(stream : _*)
	&> ZipFile.zip()
	|>>> Iteratee.consume[Array[Byte]](),
	Duration.Inf) must_== buf.toByteArray
    }

    "empty zip" in {
      test()
    }

    "single directory" in {
      test(Left(new ZipFile.DirEntry("foo", time = 0, comment = "directory")))
    }

    "empty files" in {
      test(Left(new ZipFile.DirEntry("foo")),
	Left(new ZipFile.StoredEntry("empty", size = 0, crc = crc(""), time = 1234)),
	Left(new ZipFile.DeflatedEntry("foo/zzz")))
    }

    "stored and deflated data" in {
      test(Left(new ZipFile.DirEntry("foo")),
	Left(new ZipFile.StoredEntry("stuff", size = 12, crc = crc("Hello world."))),
	Right("Hello ".getBytes), Right("world.".getBytes),
	Left(new ZipFile.DeflatedEntry("foo/zzz", comment = "compressed")),
	Right("abcdefghijk".getBytes), Right("".getBytes), Right("lmmmmmmm".getBytes))
    }

    "not produce unconsumed output" in {
      Await.result(
	Enumerator[ZipFile.Stream](null)
	&> ZipFile.zip()
	|>>> Done[Array[Byte], String]("ignored"),
	Duration.Inf) must_== "ignored"
    }

    "not produce later unconsumed output" in {
      Await.result(
	Enumerator[ZipFile.Stream](Left(new ZipFile.DirEntry("foo")), null)
	&> ZipFile.zip()
	|>>> Iteratee.head[Array[Byte]],
	Duration.Inf).flatMap(_.headOption) must_== Some('P')
    }
  }
}
