package plague;

import mindustry.entities.type.Player;

public class CustomPlayer{
    public Player player;
    public int rank;
    public int donateLevel;
    public int playTime;
    public boolean infected = false;

    public CustomPlayer(Player player, int rank, int donateLevel, int playTime){
        this.player = player;
        this.rank = rank;
        this.donateLevel = donateLevel;
        this.playTime = playTime;
    }

}
