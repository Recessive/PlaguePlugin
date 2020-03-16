package hexed;

import java.util.*;
import java.lang.reflect.Field;

import arc.*;
import arc.graphics.Color;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import hexed.HexData.*;
import arc.math.geom.*;
import jdk.nashorn.internal.ir.debug.JSONWriter;
import mindustry.entities.bullet.*;
import java.sql.Connection;
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
import org.json.simple.JSONObject;

import static arc.math.Mathf.floor;
import static arc.util.Log.info;
import static java.lang.Math.abs;
import static mindustry.Vars.*;
import static mindustry.Vars.player;

public class HexedMod extends Plugin{


	// TPS = 40
    //in seconds
    public static final float spawnDelay = 60 * 4;
    //health requirement needed to capture a hex; no longer used
    public static final float healthRequirement = 3500;
    //item requirement to captured a hex
    public static final int itemRequirement = 1500;

    public static final int messageTime = 1;

    //in ticks: 60 minutes: 60 * 60 * 60
    private int roundTime = 60 * 60 * 60;
    //in ticks: 2 minutes
    private final static int leaderboardTime = 60 * 60 * 2;

    private final static int announcementTime = 60 * 60 * 10;

    private final static int upgradeTime = 60 * 60 * 10;

    private final static int respawnCutoff = 60 * 60 * 10 + 60 * 30; // Offset by 30 seconds from upgrade time so the messages don't overlap

    private final static int updateTime = 60 * 2;

    private final static int winCondition = 10;
    private final static int maxHex = 15;

    private final static int timerBoard = 0, timerUpdate = 1, timerWinCheck = 2, timerAnnounce = 3, timerUpgrade = 4;

    private final Rules rules = new Rules();
    private Interval interval = new Interval(5);

    private HexData data;
    private boolean restarting = false, registered = false;

    private Schematic[] starts;
    private Schematic start;
    private Schematic captureSchematic;

    private int upgradeLevel = 0;

    private boolean sendRespawnMessage = true;

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private double counter = 0f;
    private int lastMin;

    private String terrain_str = "";
    private String map_str = "";

  	private String[] announcements = {"Join the discord at: [purple]https://discord.gg/GEnYcSv", "Rank up to earn [darkgray]common[white] trails or donate to get [purple]epic[white] ones!"};
  	private int announcementIndex = 0;

  	private PlayerData ply_db = new PlayerData();
  	private CosmeticData cos_db = new CosmeticData();

  	private int lives = 0;
    private Map<String, Integer> player_deaths = new HashMap<String, Integer>();
    
    private Map<String, Boolean> core_count = new HashMap<String, Boolean>();


