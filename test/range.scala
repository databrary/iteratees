package org.databrary.iteratee

import concurrent.Await
import concurrent.duration.Duration
import play.api.libs.iteratee._

object RangeSpec extends org.specs2.mutable.Specification {
  "range" should {
    def test(result : String, start : Long, stop : Long, input : String*) =
      Await.result(
	Enumerator(input.map(_.getBytes) : _*)
	&> Enumeratee.range(start, stop)
	|>>> Iteratee.consume[Array[Byte]](),
	Duration.Inf) must_== result.getBytes

    "include all input" in {
      test("Hello world", Long.MinValue, Long.MaxValue, "Hell", "o worl", "", "d")
    }

    "include exact input" in {
      test("Hello world", 0, 11, "Hello", " ", "world", "")
    }

    "trim tail within chunks" in {
      test("Hello", 0, 5, "Hell", "o ", "world")
    }

    "trim tail across chunks" in {
      test("Hello", 0, 5, "Hel", "lo", " ", "world")
    }
    
    "trim head within chunks" in {
      test("world", 6, 11, "Hel", "lo", " world")
    }

    "trim head across chunks" in {
      test("world", 6, 12, "Hel", "lo ", "", "wor", "ld")
    }

    "extract middle within chunks" in {
      test("lo wo", 3, 8, "H", "ello world")
    }

    "extract middle across chunks" in {
      test("lo wo", 3, 8, "Hel", "lo", " wor", "ld")
    }
  }
}
