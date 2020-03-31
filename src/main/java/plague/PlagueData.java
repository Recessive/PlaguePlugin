package plague;

import arc.*;
import arc.struct.*;
import arc.util.ArcAnnotate.*;
import arc.util.*;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.*;
import mindustry.content.*;
import mindustry.world.blocks.units.UnitFactory;

import static mindustry.Vars.playerGroup;

public class PlagueData {


    public static BulletType getLLaser(){
        return new BasicBulletType(10f, 140*2, "bullet"){{
            bulletWidth = 12f;
            bulletHeight = 12f;
            lifetime = 20f;
        }};
    }



}