    @Override
    public void init(){

        ply_db.connect("data/server_data.db");
        cos_db.connect(ply_db.conn);


    	loadouts.add(ItemStack.list(Items.copper, 1000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 200));
    	loadouts.add(ItemStack.list(Items.copper, 2000, Items.lead, 2000, Items.graphite, 400, Items.metaglass, 400, Items.silicon, 200, Items.titanium, 200));
    	loadouts.add(ItemStack.list(Items.copper, 4000, Items.lead, 4000, Items.graphite, 1000, Items.metaglass, 250, Items.silicon, 500, Items.titanium, 400, Items.thorium, 200));
    	loadouts.add(ItemStack.list(Items.copper, 4000, Items.lead, 4000, Items.graphite, 1000, Items.metaglass, 500, Items.silicon, 500, Items.titanium, 1000, Items.thorium, 1000));

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
        Blocks.lancer.requirements = ItemStack.with(Items.copper, 25, Items.lead, 50, Items.silicon, 250);

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


        
        starts = new Schematic[]{Schematics.readBase64("bXNjaAB4nD2SUW7DIBBEd7ExBqcfOYg/epSeoEIOqiIREzl2qt6+rIGJJfNkdmd2R6GJPpj61T8C2ZiOWzr278+RpiU9n2Gbf32MdN3vu1/vx2Ne0voOf2mj6ytFv81Pv4Y4Z/oJdFnSFub1WGI4XtT5bSHzWvy+h43GY43J3zINj7DKSfRF7dfJi0EKVF5y01Or0aABZEAjRC1EHWgCXSpxrVbnI8D5W3NjuDHcGG5c3aTT1cG5KLctzjoFPQU9BT0FPYXpFVQ6nLp2CLfbHmdfNmGd6XTmIZNkwMpkOufrpK5l0ENFIzMNFV3yU/KtdWh0tEn6TKXDZGq+wzk1s9y23gG9BjuONXGhlp9BXctCUj73VEIaNIAMaKwqI1QsVCxULFQsVCxULFQsVBwScnVfIQUqf2DO1DwcPBw8HDwcPFyZsBNyoAlUJvgH0+orFw=="),
    							Schematics.readBase64("bXNjaAB4nFWSbW6DMAyGTfgINFBoew5+7BA7x5RBVnUKoUqhU0+/OcFYGojoIc7rvHYCDVwSyJyeDEhrnsZ+vNXQLLdFu9s69T/aWrj8++2t9lcDnfbT7M3YD7N7mtfsIX/q1S4gH4NeFuPhOBk39nc/f5thwfjpMaO0v2tn9iSF1W7ApeXq7KxHpGb0er32XzpIXlAPuEfv1sGa9QHyM2Z+AcA77I9IcAgfpGFMmfJIAge5LcSvZKo4wQF2mWJtzXRk6njdmZQJOUgxY8ycZDh3oFBC65K4H0RHgrjARDlTE0lirI2CoDiRJiV/JZL6xQejGcUqJEE1FFRRmNuzZJSlwDdnakhd7Bsgbf1TSIIpZWqZOlacmM7UIUndqGIlwUGglnog2UvFXirykuynISTSVlGJJEO9sabY3TT0YutujnMta7fMGcYk9V7xCSm+G4rvhqK7EahmOjLtVSo+6RqASVK0JlcCKfoXgQ5M0YMI67Ysf8bqSfk="),
    							Schematics.readBase64("bXNjaAB4nFWS33KsIAzGo2tBRbd/9pzH8KZv06sOo9T2DCsdqu707U8CMdN6AT8D+UI+gB7+FlAt9upAe7c7//p8hqf1PcSP7TrcrPeDt3F20P2MwYON1xDdNIxh2d13iHC3282vcL66ZRo+Y/jnxhXD/RTtNg9vlv6+QX+Ndl1dhEvYXZzix+5+bK63xQc74bLydhlx7uM6D7NbXLS0oRux6LBso3fbF7Sf4ebisITJQU96bz7chtmuDgBe4NdX0nCioRJSiQocatnTCLVCJhHt60SvP0JwL/QodEmpBf5k5RPqtKxSiErJc4WUuLzDOZ2vUEiKM0pWIcq5tJpOAJSRT6CRHlm25BPUWJlyixNRl4gdSL1WXLdFyr4QZV8MzvnMFOtlNVej3KNaJf3mE5OnKhuRqBRKtp86pIpdVNgBxajbXLfGWSPoRH0iWj2nupRxLyoP7Iti74mehC5S90++hKSbO9fSuWbHiRR3qaVfLf1q6VdnZTheC0UaucFGbrBhP2qkdAtofsueUizfB+077rLhahXSUa3hagVm1kLHGzIAQmVeRKqEFLthcm5J1LGykVds5BUbecVG6nacS6+vEWqFDCt3rPwf0vpQUg=="),
    							Schematics.readBase64("bXNjaAB4nE1W7ZLaMAx0nC8CScgn3D1E/rQP1MmAj14nJDc54OYet29Sy5a2wEyy2NKuJMs26lW9Biqax6tR2WQeZvr189ePUjW338v6fr8OX+M0DdO4XozKn8dUMo3zyayqGtfrsprzcFrmh/leVlWc1/F+Gd7G021Zv1X9uVj/4WOcjTCVVzOfh491+WPIRhXr7TJczGzWkX62y8Os5/X9YZ5sth/Ll1mHeTkbtbnP0zKerXp+strDfD9N5v6p0s/TeLvZ4YIY3qbla7iMN6OU+qvkEwT2ofEzxCMGSoEyoNL50qMCaoA6MB/AfPRy9quBZJb1Nc1p1tBeKCDkYgloLmVz7WMJQot2HLlWOVAB5hK+FXwbaLhIQ9L9H4tEGnJcof3ugHKgAnbiGyGPCFlGPg836+YDeieOJLaZSR4RNCJoRKxBdnv41hx9pFpo9EAHzjLiPMhb8oihEUMjRq1iZlE+OstCcUZcoYRjJuQ7IrRI+BLwJahL4iPVhBrwtRx9wvFFlk0zc8oapOA0AnpnrJZCLYVaympk51Y63Fjke5L4RDf1udlu2nBuVPmYa78B8wbMG9Q+8zE7Xx8zdZ9m3YyrkanMR6q39r2lORtzBuYMzBkzk13p7MjTra8mPhd9SBo9r+WWY946H/LYWbacWbbONw0J+/hyq+prSigCSoBcFcKtRXtXAupwX7XCoprrsuM9TWMtxjqM9VznHXddwDHRWub+KAgpzj1QDdQC9fA4Ar1wl0hvBha5jHRhUcgdUXBuNBZjLMFYijHJt3D1CxyzrGXJa0l7zHcE9VLKHiVXnJCvM836NSIPXxdikdXac8y0ptIRe3TE3neEJpQDFbCTjthzRxCf7JSKV3VjkeyPCvujAnMF5oqZyc7vj8Qiv9LEJ7ui5pjpdJEdX3MlQ4uEuQZzzcxkJ7usxk6pcfo0vG6BRXKCNFzd2NrL+ddAo4FGwxpk588/8q3B13CFGtS+VfJpfR6aTknJo4VGC42WNciu5uhb8LS4DTrk0fn+s74d+DrwdahLh1O5ww0hJ2xgkeaYe9x0PW66Hjddjwr1UOuh1rMa2ZXwrTAmN12Pm65/ikVyO3Bf0U0mGgdoHJDRAb6yuoFFGmOSx5HzIJQCZbArMVYBNUDy7+H4pCaRvuBefeHZf3lEZ70=")};
        captureSchematic = Schematics.readBase64("bXNjaAB4nD3OSw6AIAxF0VdacOIGXISLMoaZnwR1/UpFHxMOt0kDOnQC26Y1w+a95IB07OXMBb0/x+2al3wdAAb4uetRh1CBUsqoSKVP0rbUW/EOfFubhrrtm7r+pmzKZmzGFtkiW2JL7c/iCpRSRkXq3SIPb1NrXw==");
        start = starts[upgradeLevel];
        netServer.admins.addChatFilter((player, text) -> {
            for(String swear : CurseFilter.swears){
                text = text.replaceAll("(?i)" + swear, "");
            }

            return text;
        });

        Events.on(Trigger.update, // Do trail:
                // Changed /time so the time left is clearer
                //kick everyone and restart w/ the script
                this::run);

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
                if(counter > respawnCutoff){
                    Integer curr_deaths = player_deaths.get(event.player.uuid);
                    player_deaths.put(event.player.uuid, curr_deaths+1);
                }
                killTiles(event.player.getTeam(), event.player);
            }
        });

        Events.on(PlayerConnect.class, event -> {
            for(String swear : BannedNames.badNames){
                if(event.player.name.toLowerCase().contains(swear)){
                	event.player.name = event.player.name.replaceAll("(?i)" + swear, "");
                }
            }
        });

        Events.on(PlayerJoin.class, event -> {


            cos_db.loadPlayer(event.player.uuid);


            // Determine player name color
            String prefix = (String) cos_db.entries.get(event.player.uuid).get("color");
            String no_color = filterColor(event.player.name, prefix);

            int rank_num = ply_db.getHexesCaptured(event.player.uuid)/25 % 4 + 1;

            int capped = ply_db.getHexesCaptured(event.player.uuid);

            List<String> curr_trails = cos_db.getTrails(event.player.uuid);
            if (capped < 100){
                event.player.name = "[accent]<[#cd7f32]Bronze " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 100 && capped < 200){
                event.player.name = "[accent]<[#C0C0C0]Silver " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 200 && capped < 300){
                event.player.name = "[accent]<[gold]Gold " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 300 && capped < 400){
                event.player.name = "[accent]<[#697998]Platinum " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 400 && capped < 500){
                event.player.name = "[accent]<[#00ccff]Diamond " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 500 && capped < 600){
                event.player.name = "[accent]<[#ff5050]Master " + rank_num + "[accent]>[white] " + no_color;
            }
            if (capped >= 600){
                event.player.name = "[accent]<[#660066]Grand Master[accent]>[white] " + no_color;
            }
            ply_db.setName(event.player.uuid, event.player.name);

            if (capped >= 100){
                if(!curr_trails.contains("16")){
                    cos_db.addTrail(event.player.uuid, "16");
                }
            }
            if (capped >= 200){
                if(!curr_trails.contains("11")){
                    cos_db.addTrail(event.player.uuid, "11");
                }
            }

            if (capped >= 300){
                if(!curr_trails.contains("7")){
                    cos_db.addTrail(event.player.uuid, "7");
                }
            }

            if (capped >= 400){
                if(!curr_trails.contains("13")){
                    cos_db.addTrail(event.player.uuid, "13");
                }
            }

            if (capped >= 500){
                if(!curr_trails.contains("27")){
                    cos_db.addTrail(event.player.uuid, "27");
                }
            }

            if (capped >= 500){
                if(!curr_trails.contains("107")){
                    cos_db.addTrail(event.player.uuid, "107");
                }
            }



            if (ply_db.getPoints(event.player.uuid)[0] == 0){
                ply_db.addPoints(event.player.uuid, ply_db.getWins(event.player.uuid)[0]*5);
            }

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

            event.player.sendMessage(getRankBoard());

            // event.player.sendMessage("[accent]Mutators\n\n[yellow]Terrain: [white]" + terrain_str + "\n[yellow]Map: [white]" + map_str + "\n");

            if (curr_deaths <= lives) {
                if (lives - curr_deaths == 1) {
                    event.player.sendMessage("[accent]You have [scarlet]" + (lives - curr_deaths) + " [accent]life left");
                }else{
                    event.player.sendMessage("[accent]You have [scarlet]" + (lives - curr_deaths) + " [accent]lives left");
                }
            }
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

            // Can only add Fx after the map has been made so they are properly initialised
            cos_db.initMappings();
        });

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc{0} minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("end", "End the game.", args -> endGame());

        handler.register("r", "Restart the server.", args -> System.exit(2));

        handler.register("setcolor", "<uuid> <color>", "Set the color of a players name based on uuid", args -> {
            cos_db.setCol(args[0], args[1]);
            Log.info("Set uuid " + args[0] + " prefix to " + args[1]);
        });

        handler.register("getdonator", "<uuid>", "Set the color of a players name based on uuid", args -> {
            Log.info("uuid " + args[0] + " donator level is " + ply_db.getDonatorLevel(args[0]));
        });

        handler.register("setdonator", "<uuid> <level>", "Set the color of a players name based on uuid", args -> {
            int level = Integer.parseInt(args[1]);
            if (level < 0 || level > 3){
                Log.info("Donator level must be between 0 and 3");
            }
            setDonator(args[0], level);
            Log.info("Set uuid " + args[0] + " donator level to " + args[1]);
        });

        handler.register("setxp", "<uuid> <xp>", "Set a players xp/hexes captured", args -> {
            int xp = Integer.parseInt(args[1]);
            ply_db.setXp(args[0], xp);
            Log.info("Set uuid " + args[0] + " xp to " + xp);
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        if(registered) return;
        registered = true;

        handler.<Player>register("spectate", "Enter spectator mode. This destroys your base.", (args, player) -> {
             if(player.getTeam() == Team.derelict){
                 player.sendMessage("[scarlet]You're already spectating.");
             }else{
                 killTiles(player.getTeam(), player);
                 player.kill();
                 player.setTeam(Team.derelict);
             }
        });

        handler.<Player>register("spec", "Alias for spectate", (args, player) -> {
            if(player.getTeam() == Team.derelict){
                player.sendMessage("[scarlet]You're already spectating.");
            }else{
                killTiles(player.getTeam(), player);
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

        handler.<Player>register("scoreboard", "Display the global scoreboard", (args, player) -> {
            player.sendMessage(getRankBoard());
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

        handler.<Player>register("wins", "Get total wins", (args, player) -> {
            player.sendMessage("[accent]You've won [scarlet]" + ply_db.getWins(player.uuid)[1] + "[accent] games");
        });

        handler.<Player>register("points", "Get total points", (args, player) -> {
            player.sendMessage("[accent]You have [scarlet]" + ply_db.getPoints(player.uuid)[1] + "[accent] points");
        });

        handler.<Player>register("hextotal", "Get the total hexes captured across all games", (args, player) -> {
            player.sendMessage("[accent]You've captured [scarlet]" + ply_db.getHexesCaptured(player.uuid) + "[accent] hexes across all games");
        });

        handler.<Player>register("xp", "Get your experience", (args, player) -> {
            player.sendMessage("[accent]You have [scarlet]" + ply_db.getHexesCaptured(player.uuid) + "[accent] xp points");
        });

        handler.<Player>register("trail", "Toggle trail on/off", (args, player) -> {
            List<String> trails = cos_db.getTrails(player.uuid);
            if(trails.size() < 1 || (trails.get(0).length() < 1 && trails.size() == 1)){
                player.sendMessage("[accent]You have no trails! Get [gray]common[accent] trails by ranking up or donate to get [purple]epic[accent] ones!");
                return;
            }

            cos_db.toggleTrail(player.uuid);
            player.sendMessage("[accent]Trail is now [scarlet]" + (cos_db.getTrailToggle(player.uuid) ? "on" : "off"));
        });

        handler.<Player>register("trailset", "<number>", "Change your trail", (args, player) -> {
            int trail = Integer.parseInt(args[0]) + 1;
            if(player.isAdmin){
                if (trail < 0 || trail > cos_db.trailList.size() - 2) {
                    player.sendMessage("[accent]Invalid trail. Choose a number between [scarlet]0 and [scarlet]" + (cos_db.trailList.size() - 1));
                    return;
                }
                cos_db.setTrail(player.uuid, trail);
                player.sendMessage("[accent]Set trail to [scarlet]" + trail);
            }else{


                List<String> trails = cos_db.getTrails(player.uuid);
                if(trails.size() < 1 || (trails.get(0).length() < 1 && trails.size() == 1)){
                    player.sendMessage("[accent]You have no trails! Get [gray]common[accent] trails by ranking up or donate to get [purple]epic[accent] ones!");
                    return;
                }
                if (trail < 2 || trail > trails.size()) {
                    player.sendMessage("[accent]Invalid trail. Choose a number between [scarlet]1 and [scarlet]" + (trails.size()-1));
                    return;
                }
                cos_db.setTrail(player.uuid, Integer.parseInt(trails.get(trail- 1)));
                player.sendMessage("[accent]Set trail to [scarlet]" + (trail - 1));
            }

        });
    }

    void setDonator(String uuid, int level){
        switch(level){
            case 0:
                cos_db.setCol(uuid, "[white]");
                break;
            case 1:
                cos_db.setCol(uuid, "[sky]");
                break;
            case 2:
                cos_db.setCol(uuid, "[forest]");
                break;
            case 3:
                cos_db.setCol(uuid, "[red]");
                break;

        }

        ply_db.setDonatorLevel(uuid, level);
    }

    void endGame(){
        if(restarting) return;

        restarting = true;

        for(Player player : playerGroup.all()){
            ply_db.addGame(player.uuid);
        }

        Array<Player> players = data.getLeaderboard();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < players.size && i < 3; i++){
            if(data.getControlled(players.get(i)).size > 1){ // Num hexes > 1
                builder.append("[yellow]").append(i + 1).append(".[accent] ").append(players.get(i).name)
                .append("[lightgray] (x").append(data.getControlled(players.get(i)).size).append(")[]\n");
            }
        }

        if(!players.isEmpty()){
            boolean dominated = data.getControlled(players.first()).size == data.hexes().size;

            ply_db.addWin(players.first().uuid);

            // Add 5 points for first, 4 for second, 3 for third
            int count = 0;
            for(Player player : data.getLeaderboard()){

                player.sendMessage("[accent]Your points increased from [scarlet]" + ply_db.getPoints(player.uuid)[0] + "[accent] to [scarlet]" + (ply_db.getPoints(player.uuid)[0] + 5 - count)  + "[accent] for placing " + ordinal(count+1));
                ply_db.addPoints(player.uuid, 5 - count);
                count ++;
                if(count > 4) break;
            }

            for(Player player : playerGroup.all()){
                if(data.getControlled(player).size > 1){
                    player.sendMessage("You gained " + (data.getControlled(player).size - 1) + " experience towards your rank! Check your xp with /xp");
                    ply_db.addHexCaptures(player.uuid, data.getControlled(player).size - 1);
                }
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

    public static String ordinal(int i) {
        String[] sufixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
        switch (i % 100) {
            case 11:
            case 12:
            case 13:
                return i + "th";
            default:
                return i + sufixes[i % 10];
        }
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

    String getRankBoard(){
        StringBuilder builder = new StringBuilder();
        builder.append("[accent]Global scoreboard\n\n");
        ArrayList<ArrayList<String>> top5 = ply_db.getTopPoints("monthPoints", 5);
        for(int i = 0; i <= 5; i ++){
            if (i == top5.get(0).size()){
                break;
            }
            builder.append("[yellow]").append(i+1).append(".[white] ")
                    .append(top5.get(1).get(i)).append("[orange] ").append(top5.get(2).get(i)).append(" [accent]points\n[white]");
        }
        return builder.toString();
    }


    void killTiles(Team team, Player player){
        if(data.getControlled(player).size > 1 && !restarting && counter > respawnCutoff) {
            player.sendMessage("[accent]You gained [scarlet]" + (data.getControlled(player).size - 1) + " [accent]experience towards your rank!");
            ply_db.addHexCaptures(player.uuid, data.getControlled(player).size - 1);}
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
        cos_db.savePlayer(player.uuid);
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

    public String filterColor(String s, String prefix){
        return prefix + Strings.stripColors(s);
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed") && !state.is(State.menu);
    }


    private void run() {
        if (active()) {

            data.updateStats();

            for (Player player : playerGroup.all()) {
                if (player.getTeam() != Team.derelict && player.getTeam().cores().isEmpty()) {
                    player.kill();
                    killTiles(player.getTeam(), player);
                    Integer curr_deaths = player_deaths.get(player.uuid);
                    int lives_left = lives - curr_deaths;
                    Call.sendMessage("[yellow](!)[] [accent]" + player.name + "[lightgray] has been eliminated![accent] " + lives_left + "/" + lives + " lives left [yellow] (!)");
                    Call.onInfoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                    player.setTeam(Team.derelict);
                }

                if (player.getTeam() == Team.derelict) {
                    player.dead = true;
                } else if (data.getControlled(player).size == data.hexes().size) {
                    endGame();
                    break;
                }

                // Do trail:
                try {
                    if ((abs(player.velocity().x) + abs(player.velocity().y)) > 1.2 && cos_db.getTrailToggle(player.uuid)) {
                        Call.onEffectReliable(cos_db.trailList.get(cos_db.getTrail(player.uuid)), player.x + (player.velocity().x), player.y + (player.velocity().y), (180 + player.rotation) % 360, Color.white);
                    }
                }catch (NullPointerException ignore){
                }
            }
            int minsToGo = (int) Math.ceil((roundTime - counter) / 60 / 60); // Changed /time so the time left is clearer
            if (minsToGo != lastMin) {
                lastMin = minsToGo;
            }

            if (counter > respawnCutoff && sendRespawnMessage) {
                Call.sendMessage("[accent]You will now lose a life if you disconnect");
                sendRespawnMessage = false;
            }

            if (interval.get(timerBoard, leaderboardTime)) {
                Call.onInfoToast(getLeaderboard(), 15f);
            }

            if (interval.get(timerUpdate, updateTime)) {
                data.updateControl();
            }

            if (interval.get(timerWinCheck, 60 * 2)) {
                Array<Player> players = data.getLeaderboard();
                if (!players.isEmpty() && data.getControlled(players.first()).size >= winCondition && players.size > 1 && data.getControlled(players.get(1)).size <= 1) {
                    endGame();
                }
            }

            if (interval.get(timerAnnounce, announcementTime)) {
                Call.sendMessage(announcements[announcementIndex % announcements.length]);
                announcementIndex ++;
            }

            if (interval.get(timerUpgrade, upgradeTime)) {
                if (upgradeLevel < starts.length - 1) {
                    Call.sendMessage("[scarlet]WARNING: [lightgray]Spawn loadout upgraded to level [yellow]" + String.valueOf(upgradeLevel + 1));
                }
                upgradeLevel = Math.min(starts.length - 1, upgradeLevel + 1);
                start = starts[upgradeLevel];
                state.rules.loadout = loadouts.get(upgradeLevel);
            }
            Array<Player> players = data.getLeaderboard();

            if (!players.isEmpty() && data.getControlled(players.first()).size >= maxHex && playerGroup.all().size < maxHex && data.getControlled(players.get(1)).size < 4) {
                endGame();
            }

            counter += Time.delta();

            //kick everyone and restart w/ the script
            if (counter > roundTime && !restarting) {
                endGame();
            }
        } else {
            counter = 0;
        }
    }
}