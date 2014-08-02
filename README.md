# Iteratee utilities

## Installation

Add the dependency:

> "org.databrary" %% "iteratees" % "0.1"

You can see examples of usage in the test cases.

## ZipFile

These functions provide an interface to java.util.zip.ZipOutputStream in the form of an Enumeratee.
There are two main functions providing a higher-level and lower-level interface.
Both follow proper reactive semantics, never blocking (except possibly on IO).
However, errors may still be delivered by thrown exceptions.

Also provided are some classes that provide more convenient ways of creating ZipEntries: `DirEntry`, `StoredEntry` and `DeflatedEntry`.

### zip

The lower-level interface, `org.databrary.ZipFile.zip`, provides an `Enumeratee[Either[ZipEntry, Array[Byte]], Array[Byte]]` that converts an incoming sequence of `Left(ZipEntry)`, each followed by any number of `Right(data)` blocks, into a zip file.
This is equivalent to calling this sequence of `ZipOutputStream.setNextEntry` and `ZipOutputStream.write` on each item, so all the restrictions of those interfaces still apply.

### flatZip

The higher-level interface, `org.databrary.ZipFile.flatZip`, provides a more convenient interface for creating zip streams out of different files or data sources.
It provides an `Enumeratee[ZipFile.StreamEntry, Array[Byte]]`, where a `StreamEntry` is a `ZipEntry` amended with an `Enumerator[Array[Byte]]`.

Two easy ways of creating StreamEntries are provided: `ZipFile.DirEntry` (which simply has no data content), and `ZipFile.fileEntry` which generates a StreamEntry from a file on disk.
You can also use `ZipFile.DeflatedStreamEntry` or define your own.

## Enumeratee utilities

### Range

Similar to a composition Enumeratee.take and Enumeratee.drop, `org.databrary.Enumeratee.range` is designed to work on Array[Byte] streams and counts individual bytes, allowing you to select an interior range of a byte stream.

### FilterOutputStream

Used in the construction of zip files, `org.databrary.Enumeratee.filterOutputStream` allows you to wrap a `java.io.FilterOutputStream` in an Enumartee efficiently.
Unlike Enumerator.outputStream, this follows correct Enumeratee semantics, so will push back and only accept input when more is needed.
