package plague;

import arc.graphics.Color;
import arc.struct.Array;
import mindustry.game.Team;

public class PlagueTeam extends Team {
    protected PlagueTeam(int id, String name, Color color) {
        super(id, name, color);
    }

    @Override
    public boolean isEnemy(Team other) {
        return false;
    }

    @Override
    public Array<Team> enemies() {
        return new Array();
    }
}
