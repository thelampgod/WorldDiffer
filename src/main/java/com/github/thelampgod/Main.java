package com.github.thelampgod;

import br.com.gamemods.regionmanipulator.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: <world1> <world2>");
            System.exit(1);
        }

        HashMap<RegionPos, File> world2 = new HashMap<>();

        try (Stream<Path> fileStream1 = Files.list(Paths.get(args[1]))) {
            fileStream1.parallel()
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().endsWith(".mca"))
                    .map(Path::toFile)
                    .forEach(file -> world2.put(new RegionPos(file.getName()), file));
        }

        try (Stream<Path> fileStream = Files.list(Paths.get(args[0]))) {
            fileStream.parallel()
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().endsWith(".mca"))
                    .map(Path::toFile)
                    .filter(world2::containsValue)
                    .forEach(file -> {
                        try {
                            Region r1 = RegionIO.readRegion(file);
                            Region r2 = RegionIO.readRegion(world2.get(r1.getPosition()));

                            RegionPos rPos = r1.getPosition();

                            for (int x = 0; x < 32; ++x) {
                                for (int z = 0; z < 32; ++z) {
                                    ChunkPos cPos = new ChunkPos(rPos.getXPos() >> 5 + x, rPos.getZPos() >> 5 + z);
                                    Chunk c1 = r1.get(cPos);
                                    Chunk c2 = r2.get(cPos);
                                }
                            }


                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    });
        }
//
//
//        Region region = RegionIO.readRegion(new File(args[0]));
//        ChunkPos pos = new ChunkPos(-6304, -4858);
//        Chunk chunk = region.get(pos);
//
//        NbtList<NbtCompound> sections = chunk.getLevel().getCompoundList("Sections");
//
//        byte[] ar = new byte[4096];
//        Arrays.fill(ar, (byte)46); // fill array with tnt
////        for (int i = 0; i < 8; i++) {
////            ar[i] = (byte) 46;
////        }
//
//        for (int i = 0; i < sections.getSize(); ++i) {
//            NbtCompound section = sections.get(i);
//            section.set("Blocks", new NbtByteArray(ar)); //set all existing sections with our tnt array (empty sections don't exist)
////            byte[] blox = section.getByteArray("Blocks");
////            for (byte b : blox) {
////                System.out.println("sectionY= " + i + " byte=" + b);
////            }
//        }
//
//        region.put(pos, chunk);
//
//        RegionIO.writeRegion(new File(String.format("./out/r.%d.%d.mca", region.getPosition().getXPos(), region.getPosition().getZPos())), region);

    }
}