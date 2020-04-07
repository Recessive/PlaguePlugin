package plague;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.maps.*;
import mindustry.maps.filters.*;
import mindustry.maps.filters.GenerateFilter.*;
import mindustry.maps.generators.*;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.blocks.Floor;
import mindustry.world.blocks.StaticWall;
import mindustry.world.meta.BlockGroup;
import org.javatuples.Pair;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static mindustry.Vars.*;
import static mindustry.maps.filters.FilterOption.wallsOnly;


public class PlagueGenerator extends Generator{

    // elevation --->
    // temperature
    // |
    // v

    public static final int size = 601;
    int terrain_type;
    int map_type;

    Block[][] floors;
    Block[][] blocks;

    // Fix this generation later


    public PlagueGenerator() {
        super(size, size);
    }

    @Override
    public void generate(Tile[][] tiles) {

        GenerateInput in = new GenerateInput();

        tendrilFilter gapGen = new tendrilFilter();
        gapGen.block = Blocks.air;
        gapGen.floorGen = false;
        gapGen.falloff = (float) 0.2;
        gapGen.scl = (float) 40;
        gapGen.threshold = (float) 0.45;

        tendrilFilter mossGen = new tendrilFilter();
        mossGen.floor = Blocks.moss;
        mossGen.blockGen = false;

        int cx = size / 2;
        int cy = size / 2;
        double centreDist = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Block wall;
                Block floor = Blocks.darksand;
                centreDist = Math.sqrt(Math.pow(cx - x, 2) + Math.pow(cy - y, 2));
                if (centreDist < 100) {
                    wall = Blocks.air;
                }else {
                    wall = Blocks.duneRocks;
                }


                Block ore = Blocks.air;

                in.floor = floor;
                in.block = wall;
                in.ore = ore;
                in.x = x;
                in.y = y;
                in.width = in.height = size;

                if (centreDist >= 100) {
                    gapGen.apply(in);
                }
                if (centreDist >= 150){
                    mossGen.apply(in);
                }

                if(x == 0 || x == size-1 || y == 0 || y == size-1){
                    in.block = Blocks.duneRocks;
                }


                tiles[x][y] = new Tile(x, y, in.floor.id, in.ore.id, in.block.id);
            }
        }

        tiles[size/2][size/2].setNet(Blocks.coreFoundation, Team.crux, 0);
        tiles[size/2][size/2+10].setNet(Blocks.powerSource, Team.crux, 0);
        world.setMap(new Map(StringMap.of("name", "Patient Zero", "author", "Recessive")));
    }

    public static void defaultOres(Tile[][] tiles){
        GenerateInput in = new GenerateInput();
        Array<GenerateFilter> ores = new Array<>();
        maps.addDefaultOres(ores);
        ores.each(o -> ((OreFilter) o).threshold -= 0.05f);
        ores.insert(0, new OreFilter() {{
            ore = Blocks.oreScrap;
            scl += 2 / 2.1F;
        }});

        in.floor = (Floor) Blocks.darksand;
        in.block = Blocks.duneRocks ;
        in.width = in.height = size;

        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[x].length; y++) {
                in.ore = Blocks.air;
                in.x = x;
                in.y = y;

                for (GenerateFilter f : ores) {
                    f.apply(in);
                }
                tiles[x][y].setOverlay(in.ore);
            }
        }
    }

    private static void perimeterFlood(List<Tile> tileFlood, int[][] floodGrid, Tile[][] tiles){
        while(!tileFlood.isEmpty()){

            Tile t = tileFlood.remove(0);

            if (t.x+1 < tiles.length && floodGrid[t.x+1][t.y] == 0) {
                floodGrid[t.x+1][t.y] = 1;
                tileFlood.add(tiles[t.x+1][t.y]);
            }
            if (t.x-1 > 0 && floodGrid[t.x-1][t.y] == 0) {
                floodGrid[t.x-1][t.y] = 1;
                tileFlood.add(tiles[t.x-1][t.y]);
            }
            if (t.y+1 < tiles[0].length && floodGrid[t.x][t.y+1] == 0) {
                floodGrid[t.x][t.y+1] = 1;
                tileFlood.add(tiles[t.x][t.y+1]);
            }
            if (t.y-1 > 0 && floodGrid[t.x][t.y-1] == 0) {
                floodGrid[t.x][t.y-1] = 1;
                tileFlood.add(tiles[t.x][t.y-1]);
            }
        }
    }

    public static void inverseFloodFill(Tile[][] tiles){
        int[][] floodGrid = new int[size][size];
        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                if(tiles[x][y].block() instanceof StaticWall){
                    floodGrid[x][y] = 2;
                }
            }
        }
        List<Tile> tileFlood = new ArrayList<>();
        tileFlood.add(tiles[tiles.length/2][tiles[0].length/2]);
        perimeterFlood(tileFlood, floodGrid, tiles);

        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[0].length; y++) {
                if (floodGrid[x][y] == 0){
                    tiles[x][y].setBlock(Blocks.duneRocks);
                }
            }
        }
    }
}

class tendrilFilter extends GenerateFilter{
    public float scl = 40, threshold = 0.5f, octaves = 3f, falloff = 0.5f;
    public Block floor = Blocks.stone, block = Blocks.rocks;

    public boolean blockGen = true;
    public boolean floorGen = true;

    @Override
    public void apply(){
        float noise = noise(in.x, in.y, scl, 1f, octaves, falloff);

        if(noise > threshold){
            if(floorGen) in.floor = floor;
            if(blockGen) in.block = block;
        }
    }
}
