package hexed;

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
import mindustry.world.*;

import static mindustry.Vars.*;

public class HexedGenerator extends Generator{

    // elevation --->
    // temperature
    // |
    // v

    int terrain_type = (int)(Math.random() * 2);
    int map_type = (int)(Math.random() * 100);

    Block[][] floors = get_floors();
    Block[][] blocks = get_blocks();

    private Block[][] get_floors(){
        // I don't know java, I'm just winging it. Leave me alone ok, I don't know how to do this without using t1 as a decoy lol
        Block[][] t1 = {};

        if(terrain_type == 0){
            Block[][] t = {
                {Blocks.sand, Blocks.sand, Blocks.sand, Blocks.sand, Blocks.sand, Blocks.sand},
                {Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.sand, Blocks.sand},
                {Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.sand, Blocks.sand},
                {Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.sand, Blocks.sand},
                {Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.sand, Blocks.sand}
            };
            return t;
        }

        if(terrain_type == 1){
            Block[][] t = {
                {Blocks.sand, Blocks.sand, Blocks.sand, Blocks.sand, Blocks.sand, Blocks.grass},
                {Blocks.darksandWater, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.grass, Blocks.grass},
                {Blocks.darksandWater, Blocks.darksand, Blocks.darksand, Blocks.darksand, Blocks.grass, Blocks.shale},
                {Blocks.darksandTaintedWater, Blocks.darksandTaintedWater, Blocks.moss, Blocks.moss, Blocks.sporeMoss, Blocks.stone},
                {Blocks.ice, Blocks.iceSnow, Blocks.snow, Blocks.holostone, Blocks.hotrock, Blocks.salt}
            };
            return t;
        }
        return t1;
    }

    private Block[][] get_blocks(){
        Block[][] t1 = {};

        if(terrain_type == 0){
            Block[][] t = {
                {Blocks.rocks, Blocks.rocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks},
                {Blocks.rocks, Blocks.rocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks},
                {Blocks.rocks, Blocks.rocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks},
                {Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.rocks},
                {Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.rocks, Blocks.sandRocks}
            };
            return t;
        }

        if(terrain_type == 1){
            Block[][] t = {
                {Blocks.rocks, Blocks.rocks, Blocks.sandRocks, Blocks.sandRocks, Blocks.pine, Blocks.pine},
                {Blocks.rocks, Blocks.rocks, Blocks.duneRocks, Blocks.duneRocks, Blocks.pine, Blocks.pine},
                {Blocks.rocks, Blocks.rocks, Blocks.duneRocks, Blocks.duneRocks, Blocks.pine, Blocks.pine},
                {Blocks.sporerocks, Blocks.duneRocks, Blocks.sporerocks, Blocks.sporerocks, Blocks.sporerocks, Blocks.rocks},
                {Blocks.icerocks, Blocks.snowrocks, Blocks.snowrocks, Blocks.snowrocks, Blocks.rocks, Blocks.saltRocks}
            };
            return t;
        }
        return t1;
    }

            

    public HexedGenerator() {
        super(Hex.size, Hex.size);
    }

