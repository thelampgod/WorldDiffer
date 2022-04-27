package com.github.thelampgod;

import com.github.thelampgod.util.ProgressMonitor;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocalThread;
import lombok.NonNull;
import net.daporkchop.lib.common.function.throwing.EConsumer;
import net.daporkchop.lib.common.ref.Ref;
import net.daporkchop.lib.common.ref.ThreadRef;
import net.daporkchop.lib.math.vector.i.Vec2i;
import net.daporkchop.lib.minecraft.region.util.ChunkProcessor;
import net.daporkchop.lib.minecraft.world.Chunk;
import net.daporkchop.lib.minecraft.world.MinecraftSave;
import net.daporkchop.lib.minecraft.world.World;
import net.daporkchop.lib.minecraft.world.format.anvil.AnvilSaveFormat;
import net.daporkchop.lib.minecraft.world.format.anvil.AnvilWorldManager;
import net.daporkchop.lib.minecraft.world.format.anvil.region.RegionFile;
import net.daporkchop.lib.minecraft.world.format.anvil.region.RegionOpenOptions;
import net.daporkchop.lib.minecraft.world.impl.MinecraftSaveConfig;
import net.daporkchop.lib.minecraft.world.impl.SaveBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static net.daporkchop.lib.common.util.PorkUtil.CPU_COUNT;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: <world1> <world2>");
            System.exit(1);
        }

        try (MinecraftSave save = new SaveBuilder()
                .setInitFunctions(new MinecraftSaveConfig()
                        .openOptions(new RegionOpenOptions().access(RegionFile.Access.READ_ONLY).mode(RegionFile.Mode.MMAP_FULL)))
                .setFormat(new AnvilSaveFormat(new File(args[0]))).build()) {


            int dim = 0;
            World world = save.world(dim);


            List<ChunkProcessor> processors = new ArrayList<>();

            {
                ProgressMonitor monitor = new ProgressMonitor(1L);
                processors.add((current, estimatedTotal, column) -> monitor.set(current, estimatedTotal));
            }

            {
                Ref<char[]> refCache = ThreadRef.late(() -> new char[32768]);
                Ref<char[]> dstCache = ThreadRef.late(() -> new char[32768]);
                processors.add((current, estimatedTotal, chunk) -> {
                    char[] ref = refCache.get();
                    char[] gen = dstCache.get();

                    for (int i = 0, x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 128; y++, i++) {
                                ref[i] = (char) ((chunk.getBlockId(x, y, z) << 4) /*| chunk.getBlockMeta(x, y, z)*/);
                            }
                        }
                    }
                });


                scanWorld(world, processors);
            }
        }

    }

    private static void scanWorld(World world, List<ChunkProcessor> processors) {
        Queue<Vec2i> regions = new ConcurrentLinkedQueue<>(((AnvilWorldManager) world.manager()).getRegions());

        AtomicLong curr = new AtomicLong(0L);
        AtomicLong estimatedTotal = new AtomicLong(regions.size() * (32L * 32L));

        Runnable action = () -> {
            for (Vec2i pos; (pos = regions.poll()) != null; ) {
                int xx = pos.getX() << 5;
                int zz = pos.getY() << 5;
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        Chunk chunk = world.column(xx + x, zz + z);
                        if (chunk.load(false)) {
                            long current = curr.getAndIncrement();
                            for (ChunkProcessor processor : processors) {
                                processor.handle(current, estimatedTotal.get(), chunk);
                            }
                            chunk.unload();
                        } else {
                            estimatedTotal.decrementAndGet();
                        }
                    }
                }
            }
        };


        //create and start workers
        Thread[] threads = new Thread[Integer.getInteger("threads", CPU_COUNT)];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new FastThreadLocalThread(action, "WorldDiffer worker #" + i);
            threads[i].start();
        }

        //wait for all workers to exit
        Stream.of(threads).forEach((EConsumer<Thread>) Thread::join);
    }

}