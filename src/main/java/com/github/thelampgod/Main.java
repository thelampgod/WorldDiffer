package com.github.thelampgod;

import br.com.gamemods.nbtmanipulator.NbtByteArray;
import br.com.gamemods.nbtmanipulator.NbtCompound;
import br.com.gamemods.nbtmanipulator.NbtList;
import br.com.gamemods.regionmanipulator.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: <world1> <world2> <from/into> <output>");
            System.exit(1);
        }

        //TODO: dont use hashmap to not run out of memory
        HashMap<RegionPos, File> world2 = new HashMap<>();

        try (Stream<Path> fileStream1 = Files.list(Paths.get(args[1]))) {
            fileStream1
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".mca"))
                    .map(Path::toFile)
                    .forEach(file -> world2.put(new RegionPos(file.getName()), file));
        }

        try (Stream<Path> fileStream = Files.list(Paths.get(args[0]))) {
            fileStream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".mca"))
                    .map(Path::toFile)
                    .filter(file -> world2.containsKey(new RegionPos(file.getName())))
                    .forEach(file -> {
                        try {
                            System.out.println("reading regions");
                            Region r1 = RegionIO.readRegion(file);
                            Region r2 = RegionIO.readRegion(world2.get(r1.getPosition()));

                            RegionPos rPos = r1.getPosition();

                            for (int x = 0; x < 32; ++x) {
                                for (int z = 0; z < 32; ++z) {
                                    ChunkPos cPos = new ChunkPos((rPos.getXPos() << 5) + x, (rPos.getZPos() << 5) + z);
                                    System.out.println(rPos + " " + cPos);
                                    Chunk c1 = r1.get(cPos);
                                    Chunk c2 = r2.get(cPos);

                                    //if chunk doesnt exist in region
                                    if (c1 == null || c2 == null) {
                                        continue;
                                    }

                                    NbtList<NbtCompound> sList1 = c1.getLevel().getCompoundList("Sections");
                                    NbtList<NbtCompound> sList2 = c2.getLevel().getCompoundList("Sections");

                                    Set<Byte> sectionsToRemove = new HashSet<>();
                                    for (int i = 0; i < sList1.getSize(); ++i) {
                                        NbtCompound s1 = sList1.get(i);
                                        byte Y = s1.getByte("Y");

                                        //if section doesnt exist in other world
                                        if (sList2.get(Y) == null) {
                                            sectionsToRemove.add(Y);
                                            continue;
                                        }

                                        NbtCompound s2 = sList2.get(Y);

                                        byte[] arr1 = s1.getByteArray("Blocks");
                                        byte[] arr2 = s2.getByteArray("Blocks");



                                        for (int j = 0; j < arr1.length; ++j) {
                                            //TODO: also other way round (only save blocks where nothing changed)
                                            if (arr1[j] == arr2[j]) {
                                                arr1[j] = (byte) 0;
                                            } else {
                                                arr1[j] = (args[2].equalsIgnoreCase("from") ? arr1[j] : arr2[j]);
                                            }
                                        }

                                        s1.set("Blocks", new NbtByteArray(arr1));
                                        arr1 = null;

                                        List<byte[]> dataArrays1 = Arrays.asList(
                                                s1.getByteArray("BlockLight"),
                                                s1.getByteArray("Data"),
                                                s1.getByteArray("SkyLight")
                                        );

                                        List<byte[]> dataArrays2 = Arrays.asList(
                                                s2.getByteArray("BlockLight"),
                                                s2.getByteArray("Data"),
                                                s2.getByteArray("SkyLight")
                                        );

                                        for (int j = 0; j < 2048; ++j) {
                                            for (int l = 0;  l < 3; ++l) {
                                                byte[] a1 = dataArrays1.get(l);
                                                byte[] a2 = dataArrays2.get(l);
                                                if (a1[j] == a2[j]) {
                                                    a1[j] = (byte) 0;
                                                } else {
                                                    a1[j] = (args[2].equalsIgnoreCase("from") ? a1[j] : a2[j]);
                                                }

                                                dataArrays1.set(l, a1);
                                            }
                                        }

                                        s1.set("BlockLight", new NbtByteArray(dataArrays1.get(0)));
                                        s1.set("Data", new NbtByteArray(dataArrays1.get(0)));
                                        s1.set("SkyLight", new NbtByteArray(dataArrays1.get(0)));
                                    }

                                    for (byte b : sectionsToRemove) {
                                        sList1.removeIf(nbt -> nbt.getByte("Y") == b);
                                    }

                                    //copy over new sections (if mode is "from")
                                    //TODO: this seems to be somewhat broken, should check for the "Y" value instead of relying on the iteration order being correct
//                                    if (sList2.getSize() > sList1.getSize() && !args[2].equalsIgnoreCase("from")) {
//                                        for (int i = sList1.getSize() - 1; i < sList2.getSize(); ++i) {
//                                            sList1.add(sList2.get(i));
//                                        }
//                                    }

                                    System.out.println("saving chunk " + cPos + " in region");
                                    r1.put(cPos, c1);
                                }
                            }

                            String output = "./out/";

                            if (args.length == 4) {
                                output = args[3];
                            }

                            RegionIO.writeRegion(new File(String.format(output + "/r.%d.%d.mca", rPos.getXPos(), rPos.getZPos())), r1);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    });
        }
    }
}