package hexed;

import java.util.*;
import java.lang.reflect.Field;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import hexed.HexData.*;
import arc.math.geom.*;
import mindustry.entities.bullet.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.core.NetServer.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.net.Packets.*;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;
import mindustry.core.*;

import static arc.util.Log.info;
import static mindustry.Vars.*;

public class HexedMod extends Plugin{
	// TPS = 40
    //in seconds
    public static final float spawnDelay = 60 * 4;
    //health requirement needed to capture a hex; no longer used
    public static final float healthRequirement = 3500;
    //item requirement to captured a hex
    public static final int itemRequirement = 1000;

    public static final int messageTime = 1;
    //in ticks: 60 minutes: 60 * 60 * 60
    private int roundTime = 60 * 60 * 60;
    //in ticks: 2 minutes
    private final static int leaderboardTime = 60 * 60 * 2;

    private final static int announcementTime = 60 * 60 * 10;

    private final static int upgradeTime = 60 * 60 * 10;

    private final static int updateTime = 60 * 2;

    private final static int winCondition = 10;

    private final static int timerBoard = 0, timerUpdate = 1, timerWinCheck = 2, timerAnnounce = 3, timerUpgrade = 4;

    private final Rules rules = new Rules();
    private Interval interval = new Interval(5);

    private HexData data;
    private boolean restarting = false, registered = false;

    private Schematic[] starts;
    private Schematic start;
    private Schematic captureSchematic;

    private int upgradeLevel = 0;

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private double counter = 0f;
    private int lastMin;

    private String terrain_str = "";
    private String map_str = "";

  	private String[] announcements = {"Join the discord at: https://discord.gg/GEnYcSv"};
  	private int announcementIndex = 0;

