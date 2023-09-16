package br.com.gamemods.regionmanipulator

import br.com.gamemods.nbtmanipulator.NbtIO
import java.io.*
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.ceil
import kotlin.math.min

/**
 * Contains usefull methods do read and write [Region] from [File].
 */
object RegionIO {
    /**
     * Reads a region identifying it's [RegionPos] by the name of the file.
     * @param file The file to be read. It must be named like r.1.-2.mca where 1 is it's xPos and -2 it's zPos.
     *
     * @throws IOException If an IO exception occurs while reading the MCA headers.
     * Exceptions which happens while loading the chunk's body are reported in [CorruptChunk.throwable]
     * which can be acceded using [Region.getCorrupt] or [Region.getCorruptChunks]
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readRegion(file: File): Region {
        val nameParts = file.name.split('.', limit = 4)
        val xPos = nameParts[1].toInt()
        val zPos = nameParts[2].toInt()
        val regionPos = RegionPos(xPos, zPos)
        return readRegion(file, regionPos)
    }

    private data class ChunkInfo(val location: Int, val size: Int, var lastModified: Date = Date(0))

    /**
     * Reads a region using a specified [RegionPos].
     * @param file The file to be read. Can have any name
     * @param pos The position of this region. Must match the content's otherwise it won't be manipulable.
     *
     * @throws IOException If an IO exception occurs while reading the MCA headers.
     * Exceptions which happens while loading the chunk's body are reported in [CorruptChunk.throwable]
     * which can be acceded using [Region.getCorrupt] or [Region.getCorruptChunks]
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readRegion(file: File, pos: RegionPos): Region {

        RandomAccessFile(file, "r").use { input ->
            val chunkInfos = Array(1024) {
                val loc = (input.read() shl 16) + (input.read() shl 8) + input.read()
                ChunkInfo(loc * 4096, input.read() * 4096).takeUnless { it.size == 0 }
            }

            for (i in 0 until 1024) {
                input.readInt().takeUnless { it == 0 }?.let {
                    chunkInfos[i]?.lastModified = Date( it * 1000L)
                }
            }

            val corruptChunks = mutableListOf<CorruptChunk>()

            val chunks = chunkInfos.mapIndexedNotNull { i, ci ->
                val info = ci ?: return@mapIndexedNotNull null
                var length: Int? = null
                var compression: Int? = null
                var data: ByteArray? = null
                try {
                    input.seek(info.location.toLong())
                    length = input.readInt()
                    compression = input.read()
                    check(compression == 1 || compression == 2) {
                        "Bad compression $compression . Chunk index: $i"
                    }

                    data = ByteArray(min(length, info.size))
                    val read = input.readFullyIfPossible(data)
                    if (read < data.size) {
                        data = data.copyOf(read)
                    }

                    if (length > data.size) {
                        throw EOFException("Could not read all $length bytes. Read only ${data.size} bytes in a sector of ${info.size} bytes")
                    }

                    val inputStream = when (compression) {
                        1 -> GZIPInputStream(ByteArrayInputStream(data))
                        2 -> InflaterInputStream(ByteArrayInputStream(data))
                        else -> error("Unexpected compression type $compression")
                    }

                    val nbt = NbtIO.readNbtFile(inputStream, false)
                    Chunk(info.lastModified, nbt)
                } catch (e: Throwable) {
                    corruptChunks += CorruptChunk(
                        pos, i, info.lastModified, data,
                        info.location.toLong(), info.size,
                        length, compression, e
                    )
                    null
                }
            }


            return Region(pos, chunks, corruptChunks)
        }
    }

    /**
     * Attempts to read `array.size` bytes from the file into the byte
     * array, starting at the current file pointer. This method reads
     * repeatedly from the file until the requested number of bytes are
     * read or the end of the file is reached.
     * This method blocks until the requested number of bytes are
     * read, the end of the stream is detected, or an exception is thrown.
     *
     * Differently from [RandomAccessFile.readFully], [EOFException] is never thrown.
     *
     * @return The number of bytes which was read into the array,
     * if the end of the file was reached the number will be lower then the array size
     * and the remaining bytes in the array will not be changed.
     */
    private fun RandomAccessFile.readFullyIfPossible(array: ByteArray): Int {
        val size = array.size
        var currentSize = 0;
        do {
            val read = this.read(array, currentSize, size - currentSize)
            if (read < 0) {
                return currentSize
            }
            currentSize += read
        } while (currentSize < size)
        return currentSize
    }

