package plague;

import mindustry.entities.type.Player;

public class CustomPlayer{
    public Player player;
    public int rank;
    public int playTime;
    public boolean infected = false;

    public CustomPlayer(Player player, int rank, int playTime){
        this.player = player;
        this.rank = rank;
        this.playTime = playTime;
    }

}
