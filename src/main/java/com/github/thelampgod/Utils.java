package com.github.thelampgod;

import io.netty.buffer.ByteBuf;
import lombok.NonNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

public class Utils {

    protected static final OpenOption[] WRITE_OPEN_OPTIONS = { StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING };
    protected static final CopyOption[] REPLACE_COPY_OPTIONS = { StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE };


    public void writeFully(@NonNull FileChannel channel, @NonNull ByteBuf data) throws IOException {
        do {
            data.readBytes(channel, data.readableBytes());
        } while (data.isReadable());
    }

    public void writeAndReplace(@NonNull Path dstPath, @NonNull ByteBuf data) throws IOException {
        Path tmpPath = dstPath.resolveSibling(dstPath.getFileName() + ".tmp");

        //write to temporary file
        try (FileChannel channel = FileChannel.open(tmpPath, WRITE_OPEN_OPTIONS)) {
            writeFully(channel, data);
        }

        //replace real file (atomically)
        Files.move(tmpPath, dstPath, REPLACE_COPY_OPTIONS);
    }
}
