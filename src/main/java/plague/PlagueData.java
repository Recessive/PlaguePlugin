package plague;

import arc.*;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.*;
import arc.util.ArcAnnotate.*;
import arc.util.*;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.Weapon;
import mindustry.world.*;
import mindustry.content.*;
import mindustry.world.blocks.defense.DeflectorWall;
import mindustry.world.blocks.units.UnitFactory;

import java.util.zip.DeflaterOutputStream;

import static mindustry.Vars.playerGroup;

public class PlagueData {


    public static BulletType getLLaser(){
        return new BasicBulletType(10f, 140*2, "bullet"){{
            bulletWidth = 12f;
            bulletHeight = 12f;
            lifetime = 20f;
        }};
    }

    public static Block newPhaseSmall(){
        return new customDeflector("phase-wall"){{
            requirements(Category.defense, ItemStack.with(Items.phasefabric, 6));
            health = 150 * 4;
        }};
    }

    public static Block newPhaseLarge(){
        return new customDeflector("phase-wall-large"){{
            requirements(Category.defense, ItemStack.with(Items.phasefabric, 24));
            health = 150 * 4 * 4;
            size = 2;
        }};
    }

    public static Weapon nerfedChaos(){
        return new Weapon("chaos"){{
            length = 8f;
            reload = 50f;
            width = 17f;
            alternate = true;
            recoil = 3f;
            shake = 2f;
            shots = 1;
            spacing = 4f;
            shotDelay = 5;
            ejectEffect = Fx.shellEjectMedium;
            bullet = Bullets.flakSurge;
            shootSound = Sounds.shootBig;
        }};
    }

}

class customDeflector extends DeflectorWall {
    public customDeflector(String name){
        super(name);
    }

    @Override
    public void handleBulletHit(TileEntity entity, Bullet bullet){
        super.handleBulletHit(entity, bullet);

        //doesn't reflect powerful bullets
        if(bullet.damage() > maxDamageDeflect || bullet.isDeflected()) return;

        // Only 50% chance to deflect
        if(Math.random() < 0.5) return;

        float penX = Math.abs(entity.x - bullet.x), penY = Math.abs(entity.y - bullet.y);



        bullet.hitbox(rect2);

        Vec2 position = Geometry.raycastRect(bullet.x - bullet.velocity().x*Time.delta(), bullet.y - bullet.velocity().y*Time.delta(), bullet.x + bullet.velocity().x*Time.delta(), bullet.y + bullet.velocity().y*Time.delta(),
                rect.setSize(size * 8 + rect2.width*2 + rect2.height*2).setCenter(entity.x, entity.y));

        if(position != null){
            bullet.set(position.x, position.y);
        }

        if(penX > penY){
            bullet.velocity().x *= -1;
        }else{
            bullet.velocity().y *= -1;
        }

        //bullet.updateVelocity();
        bullet.resetOwner(entity, entity.getTeam());
        bullet.scaleTime(1f);
        bullet.deflect();

        ((DeflectorEntity)entity).hit = 1f;
    }
}