    private fun deflate(data: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        deflater.reset()
        deflater.setInput(data)
        deflater.finish()
        val bos = ByteArrayOutputStream(data.size)
        val buf = ByteArray(1024)
        try {
            while (!deflater.finished()) {
                val i = deflater.deflate(buf)
                bos.write(buf, 0, i)
            }
        } finally {
            deflater.end()
        }
        return bos.toByteArray()
    }

    /**
     * Saves a [Region] in a [File]. The region file will be entirely rebuilt.
     * @param file The file which will be written.
     * @param region The region which will be saved.
     *
     * @throws IOException If an IO exception occurs while writing to the file.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun writeRegion(file: File, region: Region) {
        val chunkInfoHeader = mutableListOf<ChunkInfo>()

        val heapData = ByteArrayOutputStream()
        val heap = DataOutputStream(heapData)
        var heapPos = 0
        var index = -1
        for (z in 0 until 32) {
            for (x in 0 until 32) {
                index++
                val pos = ChunkPos(region.position.xPos * 32 + x, region.position.zPos * 32 + z)
                val chunk = region[pos]

                if (chunk == null) {
                    chunkInfoHeader += ChunkInfo(0, 0)
                } else {
                    val chunkData = ByteArrayOutputStream()
                    //val chunkOut = DeflaterOutputStream(chunkData)
                    val chunkOut = chunkData
                    NbtIO.writeNbtFile(chunkOut, chunk.nbtFile, false)
                    //chunkOut.finish()
                    chunkOut.flush()
                    chunkOut.close()
                    val uncompressedChunkBytes = chunkData.toByteArray()
                    val chunkBytes = deflate(uncompressedChunkBytes, 7)
                    val sectionBytes = ByteArray((ceil((chunkBytes.size + 5) / 4096.0).toInt() * 4096) - 5) {
                        if (it >= chunkBytes.size) {
                            0
                        } else {
                            chunkBytes[it]
                        }
                    }

                    heap.writeInt(chunkBytes.size + 1)
                    heap.writeByte(2)
                    heap.write(sectionBytes)
                    chunkInfoHeader += ChunkInfo(8192 + heapPos, sectionBytes.size + 5, chunk.lastModified)
                    heapPos += 5 + sectionBytes.size
                }
            }
        }
        heap.flush()
        heap.close()
        val heapBytes = heapData.toByteArray()

        val headerData = ByteArrayOutputStream()
        val header = DataOutputStream(headerData)

        chunkInfoHeader.forEach {
            if (it.size > 0) {
                assert(it.location >= 8192) {
                    "Header location is too short, it must be >= 8192! Got ${it.location}"
                }
                assert(ByteBuffer.wrap(heapBytes, it.location - 8192, 4).int > 0) {
                    "Header location is pointing to an incorrect heap location"
                }
            }
            val sec = it.location / 4096
            header.writeByte((sec shr 16) and 0xFF)
            header.writeByte((sec shr 8) and 0xFF)
            header.writeByte(sec and 0xFF)

            val size = it.size / 4096
            header.writeByte(size)
        }

        chunkInfoHeader.forEach {
            header.writeInt((it.lastModified.time / 1000L).toInt())
        }
        header.close()
        val headerBytes = headerData.toByteArray()
        check(headerBytes.size == 8192) {
            "Failed to write the mca header. Size ${header.size()} != 4096"
        }

        file.outputStream().buffered().use {
            it.write(headerBytes)
            it.write(heapBytes)
            it.flush()
        }
    }
}
