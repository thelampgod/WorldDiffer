package com.github.thelampgod;

import br.com.gamemods.nbtmanipulator.NbtByteArray;
import br.com.gamemods.nbtmanipulator.NbtCompound;
import br.com.gamemods.nbtmanipulator.NbtList;
import br.com.gamemods.regionmanipulator.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class Main {

    private enum Mode {
        FROM,
        INTO,
        DESTRUCTION
    };

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: <world1> <world2> <from/into/destruction> <output>");
            System.exit(1);
        }

        String output = "./out/";

        if (args.length == 4) {
            output = args[3];
        }

        HashSet<String> existingRegions = new HashSet<>(Arrays.asList(Objects.requireNonNull(new File(output).list())));

        Mode mode = parseMode(args[2].toLowerCase());

        try (Stream<Path> fileStream = Files.list(Paths.get(args[0]))) {
            String finalOutput = output;
            fileStream.parallel()
                    .filter(file -> file.getFileName().toString().endsWith(".mca"))
                    .map(Path::toFile)
                    .filter(file -> !existingRegions.contains(file.getName()))
                    .forEach(file -> {
                        try {
                            Region r2;
                            try {
                                r2 = RegionIO.readRegion(new File(String.format("%s/%s", args[1], file.getName())));

                            } catch (FileNotFoundException e) {
                                System.out.println("region no exist");
                                return;
                            }
                            Region r1 = RegionIO.readRegion(file);
                            RegionPos rPos = r1.getPosition();

                            for (int x = 0; x < 32; ++x) {
                                for (int z = 0; z < 32; ++z) {
                                    ChunkPos cPos = new ChunkPos((rPos.getXPos() << 5) + x, (rPos.getZPos() << 5) + z);
                                    Chunk c1 = r1.get(cPos);
                                    Chunk c2 = r2.get(cPos);

                                    //if chunk doesnt exist in region
                                    if (c1 == null || c2 == null) {
                                        r1.remove(cPos);
                                        continue;
                                    }

                                    NbtList<NbtCompound> sList1 = c1.getLevel().getCompoundList("Sections");
                                    NbtList<NbtCompound> sList2 = c2.getLevel().getCompoundList("Sections");

                                    Set<Byte> sectionsToRemove = new HashSet<>();
                                    for (int i = 0; i < sList1.getSize(); ++i) {
                                        NbtCompound s1 = sList1.get(i);
                                        byte Y = s1.getByte("Y");

                                        Optional<NbtCompound> s2Optional = sList2.stream().filter(nbt -> nbt.getByte("Y") == Y).findAny();
                                        //if section doesnt exist in other world
                                        if (!s2Optional.isPresent()) {
                                            sectionsToRemove.add(Y);
                                            continue;
                                        }
                                        NbtCompound s2 = s2Optional.get();

                                        byte[] arr1 = s1.getByteArray("Blocks");
                                        byte[] arr2 = s2.getByteArray("Blocks");

                                        byte[] blockArray = compareArray(arr1, arr2, arr1.length, mode, (byte) 0);

                                        s1.set("Blocks", new NbtByteArray(blockArray));

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
                                        for (int l = 0; l < 3; ++l) {
                                            byte[] a1 = dataArrays1.get(l);
                                            byte[] a2 = dataArrays2.get(l);

                                            byte[] result = compareArray(a1, a2, a1.length, mode, (byte) 0);

                                            dataArrays1.set(l, result);
                                        }

                                        s1.set("BlockLight", new NbtByteArray(dataArrays1.get(0)));
                                        s1.set("Data", new NbtByteArray(dataArrays1.get(1)));
                                        s1.set("SkyLight", new NbtByteArray(dataArrays1.get(2)));
                                    }

                                    for (byte b : sectionsToRemove) {
                                        sList1.removeIf(nbt -> nbt.getByte("Y") == b);
                                    }

                                    //copy over new sections (if mode is "into")
                                    if (sList2.getSize() > sList1.getSize() && mode == Mode.INTO) {
                                        for (int i = 0; i < sList2.getSize(); ++i) {
                                            NbtCompound compound = sList2.get(i);
                                            Optional<NbtCompound> s1 = sList1.stream().filter(nbt -> nbt.getByte("Y") == compound.getByte("Y")).findAny();
                                            if (!s1.isPresent()) {
                                                sList1.add(compound);
                                            }
                                        }
                                    }

                                    byte[] biomes1 = c1.getLevel().getByteArray("Biomes");
                                    byte[] biomes2 = c2.getLevel().getByteArray("Biomes");

                                    byte[] biomeArray = compareArray(biomes1, biomes2, biomes1.length, mode, (byte) 127); //127 == void

                                    c1.getLevel().set("Biomes", new NbtByteArray(biomeArray));

                                    if (mode == Mode.INTO) {
                                        c1.getLevel().set("TileEntities", c2.getLevel().getCompoundList("TileEntities"));
                                        c1.getLevel().set("Entities", c2.getLevel().getCompoundList("Entities"));
                                    }

                                    r1.put(cPos, c1);
                                }
                            }

                            RegionIO.writeRegion(new File(String.format(finalOutput + "/r.%d.%d.mca", rPos.getXPos(), rPos.getZPos())), r1);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

        }
    }

    private static Mode parseMode(String arg) {
        Mode temp = Mode.FROM;

        if (arg.startsWith("into")) {
            temp = Mode.INTO;
        } else if (arg.startsWith("destruction")) {
            temp = Mode.DESTRUCTION;
        }

        return temp;
    }

    private static byte[] compareArray(byte[] arr1, byte[] arr2, int size, Mode mode, byte emptyByte) {
        //TODO: also other way round (only save blocks where nothing changed)
        byte[] temp = new byte[size];

        if (mode == Mode.DESTRUCTION) {
            for (int i = 0; i < size; ++i) {
                if (arr1[i] != arr2[i]) {
                    temp[i] = emptyByte;
                } else {
                    temp[i] = arr1[i];
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                if (arr1[i] == arr2[i]) {
                    temp[i] = emptyByte;
                } else {
                    temp[i] = (mode == Mode.FROM ? arr1[i] : arr2[i]);
                }
            }
        }
        return temp;
    }
}