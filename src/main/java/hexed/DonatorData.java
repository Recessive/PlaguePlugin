package hexed;

import arc.Events;
import arc.graphics.Color;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;

import static java.lang.Math.abs;
import static mindustry.Vars.playerGroup;
import static mindustry.Vars.state;

public class DonatorData {

    public boolean doTrail = false;
    public boolean doBuildEffect = true;
    public int level = 1;
    public Player player;

    public DonatorData(int l, Player ply){
        level = l;
        player = ply;
    }
    void makeTrail(){
        if(doTrail && (abs(player.velocity().x) + abs(player.velocity().y)) > 1.2){
            // Flame one is cool
            Call.onEffectReliable(Fx.hitFlameSmall, player.x+(player.velocity().x * 3), player.y+(player.velocity().y * 3), (180 + player.rotation)%360, Color.orange);
        }
    }

    void makeBuildEffect(){
        if(doBuildEffect && player.isBuilding()){
            Call.onEffectReliable(Fx.bubble, player.x, player.y, 0, Color.orange);
        }
    }

    String toggleTrail(){
        if(level > 2){
            doTrail = !doTrail;
        }
        if(doTrail) {
            return "on";
        }else{
            return "off";
        }
    }

}
