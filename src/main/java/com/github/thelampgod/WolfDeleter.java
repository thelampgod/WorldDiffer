package com.github.thelampgod;

import br.com.gamemods.nbtmanipulator.NbtCompound;
import br.com.gamemods.nbtmanipulator.NbtList;
import br.com.gamemods.regionmanipulator.RegionIO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class WolfDeleter {

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("usage: <region folder>");
            System.exit(1);
        }

        final File folder = new File(args[0]);

        Arrays.stream(folder.listFiles())
                .parallel()
                .filter(file -> file.getName().endsWith(".mca"))
                .map(file -> {
                    try {
                        return RegionIO.readRegion(file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .forEach(region -> {
                    region.values().forEach(chunk -> {
                        final NbtCompound level = chunk.getLevel();
                        NbtList<NbtCompound> entities = (NbtList<NbtCompound>) level.getList("Entities");

                        if (entities.isEmpty()) {
                            return;
                        }

                        Set<NbtCompound> compoundsToRemove = new HashSet<>();

                        entities.stream()
                                .filter(compound -> {
                                    try {
                                        compound.getString("OwnerUUID");
                                    } catch (NoSuchElementException e) {
                                        return false;
                                    }

                                    return !compound.getString("OwnerUUID").isEmpty();
                                })
                                .forEach(compound -> {
                                    String id = compound.getString("id");
                                    if (id.equals("minecraft:wolf")) {
                                        System.out.println("hit");
                                        compoundsToRemove.add(compound);
                                    }
                                });

                        if (!compoundsToRemove.isEmpty()) {
                            entities.removeAll(compoundsToRemove);
                            level.set("Entities", entities);
                        }

                    });
                    Path path = Paths.get(folder.getPath(), "censored");
                    try {
                        if (!path.toFile().exists()) {
                            Files.createDirectory(path);
                        }
                        RegionIO.writeRegion(Paths.get(path.toString(), String.format("r.%d.%d.mca", region.getPosition().getXPos(), region.getPosition().getZPos())).toFile(), region);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }
}