    @Override
    public void init(){
    	loadouts.add(ItemStack.list(Items.copper, 1000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 200));
    	loadouts.add(ItemStack.list(Items.copper, 2000, Items.lead, 2000, Items.graphite, 400, Items.metaglass, 400, Items.silicon, 200, Items.titanium, 200));
    	loadouts.add(ItemStack.list(Items.copper, 4000, Items.lead, 4000, Items.graphite, 1000, Items.metaglass, 1000, Items.silicon, 500, Items.titanium, 400, Items.thorium, 200));
    	loadouts.add(ItemStack.list(Items.copper, 4000, Items.lead, 4000, Items.graphite, 1000, Items.metaglass, 1000, Items.silicon, 500, Items.titanium, 1000, Items.thorium, 1000));

        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.loadout = loadouts.get(0);
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 2;
        rules.blockHealthMultiplier = 1f;
        rules.unitBuildSpeedMultiplier = 1f;
        rules.playerDamageMultiplier = 0f;
        rules.enemyCoreBuildRadius = (Hex.diameter - 1) * tilesize / 2f;
        rules.unitDamageMultiplier = 1f;
        rules.playerHealthMultiplier = 0.5f;
        rules.canGameOver = false;
        rules.bannedBlocks.add(Blocks.hail);
        rules.bannedBlocks.add(Blocks.ripple);

        // Increase cost of lancer (No need with core placing on capture)
        Blocks.lancer.requirements = ItemStack.with(Items.copper, 25, Items.lead, 50, Items.silicon, 150);

        /*Array<Block> block_list = content.blocks();
        for(int i = 0; i < block_list.size; i++){
            Block temp = block_list.get(i);
            temp.insulated = true;
        }
        Log.info(Blocks.lancer.insulated);*/

        /*Array<Item> item_list = content.items();

        for(int i = 0; i < item_list.size; i++){
            Item temp = item_list.get(i);
            temp.explosiveness = 0;
            temp.flammability = 0;
        };*/
        
        // Log.info(Items.coal.explosiveness);

        Map<String, Integer> player_deaths = new HashMap<String, Integer>();
        int lives = 2;

        
        starts = new Schematic[]{Schematics.readBase64("bXNjaAB4nD2SUW7DIBBEd7ExBqcfOYg/epSeoEIOqiIREzl2qt6+rIGJJfNkdmd2R6GJPpj61T8C2ZiOWzr278+RpiU9n2Gbf32MdN3vu1/vx2Ne0voOf2mj6ytFv81Pv4Y4Z/oJdFnSFub1WGI4XtT5bSHzWvy+h43GY43J3zINj7DKSfRF7dfJi0EKVF5y01Or0aABZEAjRC1EHWgCXSpxrVbnI8D5W3NjuDHcGG5c3aTT1cG5KLctzjoFPQU9BT0FPYXpFVQ6nLp2CLfbHmdfNmGd6XTmIZNkwMpkOufrpK5l0ENFIzMNFV3yU/KtdWh0tEn6TKXDZGq+wzk1s9y23gG9BjuONXGhlp9BXctCUj73VEIaNIAMaKwqI1QsVCxULFQsVCxULFQsVBwScnVfIQUqf2DO1DwcPBw8HDwcPFyZsBNyoAlUJvgH0+orFw=="),
    							Schematics.readBase64("bXNjaAB4nE2S8Y6DIAzGC6goul32IP5xT3KPcOEct5g4NEx32dtfgdLMRPaza7+vFKCHDwGVt3cHenFPt3x/nuAyrdvmwvhnl2VcbLg56N9CcNnn3fr5uI/T6p/utQZQNkxwugZ73MZfO+1reEH/WLF43Kx3CwzBbXbGr3X2O+jHZPfdBRimNbjRH9Pijgdc3irIuLk7f8XE9vDLaiPpn1T6AoAvfEHERcZVQXmqtEp8GybN1HJeB0Wh59hAeiJ/CoWUlJVEKnqC9QTpxbykomLtOdWK5BepSpmRaqSs1yBV5CahJg/JHpI9JHnEvNSz0kiGOpa55+RWfGkWqkZSTMlNteilSU9xRUViHdS5a2kwlqKix1+T0uK/A1ecqfuaagek7BHJ0DRqzmsor0eqkr9BMqTXcJ6GcjCapqaR8p5i74Z2pGniFVLpSmcVJficI5XptrkHGUkzteTb0nQbJEOn1fKptjyrDsrT0f1TSHnOMVbuX8duHbt15Bbzsk6s7bm23L+O3QztXKZpZRXDeoa7N3SL/wGbh0NA"),
    							Schematics.readBase64("bXNjaAB4nFVSa26EIBAe8IGg22az5/BPL9IjNMSljY0rG1Zt9vYdYJi0JurHMN+DUejhVUC92psDtbjDLR9vJ7hs82bXeb+NP3ZZxsWGLwenf0U483Ly6+GePkBlwwSna7D71/hpp82HJ/QPj/Txble3wBDc3c648vO6gXpMdttcgIs/XLiG+XDjPfhvF5kwTD64cd2nxe0POP+RoTztza1XZHf7ungbEcA73iCgPCsoV52eEu+WkWLUcZ9mbs+1gWqC1hWipCwloqInWE+QXmQkFRnRC9UkJ5JZsWoQJT3RIkpJq9jXpJpEVDwke0j2kDmzUIhM2uwQpcwi7hbfMosGUU4fUXITGquK+wqjJoaBJqeueqzlfAO+TeqKuwMzCrfhWTWZISMyNI2G+1qecZv6hOwRGZpBy32K+jpEgs6r+BwqM0REeeI1ooFUFKuU7xxRmW6XM0iZtAvqKGlH020RGXLrsoeIu0VZQ7k0/3+a8sVa+f80u2l20+QW+zRNTdM5Ym1gveJm6GvGL170DOsZTm/oL/4FqklJyw=="),
    							Schematics.readBase64("bXNjaAB4nFWS23LjIAyGZccF40N6yO5j+KpP06sOY1O3O8R0qO1M334lkDVtLswXgf5fEkAPfwuoFnt1oL3bnX99PsPT+h7ix3Ydbtb7wds4O+h+xuDBxmuIbhrGsOzuO0S42+3mVzhf3TINnzH8c+OK4X6KdpuHN0v/vkF/jXZdXYRL2F2c4sfufhyut8UHO+G28nYZce3jOg+zW1y0dKAb0XRYttG77Quaz3BzcVjC5KAnvTcfbsNsVwcAL/DrV9LnRJ9KSCUq8FPLGSPUCLWJ6Fwnev0RgnuhR6FLSi3wT1Y+oU7DKoWolLxWSInLO1xTfYVCUpxRsgpRzqXdVAFQRq5AIz2ybMkV1Ohcp0yijsur2NckZzrXIOW5EOW5tLjmminWy252o9zDrZJ+c8U0U5UHkagUSmM/dUgVT1FhBxSjbrNvjatG0In6RLR7Tr6UcS8qDzwXxbMnehK6iO+ffAlJN3eupXPNEydS3KWWfrX0q6VfnZXheC0UMXKDRm7Q8DxqpHwLJ5xlI7GOazdyl4bdKqTDzbBbgZm10PGGWgChMm8iVUKKp9Hm3JKoY+VWXnErr7iVV9yKb8e59PqMUCPUsnLHyv8BxWxQSQ==")};
        captureSchematic = Schematics.readBase64("bXNjaAB4nD3OSw6AIAxF0VdacOIGXISLMoaZnwR1/UpFHxMOt0kDOnQC26Y1w+a95IB07OXMBb0/x+2al3wdAAb4uetRh1CBUsqoSKVP0rbUW/EOfFubhrrtm7r+pmzKZmzGFtkiW2JL7c/iCpRSRkXq3SIPb1NrXw==");
        start = starts[upgradeLevel];
        netServer.admins.addChatFilter((player, text) -> {
            for(String swear : CurseFilter.swears){
                text = text.replaceAll("(?i)" + swear, "");
            }

            return text;
        });

        Events.on(Trigger.update, () -> {
            if(active()){
                data.updateStats();

                for(Player player : playerGroup.all()){
                    if(player.getTeam() != Team.derelict && player.getTeam().cores().isEmpty()){
                        player.kill();
                        killTiles(player.getTeam());
                        Integer curr_deaths = player_deaths.get(player.uuid);
                        int lives_left = lives-curr_deaths;
                        Call.sendMessage("[yellow](!)[] [accent]" + player.name + "[lightgray] has been eliminated![accent] " +  lives_left + "/" + lives + " lives left [yellow] (!)");
                        Call.onInfoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                        player.setTeam(Team.derelict);
                        
                        
                        player_deaths.put(player.uuid, curr_deaths+1);
                    }

                    if(player.getTeam() == Team.derelict){
                        player.dead = true;
                    }else if(data.getControlled(player).size == data.hexes().size){
                        endGame();
                        break;
                    }
                }

                int minsToGo = (int)Math.ceil((roundTime - counter) / 60 / 60); // Changed /time so the time left is clearer
                if(minsToGo != lastMin){
                    lastMin = minsToGo;
                }

                if(interval.get(timerBoard, leaderboardTime)){
                    Call.onInfoToast(getLeaderboard(), 15f);
                }

                if(interval.get(timerUpdate, updateTime)){
                    data.updateControl();
                }

                if(interval.get(timerWinCheck, 60 * 2)){
                    Array<Player> players = data.getLeaderboard();
                    if(!players.isEmpty() && data.getControlled(players.first()).size >= winCondition && players.size > 1 && data.getControlled(players.get(1)).size <= 1){
                        endGame();
                    }
                }

                if(interval.get(timerAnnounce, announcementTime)){
                    Call.sendMessage(announcements[announcementIndex]);
                }

                if(interval.get(timerUpgrade, upgradeTime)){
                	if (upgradeLevel < starts.length-1){
                		Call.sendMessage("[scarlet]WARNING: [lightgray]Spawn loadout upgraded to level [yellow]" + String.valueOf(upgradeLevel+1));
                	}
                    upgradeLevel = Math.min(starts.length-1, upgradeLevel + 1);
                    start = starts[upgradeLevel];
                    state.rules.loadout = loadouts.get(upgradeLevel);
                }

                counter += Time.delta();

                //kick everyone and restart w/ the script
                if(counter > roundTime && !restarting){
                    endGame();
                }
            }else{
                counter = 0;
            }
        });

        Events.on(BlockDestroyEvent.class, event -> {
            //reset last spawn times so this hex becomes vacant for a while.
            if(event.tile.block() instanceof CoreBlock){
                //Log.info(event.tile.entity.sleeping);
                Hex hex = data.getHex(event.tile.pos());

                if(hex != null){
                    //update state
                    hex.spawnTime.reset();
                    hex.updateController();
                }
                Geometry.circle(hex.x, hex.y, 5, 5, Hex.diameter, (cx, cy) -> {
                	if(Math.abs(cx - hex.x) < (3) && Math.abs(cy - hex.y) < (3)){
	                    Tile tile = world.tile(cx, cy);
	                    tile.setBlock(Blocks.rocks);
                	} 
                });
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if(active() && event.player.getTeam() != Team.derelict){
                killTiles(event.player.getTeam());
            }
        });

        Events.on(PlayerConnect.class, event -> {
            for(String swear : BannedNames.badNames){
                if(event.player.name.toLowerCase().contains(swear)){
                	Log.info(swear.length());
                	event.player.name = event.player.name.replaceAll("(?i)" + swear, "");
                }
            }
        });

        Events.on(PlayerJoin.class, event -> {
            if(!active() || event.player.getTeam() == Team.derelict) return;

            Array<Hex> copy = data.hexes().copy();
            copy.shuffle();
            Hex hex = copy.find(h -> h.controller == null && h.spawnTime.get());

            if(!player_deaths.containsKey(event.player.uuid)){
                player_deaths.put(event.player.uuid, 0);
            }

            Integer curr_deaths = player_deaths.get(event.player.uuid);

            if(curr_deaths > lives){
                Call.onInfoMessage(event.player.con, "You have lost all your lives this round.\nAssigning into spectator mode.");
                event.player.kill();
                event.player.setTeam(Team.derelict);
            }
            else{
                if(hex != null){
                    loadout(event.player, hex.x, hex.y, start, true);
                    Core.app.post(() -> data.data(event.player).chosen = false);
                    hex.findController();
                }else{
                    Call.onInfoMessage(event.player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                    event.player.kill();
                    event.player.setTeam(Team.derelict);
                }
            }

            data.data(event.player).lastMessage.reset();

            event.player.sendMessage("[accent]Mutators\n\n[yellow]Terrain: [white]" + terrain_str + "\n[yellow]Map: [white]" + map_str + "\n");
        });

        Events.on(ProgressIncreaseEvent.class, event -> updateText(event.player));

        Events.on(HexCaptureEvent.class, event -> {
        	updateText(event.player);
        	loadout(event.player, event.hex.x, event.hex.y, captureSchematic, false);
        });

        Events.on(HexMoveEvent.class, event -> updateText(event.player));

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Array<Player> arr = Array.with(players);

            if(active()){
                //pick first inactive team
                for(Team team : Team.all()){
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.getTeam() == team) && !data.data(team).dying && !data.data(team).chosen){
                        data.data(team).chosen = true;
                        return team;
                    }
                }
                Call.onInfoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                return Team.derelict;
            }else{
                return prev.assign(player, players);
            }
        };
    }

    void updateText(Player player){
        HexTeam team = data.data(player);

        StringBuilder message = new StringBuilder("[white]Hex #" + team.location.id + "\n");

        if(!team.lastMessage.get()) return;

        if(team.location.controller == null){
            if(team.progressPercent > 0){
                message.append("[lightgray]Capture progress: [accent]").append((int)(team.progressPercent)).append("%");
            }else{
                message.append("[lightgray][[Empty]");
            }
        }else if(team.location.controller == player.getTeam()){
            message.append("[yellow][[Captured]");
        }else if(team.location != null && team.location.controller != null && data.getPlayer(team.location.controller) != null){
            message.append("[#").append(team.location.controller.color).append("]Captured by ").append(data.getPlayer(team.location.controller).name);
        }else{
            message.append("<Unknown>");
        }

        Call.setHudText(player.con, message.toString());
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("hexed", "Begin hosting with the Hexed gamemode.", args -> {
            if(!state.is(State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            data = new HexData();

            switch (data.terrain_type){
            	case 0:
            		terrain_str = "Desert";
            		break;
            	case 1:
            		terrain_str = "Mixed";
            		break;
            }

            if (data.map_type < 33){
            	map_str = "Cirlces";
            }
            else if (data.map_type < 66){
            	map_str = "No walls";
            }
            else {
            	map_str = "Boxed in";
            }

            logic.reset();
            Log.info("Generating map...");
            HexedGenerator generator = new HexedGenerator();
            generator.loadData(data);
            world.loadGenerator(generator);
            data.initHexes(generator.getHex());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
        });

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc{0} minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("end", "End the game.", args -> endGame());

        handler.register("r", "Restart the server.", args -> System.exit(2));
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        if(registered) return;
        registered = true;

        handler.<Player>register("spectate", "Enter spectator mode. This destroys your base.", (args, player) -> {
             if(player.getTeam() == Team.derelict){
                 player.sendMessage("[scarlet]You're already spectating.");
             }else{
                 killTiles(player.getTeam());
                 player.kill();
                 player.setTeam(Team.derelict);
             }
        });

        handler.<Player>register("captured", "Dispay the number of hexes you have captured.", (args, player) -> {
            if(player.getTeam() == Team.derelict){
                player.sendMessage("[scarlet]You're spectating.");
            }else{
                player.sendMessage("[lightgray]You've captured[accent] " + data.getControlled(player).size + "[] hexes.");
            }
        });

        handler.<Player>register("leaderboard", "Display the leaderboard", (args, player) -> {
            player.sendMessage(getLeaderboard());
        });

        handler.<Player>register("hexstatus", "Get hex status at your position.", (args, player) -> {
            Hex hex = data.data(player).location;
            if(hex != null){
                hex.updateController();
                StringBuilder builder = new StringBuilder();
                builder.append("| [lightgray]Hex #").append(hex.id).append("[]\n");
                builder.append("| [lightgray]Owner:[] ").append(hex.controller != null && data.getPlayer(hex.controller) != null ? data.getPlayer(hex.controller).name : "<none>").append("\n");
                for(TeamData data : state.teams.getActive()){
                    if(hex.getProgressPercent(data.team) > 0){
                        builder.append("|> [accent]").append(this.data.getPlayer(data.team).name).append("[lightgray]: ").append((int)hex.getProgressPercent(data.team)).append("% captured\n");
                    }
                }
                player.sendMessage(builder.toString());
            }else{
                player.sendMessage("[scarlet]No hex found.");
            }
        });

        handler.<Player>register("mutators", "Show current map mutators.", (args, player) -> {
        	player.sendMessage("[accent]Mutators\n\n[yellow]Terrain: [white]" + terrain_str + "\n[yellow]Map: [white]" + map_str + "\n");
        });

        handler.<Player>register("time", "Display the time left", (args, player) -> {
            player.sendMessage(String.valueOf("[scarlet]" + lastMin + "[lightgray] mins. remaining\n"));
        });
    }

    void endGame(){
        if(restarting) return;

        restarting = true;
        Array<Player> players = data.getLeaderboard();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < players.size && i < 3; i++){
            if(data.getControlled(players.get(i)).size > 1){
                builder.append("[yellow]").append(i + 1).append(".[accent] ").append(players.get(i).name)
                .append("[lightgray] (x").append(data.getControlled(players.get(i)).size).append(")[]\n");
            }
        }

        if(!players.isEmpty()){
            boolean dominated = data.getControlled(players.first()).size == data.hexes().size;

            for(Player player : playerGroup.all()){
                Call.onInfoMessage(player.con, "[accent]--ROUND OVER--\n\n[lightgray]"
                + (player == players.first() ? "[accent]You[] were" : "[yellow]" + players.first().name + "[lightgray] was") +
                " victorious, with [accent]" + data.getControlled(players.first()).size + "[lightgray] hexes conquered." + (dominated ? "" : "\n\nFinal scores:\n" + builder));
            }
        }

        Log.info("&ly--SERVER RESTARTING--");
        Time.runTask(60f * 10f, () -> {
            netServer.kickAll(KickReason.serverRestarting);
            Time.runTask(5f, () -> System.exit(2));
        });
    }

    String getLeaderboard(){
        StringBuilder builder = new StringBuilder();
        builder.append("[accent]Leaderboard\n[scarlet]").append(lastMin).append("[lightgray] mins. remaining\n\n");
        int count = 0;
        for(Player player : data.getLeaderboard()){
            builder.append("[yellow]").append(++count).append(".[white] ")
            .append(player.name).append("[orange] (").append(data.getControlled(player).size).append(" hexes)\n[white]");

            if(count > 4) break;
        }
        return builder.toString();
    }

    void killTiles(Team team){
        data.data(team).dying = true;
        Time.runTask(8f, () -> data.data(team).dying = false);
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.entity != null && tile.getTeam() == team){
                    Time.run(Mathf.random(60f * 6), tile.entity::kill);
                }
            }
        }

    }

    void loadout(Player player, int x, int y, Schematic schem, boolean addItem){
        // Set coretile to be an instance of HexTile
        Stile coreTile = schem.tiles.find(s -> s.block instanceof CoreBlock);
        if(coreTile == null) throw new IllegalArgumentException("Schematic has no core tile. Exiting.");
        int ox = x - coreTile.x, oy = y - coreTile.y;
        schem.tiles.each(st -> {
            Tile tile = world.tile(st.x + ox,st.y + oy);
            if(tile == null) return;

            if(tile.link().block() != Blocks.air){
                tile.link().removeNet();
            }

            tile.setNet(st.block, player.getTeam(), st.rotation);

            if(st.block.posConfig){
                tile.configureAny(Pos.get(tile.x - st.x + Pos.x(st.config), tile.y - st.y + Pos.y(st.config)));
            }else{
                tile.configureAny(st.config);
            }
            if(tile.block() instanceof CoreBlock && addItem){
                for(ItemStack stack : state.rules.loadout){
                    Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
                }
            }
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed") && !state.is(State.menu);
    }


}
