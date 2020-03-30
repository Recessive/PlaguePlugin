package plague;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.maps.*;
import mindustry.maps.filters.*;
import mindustry.maps.filters.GenerateFilter.*;
import mindustry.maps.generators.*;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.blocks.Floor;
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

    public static final int size = 511;
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


        Array<GenerateFilter> ores = new Array<>();
        maps.addDefaultOres(ores);
        ores.each(o -> ((OreFilter) o).threshold -= 0.05f);
        ores.insert(0, new OreFilter() {{
            ore = Blocks.oreScrap;
            scl += 2 / 2.1F;
        }});

        GenerateInput in = new GenerateInput();

        tendrilFilter gapGen = new tendrilFilter();
        gapGen.block = Blocks.air;
        gapGen.floorGen = false;
        gapGen.falloff = (float) 0.2;

        tendrilFilter mossGen = new tendrilFilter();
        mossGen.floor = Blocks.moss;
        mossGen.blockGen = false;

        int cx = size / 2;
        int cy = size / 2;
        double centreDist = 0;

        int[][] floodGrid = new int[size][size];


        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Block wall;
                Block floor;
                centreDist = Math.sqrt(Math.pow(cx - x, 2) + Math.pow(cy - y, 2));
                if (centreDist < 100) {
                    floor = Blocks.darksand;
                    wall = Blocks.air;
                }else {
                    floor = Blocks.darksand;
                    wall = Blocks.duneRocks;
                }


                Block ore = Blocks.air;

                in.floor = floor;
                in.block = wall;
                in.ore = ore;
                in.x = x;
                in.y = y;
                in.width = in.height = size;

                for (GenerateFilter f : ores) {
                    f.apply(in);
                }
                if (centreDist >= 100) {
                    gapGen.apply(in);
                    mossGen.apply(in);
                }


                /*if(ore == Blocks.air){
                    floor = Blocks.sand;
                }*/
                if(x == 0 || x == size-1 || y == 0 || y == size-1){
                    in.block = Blocks.duneRocks;
                }
                if(in.block == Blocks.air){
                    floodGrid[x][y] = 0;
                }else{
                    floodGrid[x][y] = 2;
                }


                tiles[x][y] = new Tile(x, y, in.floor.id, in.ore.id, in.block.id);
            }
        }


        // Now flood the world and remove completely walled in areas

        List<Tile> tileFlood = new ArrayList<>();
        tileFlood.add(tiles[cx][cy]);

        perimeterFlood(tileFlood, floodGrid, tiles);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (floodGrid[x][y] == 1){
                    bridgeGap(tiles[x][y], floodGrid, tiles);
                }
            }
        }


        world.setMap(new Map(StringMap.of("name", "Plague")));
    }

    private boolean noWalls(Tile t, Tile[][] tiles){
        if(tiles[t.x+1][t.y].block() != Blocks.air) return false;
        if(tiles[t.x+1][t.y+1].block() != Blocks.air) return false;
        if(tiles[t.x+1][t.y-1].block() != Blocks.air) return false;
        if(tiles[t.x-1][t.y].block() != Blocks.air) return false;
        if(tiles[t.x-1][t.y+1].block() != Blocks.air) return false;
        if(tiles[t.x-1][t.y-1].block() != Blocks.air) return false;
        if(tiles[t.x][t.y+1].block() != Blocks.air) return false;
        if(tiles[t.x][t.y-1].block() != Blocks.air) return false;



        return true;
    }

    private void perimeterFlood(List<Tile> tileFlood, int[][] floodGrid, Tile[][] tiles){
        while(!tileFlood.isEmpty()){

            Tile t = tileFlood.remove(0);

            for(int xsign = -1; xsign < 2; xsign++){
                for(int ysign = -1; ysign < 2; ysign++){
                    if(xsign == 0 && ysign == 0){
                        continue;
                    }
                    /*if(t.x+xsign < 0 || t.x+xsign > size || t.y+ysign < 0 || t.y+ysign > size){
                        continue;
                    }*/
                    if (floodGrid[t.x+xsign][t.y+ysign] == 0) {
                        if(noWalls(tiles[t.x+xsign][t.y+ysign], tiles)){
                            floodGrid[t.x+xsign][t.y+ysign] = 3;
                        }else{
                            floodGrid[t.x+xsign][t.y+ysign] = 1;
                        }

                        tileFlood.add(tiles[t.x+xsign][t.y+ysign]);
                    }
                }
            }


            // t.setFloor((Floor) Blocks.salt);

        }
    }

    private void clearWalls(float x1, float y1, float x2, float y2, int thickness, Tile[][] tiles, int[][] floodGrid){
        float m = (y2-y1)/(x2-x1);
        float c = y2 - m*x2;
        int y;
        float smaller = Math.min(x1, x2);
        float larger = Math.max(x1, x2);
        for(float x = smaller; x < larger; x ++){
            y =(int) (m*x+c);
            for(int xdelta = -thickness; xdelta < thickness; xdelta++){
                for(int ydelta = -thickness; ydelta < thickness; ydelta++){
                    if(!(y + ydelta < 1 || y + ydelta > size-2 || (int) x + xdelta < 1 || (int) x + xdelta > size-2)){
                        tiles[(int) x + xdelta][y + ydelta].setBlock(Blocks.air);
                        // tiles[(int) x + xdelta][y + ydelta].setFloor((Floor) Blocks.metalFloor3);
                    }
                }
            }

        }
    }

    private Tile bridgeGap(Tile t, int[][] floodGrid, Tile[][] tiles){
        int range = 15;
        for(int x = -range; x < range; x ++){
            if(t.x + x < 0 || t.x + x >= size){
                continue;
            }
            for(int y = -range; y < range; y ++) {
                if(t.y + y < 0 || t.y + y >= size){
                    continue;
                }
                if(floodGrid[t.x+x][t.y+y] == 0){
                    clearWalls(t.x, t.y, t.x+x, t.y+y, 2, tiles, floodGrid);
                    return tiles[t.x+x][t.y+y];
                }
            }
        }
        return null;
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