    @Override
    public void generate(Tile[][] tiles){
        
        Log.info("Terrain_type: " + terrain_type);
        Log.info("map_type: " + map_type);



        Simplex t = new Simplex(Mathf.random(0, 10000));
        Simplex e = new Simplex(Mathf.random(0, 10000));
        Array<GenerateFilter> ores = new Array<>();
        maps.addDefaultOres(ores);
        ores.each(o -> ((OreFilter)o).threshold -= 0.05f);
        ores.insert(0, new OreFilter(){{
            ore = Blocks.oreScrap;
            scl += 2 / 2.1F;
        }});
        GenerateInput in = new GenerateInput();
        IntArray hex = getHex();

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int temp = Mathf.clamp((int)((t.octaveNoise2D(12, 0.6, 1.0 / 400, x, y) - 0.5) * 10 * blocks.length), 0, blocks.length-1);
                int elev = Mathf.clamp((int)(((e.octaveNoise2D(12, 0.6, 1.0 / 700, x, y) - 0.5) * 10 + 0.15f) * blocks[0].length), 0, blocks[0].length-1);
                Block floor = floors[temp][elev];
                Block wall = blocks[temp][elev];
                Block ore = Blocks.air;

                for(GenerateFilter f : ores){
                    in.floor = Blocks.stone;
                    in.block = wall;
                    in.ore = ore;
                    in.x = x;
                    in.y = y;
                    in.width = in.height = Hex.size;
                    f.apply(in);
                    if(in.ore != Blocks.air){
                        ore = in.ore;
                    }
                }

                tiles[x][y] = new Tile(x, y, floor.id, ore.id, wall.id);
            }
        }
        if(map_type >= 45){
            for (int i = 0; i < width; i++){
                for (int j = 0; j < height; j++){
                    Tile tile = tiles[i][j];
                    tile.setBlock(Blocks.air);
                }
            }
        }
        for(int i = 0; i < hex.size; i++){
            int x = Pos.x(hex.get(i));
            int y = Pos.y(hex.get(i));

            Geometry.circle(x, y, width, height, Hex.diameter, (cx, cy) -> {
                if(map_type < 45){
                    // Check if point x, y is inside circle:
                    if(Math.sqrt(Math.pow(cx - x, 2) + Math.pow(cy - y, 2)) < Hex.radius - 4){
                        Tile tile = tiles[cx][cy];
                        tile.setBlock(Blocks.air);

                    }
                }   
                else if(map_type > 110){ // Disabling squares, so we only get empty map and circles

                    if(Math.abs(cx - x) < (Hex.radius - 9) && Math.abs(cy - y) < (Hex.radius - 4)){
                        Tile tile = tiles[cx][cy];
                        tile.setBlock(Blocks.air);
                    }

                    /*if(Intersector.isInsideHexagon(x, y, Hex.diameter, cx, cy)){
                        Tile tile = tiles[cx][cy];
                        tile.setBlock(Blocks.air);
                    }*/
                }
            });
            
            Angles.circle(3, 360f / 3 / 2f - 90, f -> {
                Tmp.v1.trnsExact(f, Hex.spacing + 12);
                if(Structs.inBounds(x + (int)Tmp.v1.x, y + (int)Tmp.v1.y, width, height)){
                    Tmp.v1.trnsExact(f, Hex.spacing / 2 + 7);
                    Bresenham2.line(x, y, x + (int)Tmp.v1.x, y + (int)Tmp.v1.y, (cx, cy) -> {
                        Geometry.circle(cx, cy, width, height, 3, (c2x, c2y) -> tiles[c2x][c2y].setBlock(Blocks.air));
                    });
                }
            });
        }

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];
                Block wall = tile.block();
                Block floor = tile.floor();
                // Removing the chance for boulders cuz they're annoying af
                /*if(wall == Blocks.air){
                    if(Mathf.chance(0.03)){
                        if(floor == Blocks.sand) wall = Blocks.sandBoulder;
                        else if(floor == Blocks.stone) wall = Blocks.rock;
                        else if(floor == Blocks.shale) wall = Blocks.shaleBoulder;
                        else if(floor == Blocks.darksand) wall = Blocks.rock;
                        else if(floor == Blocks.moss) wall = Blocks.sporeCluster;
                        else if(floor == Blocks.ice) wall = Blocks.snowrock;
                        else if(floor == Blocks.snow) wall = Blocks.snowrock;
                    }
                }*/
                tile.setBlock(wall);
            }
        }

        world.setMap(new Map(StringMap.of("name", "Hex++")));
    }

    public IntArray getHex(){
        IntArray array = new IntArray();
        double h = Math.sqrt(3) * Hex.spacing/2;
        //base horizontal spacing=1.5w
        //offset = 3/4w
        for(int x = 0; x < width / Hex.spacing - 2; x++){
            for(int y = 0; y < height / (h/2) - 2; y++){
                int cx = (int)(x * Hex.spacing*1.5 + (y%2)* Hex.spacing*3.0/4) + Hex.spacing/2;
                int cy = (int)(y * h / 2) + Hex.spacing/2;
                array.add(Pos.get(cx, cy));
            }
        }
        return array;
    }
}
