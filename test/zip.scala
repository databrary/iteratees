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
      buf.size
    }

    "empty zip" in {
      test() must_== 22
    }

    "single directory" in {
      test(Left(new ZipFile.DirEntry("foo", time = 0, comment = "directory"))) must_== 133
    }

    "empty files" in {
      test(Left(new ZipFile.DirEntry("foo")),
	Left(new ZipFile.StoredEntry("empty", size = 0, crc = crc(""), time = 1234)),
	Left(new ZipFile.DeflatedEntry("foo/zzz"))) must_== 318
    }

    "stored and deflated data" in {
      test(Left(new ZipFile.DirEntry("foo")),
	Left(new ZipFile.StoredEntry("stuff", size = 12, crc = crc("Hello world."))),
	Right("Hello ".getBytes), Right("world.".getBytes),
	Left(new ZipFile.DeflatedEntry("foo/zzz", comment = "compressed")),
	Right("abcdefghijk".getBytes), Right("".getBytes), Right("lmmmmmmm".getBytes)) must_== 354
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

  "flatZip" should {
    def test(stream : ZipFile.StreamEntry*) = {
      val buf = new java.io.ByteArrayOutputStream()
      val zip = new ZipOutputStream(buf)
      stream.foreach { e =>
	zip.putNextEntry(e.clone.asInstanceOf[ZipEntry])
	Await.result(
	  e.getContents |>>> Iteratee.foreach(zip.write(_)),
	  Duration.Inf)
      }
      zip.close

      Await.result(
	Enumerator(stream : _*)
	&> ZipFile.flatZip()
	|>>> Iteratee.consume[Array[Byte]](),
	Duration.Inf) must_== buf.toByteArray
      buf.size
    }

    "empty zip" in {
      test() must_== 22
    }

    "single directory" in {
      test(new ZipFile.DirEntry("foo", time = 0, comment = "directory")) must_== 133
    }

    "stored and deflated data" in {
      test(new ZipFile.DirEntry("foo"),
	new ZipFile.StoredEntry("stuff", size = 12, crc = crc("Hello world.")) with ZipFile.StreamEntry {
	  def getContents = Enumerator("Hello ".getBytes, "world.".getBytes)
	},
	new ZipFile.DeflatedStreamEntry("foo/zzz", 
	  Enumerator("".getBytes, "abcdefghijk".getBytes, "lmmmmmmm".getBytes),
	  comment = "compressed")) must_== 354
    }

    "real files" in {
      test(ZipFile.fileEntry(name = "test"),
	ZipFile.fileEntry(name = "test/zip.scala"),
	ZipFile.fileEntry(name = "build.sbt")) must be greaterThan(1024)
    }
  }
}
