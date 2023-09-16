package com.github.thelampgod;

import br.com.gamemods.nbtmanipulator.*;
import br.com.gamemods.regionmanipulator.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class RemapWorld {

    public static void main(String... args) {
        if (args.length < 3) {
            System.err.println("usage: <worldfolder> <remapX> <remapZ>");
            System.err.println("coordinates are in region coordinates (block * 512)");
            System.exit(1);
        }
        //remap by
        final String inputFolder = args[0];
        final int remapX = Integer.parseInt(args[1]);
        final int remapZ = Integer.parseInt(args[2]);

        System.out.println(inputFolder);


        File entitiesFolder = new File(inputFolder + "/entities/");
        File regionFolder = new File(inputFolder + "/region/");


        File regionBackup = new File(regionFolder + "/backup/");
        if (!regionBackup.exists()) {
            regionBackup.mkdir();
        } else {
            System.err.println("'backup' folder already exists. delete to start remapping.");
            return;
        }
        File entitiesBackup = new File(entitiesFolder + "/backup/");
        if (!entitiesBackup.exists()) {
            entitiesBackup.mkdir();
        } else {
            System.err.println("'backup' folder already exists. delete to start remapping.");
            return;
        }


        Arrays.stream(regionFolder.listFiles())
                //.parallel()
                .filter(file -> file.getName().endsWith(".mca"))
                .forEach(file -> {
                    Region region;
                    try {
                        region = RegionIO.readRegion(file);
                    } catch (IOException e) {
                        System.err.println("couldnt read region");
                        return;
                    }

                    RegionPos pos = region.getPosition();
                    RegionPos newPos = new RegionPos(pos.getXPos() + remapX, pos.getZPos() + remapZ);

                    System.out.println("Remapping region " + pos + " to " + newPos);
                    region.getEntries().forEach(entry -> {
                        ChunkPos cPos = entry.getKey();
                        ChunkPos newCPos = new ChunkPos(cPos.getXPos() + (remapX * 32), cPos.getZPos() + (remapZ * 32));
                        Chunk chunk = entry.getValue();

                        System.out.println("Remapping chunk " + cPos + " to " + newCPos);
                        chunk.getCompound().set("xPos", newCPos.getXPos());
                        chunk.getCompound().set("zPos", newCPos.getZPos());

                        NbtList<NbtCompound> entities = chunk.getCompound().getCompoundList("block_entities");
                        entities.forEach(entity -> {
                            String id = entity.getString("id");
                            int x = entity.getInt("x");
                            int y = entity.getInt("y");
                            int z = entity.getInt("z");

                            int newX = x + (remapX * 512);
                            int newZ = z + (remapX * 512);
                            System.out.printf("Remapping %s(%d,%d,%d) to %d,%d,%d\n", id, x, y, z, newX, y, newZ);
                            entity.set("x", new NbtInt(newX));
                            entity.set("z", new NbtInt(newZ));
                        });

                    });

                    try {
                        Files.move(file.toPath(), regionBackup.toPath().resolve(file.getName()));
                        RegionIO.writeRegion(new File(String.format("%s/r.%d.%d.mca", regionFolder, newPos.getXPos(), newPos.getZPos())), region);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        Arrays.stream(entitiesFolder.listFiles())
                //.parallel()
                .filter(file -> file.getName().endsWith(".mca"))
                .forEach(file -> {
                    Region region;
                    try {
                        region = RegionIO.readRegion(file);
                    } catch (IOException e) {
                        System.err.println("couldnt read region");
                        return;
                    }

                    RegionPos pos = region.getPosition();
                    RegionPos newPos = new RegionPos(pos.getXPos() + remapX, pos.getZPos() + remapZ);

                    System.out.println("Remapping region " + pos + " to " + newPos);
                    region.getEntries().forEach(entry -> {
                        ChunkPos cPos = entry.getKey();
                        ChunkPos newCPos = new ChunkPos(cPos.getXPos() + (remapX * 32), cPos.getZPos() + (remapZ * 32));
                        Chunk chunk = entry.getValue();

                        System.out.println("Remapping chunk " + cPos + " to " + newCPos);
                        chunk.getCompound().set("Position", new NbtIntArray(new int[]{newCPos.getXPos(), newCPos.getZPos()}));

                        NbtList<NbtCompound> entities = chunk.getCompound().getCompoundList("Entities");
                        entities.forEach(entity -> {
                            String id = entity.getString("id");
                            NbtList<NbtDouble> positions = entity.getDoubleList("Pos");
                            double x = positions.get(0).getValue();
                            double y = positions.get(1).getValue();
                            double z = positions.get(2).getValue();

                            double newX = x + (remapX * 512);
                            double newZ = z + (remapX * 512);
                            System.out.printf("Remapping %s(%.1f,%.1f,%.1f) to %.1f,%.1f,%.1f\n", id, x, y, z, newX, y, newZ);
                            entity.set("Pos", new NbtList<>(new NbtDouble(newX), new NbtDouble(y), new NbtDouble(newZ)));
                        });

                    });

                    try {
                        Files.move(file.toPath(), entitiesBackup.toPath().resolve(file.getName()));
                        RegionIO.writeRegion(new File(String.format("%s/r.%d.%d.mca", entitiesFolder, newPos.getXPos(), newPos.getZPos())), region);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        System.out.println("Done! :)");
    }
}
