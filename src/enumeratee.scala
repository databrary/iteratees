package org.databrary.iteratee

import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumeratee.CheckDone

object Enumeratee {
  private type Bytes = Array[Byte]

  /** Composition of Enumeratee.take and Enumeratee.drop with Longs. */
  def range(start : Long, stop : Long) : Enumeratee[Bytes, Bytes] = new CheckDone[Bytes, Bytes] {
    private def next[A](pos : Long, i : Iteratee[Bytes, A]) =
      new CheckDone[Bytes, Bytes] { def continue[A](k: K[Bytes, A]) = Cont(step(pos)(k)) } &> i
    def step[A](pos : Long)(k : K[Bytes, A]) : K[Bytes, Iteratee[Bytes, A]] = {
      case in @ Input.El(a) if pos + a.length <= start => Cont(step(pos+a.length)(k))
      case in @ Input.El(a) if pos < start && pos + a.length > stop => Done(k(Input.El(a.slice((start-pos).toInt, (stop-pos).toInt))), Input.Empty)
      case in @ Input.El(a) if pos < start => next(pos+a.length, k(Input.El(a.drop((start-pos).toInt))))
      case in @ Input.El(a) if pos + a.length <= stop => next(pos+a.length, k(in))
      case in @ Input.El(a) if pos < stop => Done(k(Input.El(a.take((stop-pos).toInt))), Input.Empty)
      case Input.Empty if pos < start => Cont(step(pos)(k))
      case Input.Empty if pos < stop => next(pos, k(Input.Empty))
      case in => Done(Cont(k), in)
    }
    def continue[A](k: K[Bytes, A]) = Cont(step(0)(k))
  }

  private class BufferOutputStream extends java.io.ByteArrayOutputStream() {
    private var closed : Boolean = false
    override def close() {
      closed = true;
    }
    def pull() : Input[Bytes] =
      if (count != 0) {
	val a = new Bytes(count)
	count = 0
	buf.copyToArray(a)
	Input.El(a)
      } else if (closed)
	Input.EOF
      else
	Input.Empty
  }

  /** Convert a [[java.io.FilterOutputStream]] to an equivalent Enumeratee.
    * @tparam F type of FilterOutputStream to wrap
    * @tparam I type of input F expects, defaulting to Array[Byte]
    * @param init Constructor for FilterOutputStream given an OutputStream
    * @param write Function to write a single I to F
    */
  def filterOutputStream[F <: java.io.FilterOutputStream, I](init : java.io.OutputStream => F, write : (F, I) => Unit = (f : F, i : Bytes) => f.write(i)) : Enumeratee[I, Bytes] = {
    final class Filter(out : BufferOutputStream, in : F) extends CheckDone[I, Bytes] {
      def step[A](k : K[Bytes, A]) : K[I, Iteratee[Bytes, A]] = {
	case Input.El(i) => {
	  write(in, i)
	  continue(k)
	}
	case Input.Empty =>
	  new CheckDone[I, Bytes] {
	    def continue[A](k : K[Bytes, A]) = Cont(step(k))
	  } &> k(Input.Empty)
	case Input.EOF => {
	  in.close()
	  continue(k)
	}
      }
      def continue[A](k : K[Bytes, A]) = out.pull match {
	case in@Input.El(_) => applyOn(k(in))
	case Input.Empty => Cont(step(k))
	case in@Input.EOF => Done(k(in), Input.Empty)
      }
    }

    new CheckDone[I, Bytes] {
      def continue[A](k : K[Bytes, A]) = {
	val out = new BufferOutputStream
	val in = init(out)
	new Filter(out, in).continue(k)
      }
    }
  }
}
