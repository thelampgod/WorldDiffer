package com.github.thelampgod;

import br.com.gamemods.nbtmanipulator.NbtInt;
import br.com.gamemods.regionmanipulator.Chunk;
import br.com.gamemods.regionmanipulator.Region;
import br.com.gamemods.regionmanipulator.RegionIO;

import java.io.File;
import java.io.IOException;

public class ChunkPosFixer {

    public static void main(String[] args) throws IOException {
//        if (args.length != 1) {
//            System.err.println("usage: <region.mca>");
//            System.exit(1);
//        }

        Region region = RegionIO.readRegion(new File("C:\\Users\\thela\\Desktop\\Java\\ChunkVersionChecker\\build\\dist\\16k Clean 2b2t no population\\region/r.-6.0.mca"));

        region.values().forEach(chunk -> {
            int posX = chunk.getPosition().getXPos() - 32;

            chunk.getLevel().set("xPos", new NbtInt(posX));
        });

        RegionIO.writeRegion(new File("./r.-7.0.mca"), region);
    }
}
