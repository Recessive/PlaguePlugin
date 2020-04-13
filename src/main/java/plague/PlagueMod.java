package plague;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.entities.type.*;
import mindustry.game.EventType;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.MapException;
import mindustry.net.Administration;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.ChargeTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitFactory;

import static arc.util.Log.info;
import static java.lang.Math.abs;
import java.util.prefs.Preferences;
import static mindustry.Vars.*;

class HistoryEntry {
    Player player;
    Block block;
    boolean breaking;

    public HistoryEntry(Player player, Block block, boolean breaking) {
        this.player = player;
        this.block = block;
        this.breaking = breaking;
    }
}

public class PlagueMod extends Plugin{
    private Deque<HistoryEntry>[][] history;

    public int teams = 0;
    public int infected = 0;
    public int survivors = 0;

    private Preferences prefs;

	// TPS = 40
    //in seconds
    public static final float spawnDelay = 60 * 4;
    //health requirement needed to capture a hex; no longer used
    public static final float healthRequirement = 3500;
    //item requirement to captured a hex
    public static final int itemRequirement = 1500;

    public static final int messageTime = 1;

    //in ticks: 60 minutes: 60 * 60 * 60
    private int roundTime = 60 * 60 * 40;
    //in ticks: 30 seconds
    private final static int infectTime = 60 * 60 * 2;
    private final static int gracePeriod = 60 * 60 * 10;
    private final static int plagueInfluxTime = 60 * 60 * 1, announcementTime = 60 * 60 * 5, survivorWarnTime = 60 * 60 * 10, draugIncomeTime = 60 * 20;

    private final static int timerPlagueInflux = 0, timerAnnouncement = 1, timerSurvivorWarn = 2, timerDraugIncome = 3;

    private int lastMin;

    private final Rules rules = new Rules();
    private Rules survivorBanned = new Rules();
    private Rules plagueBanned = new Rules();
    private Interval interval = new Interval(5);

    private boolean restarting = false, registered = false;

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private double counter = 0f;
    private String mapName;
    private String mapAuthor;

    private int[] plagueCore = new int[2];


  	private String[] announcements = {"Join the discord at: [purple]https://discord.gg/GEnYcSv", "Use [accent]/hub[white] to return to the hub!","INSERT MAP VOTE MESSAGE"};
  	private int announcementIndex = 0;
    
    private Map<String, Team> lastTeam = new HashMap<String, Team>();
    private List<String> needsChanging = new ArrayList<>();

    private HashMap<Team, List<Tile>> draugCount = new HashMap<Team, List<Tile>>();
    private static final int voteSize = 5;
    private List<String> mapList = new ArrayList<>();
    private List<Integer> votableMaps = new ArrayList<>();
    private List<Integer> mapVotes = new ArrayList<>();
    private HashMap<String, Integer> playerMapVote = new HashMap<>();


    @Override
    public void init(){


    	loadouts.add(ItemStack.list(Items.copper, 25000, Items.lead, 25000, Items.graphite, 8000, Items.silicon, 8000, Items.titanium, 10000, Items.metaglass, 500));
    	loadouts.add(ItemStack.list(Items.titanium, 1000, Items.graphite, 400, Items.silicon, 400));
        rules.pvp = !true;
        rules.tags.put("plague", "true");
        rules.loadout = loadouts.get(0);
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 2;
        rules.blockHealthMultiplier = 1f;
        rules.unitBuildSpeedMultiplier = 1f;
        rules.playerDamageMultiplier = 0f;
        //rules.enemyCoreBuildRadius = 100 * tilesize;
        rules.unitDamageMultiplier = 1f;
        rules.playerHealthMultiplier = 0.5f;
        rules.canGameOver = false;
        rules.reactorExplosions = false;
        rules.respawnTime = 0;
        // rules.bannedBlocks.addAll(Blocks.solarPanel, Blocks.largeSolarPanel);
        rules.bannedBlocks.addAll(Blocks.arc);
        rules.bannedBlocks.addAll(Blocks.revenantFactory, Blocks.wraithFactory, Blocks.ghoulFactory);
        rules.bannedBlocks.add(Blocks.commandCenter); // Can't be trusted

        survivorBanned = rules.copy();
        survivorBanned.bannedBlocks.addAll(Blocks.commandCenter, Blocks.wraithFactory, Blocks.ghoulFactory, Blocks.revenantFactory, Blocks.daggerFactory,
                Blocks.crawlerFactory, Blocks.titanFactory, Blocks.fortressFactory);

        plagueBanned = rules.copy();
        plagueBanned.bannedBlocks.addAll(Blocks.duo, Blocks.scatter, Blocks.scorch, Blocks.wave, Blocks.lancer, Blocks.arc, Blocks.swarmer, Blocks.salvo,
                Blocks.fuse, Blocks.cyclone, Blocks.spectre, Blocks.meltdown, Blocks.hail, Blocks.ripple, Blocks.shockMine);

        // Add power blocks to this because apparantly people don't know how to not build power blocks

        plagueBanned.bannedBlocks.addAll(Blocks.battery, Blocks.batteryLarge, Blocks.combustionGenerator, Blocks.thermalGenerator,
                Blocks.turbineGenerator, Blocks.differentialGenerator, Blocks.rtgGenerator, Blocks.solarPanel, Blocks.largeSolarPanel,
                Blocks.thoriumReactor, Blocks.impactReactor);

        plagueBanned.bannedBlocks.addAll(Blocks.surgeWall, Blocks.surgeWallLarge, Blocks.thoriumWall, Blocks.thoriumWallLarge, Blocks.phaseWall,
            Blocks.phaseWallLarge, Blocks.titaniumWall, Blocks.titaniumWallLarge, Blocks.copperWallLarge, Blocks.copperWall, Blocks.door,
                Blocks.doorLarge, Blocks.plastaniumWall, Blocks.plastaniumWallLarge);

        plagueBanned.bannedBlocks.addAll(Blocks.mendProjector);
        plagueBanned.bannedBlocks.addAll(Blocks.glaivePad);

        Blocks.powerSource.health = Integer.MAX_VALUE;

        // Blocks.coreFoundation.unloadable = false;

        Block drauge = Vars.content.blocks().find(block -> block.name.equals("draug-factory"));
        ((UnitFactory)(drauge)).maxSpawn = 0; // Should always be 0


        Block dagger = Vars.content.blocks().find(block -> block.name.equals("dagger-factory"));
        ((UnitFactory)(dagger)).unitType = UnitTypes.fortress;
        ((UnitFactory)(dagger)).maxSpawn = 0; // 1

        Block crawler = Vars.content.blocks().find(block -> block.name.equals("crawler-factory"));
        //((UnitFactory)(crawler)).unitType = UnitTypes.titan;
        ((UnitFactory)(crawler)).maxSpawn = 0; // 1

        Block titan = Vars.content.blocks().find(block -> block.name.equals("titan-factory"));
        ((UnitFactory)(titan)).unitType = UnitTypes.eruptor;
        ((UnitFactory)(titan)).maxSpawn = 0; // 4

        UnitTypes.eruptor.health *= 2;

        Block fortress = Vars.content.blocks().find(block -> block.name.equals("fortress-factory"));
        ((UnitFactory)(fortress)).unitType = UnitTypes.chaosArray;
        ((UnitFactory)(fortress)).produceTime *= 2;
        ((UnitFactory)(fortress)).maxSpawn = 0; // 1

        // Disable slag flammability to prevent griefing
        Liquids.slag.flammability = 0;
        Liquids.slag.explosiveness = 0;

        UnitTypes.chaosArray.weapon = PlagueData.nerfedChaos();

        Blocks.phaseWall.health /= 2;
        Blocks.phaseWallLarge.health /= 2;

        // Update phase wall to only deflect 50% of the time
        /*Block phaseSmall = Vars.content.blocks().find(block -> block.name.equals("phase-wall"));
        Vars.content.blocks().remove(phaseSmall);
        phaseSmall = PlagueData.newPhaseSmall();
        Vars.content.blocks().add(phaseSmall);*/

        /*Block phaseLarge = Vars.content.blocks().find(block -> block.name.equals("phase-wall-large"));
        Vars.content.blocks().remove(phaseLarge);
        phaseLarge = PlagueData.newPhaseLarge();
        Vars.content.blocks().add(phaseLarge);*/


        Core.settings.putSave("playerlimit", 0);

        // Disable lancer pierce:
        Block lancer = Vars.content.blocks().find(block -> block.name.equals("lancer"));
        ((ChargeTurret)(lancer)).shootType = PlagueData.getLLaser();

        netServer.assigner = (player, players) -> {
            // Make admins
            // Recessive
            if(player.uuid.equals("rJ2w2dsR3gQAAAAAfJfvXA==")){
                player.isAdmin = true;
            }
            // Pointifix
            if(player.uuid.equals("Z5J7zdYbz+UAAAAAOHW3FA==")){
                player.isAdmin = true;
            }
            // mobile
            if(player.uuid.equals("bRco7qu/QD4AAAAAld5DIQ==")){
                player.isAdmin = true;
            }
            // englishtems
            if(player.uuid.equals("BecvbJgK+wEAAAAAtNEgRg==")){
                player.isAdmin = true;
            }



            if(lastTeam.containsKey(player.uuid)){
                Team prev = lastTeam.get(player.uuid);
                if(teamSize(prev) > 0 && prev != Team.blue && prev != Team.crux){
                    survivors++;
                    if(player.isAdmin){
                        player.name = filterColor(player.name, "[green]");
                    }else{
                        player.name = filterColor(player.name, "[olive]");
                    }
                    return prev;
                }
            }

            if(counter < infectTime){
                if(player.isAdmin){
                    player.name = filterColor(player.name, "[blue]");
                }else{
                    player.name = filterColor(player.name, "[royal]");
                }

                return Team.blue;
            }else{
                infected ++;
                Call.onSetRules(player.con, plagueBanned);
                if(player.isAdmin){
                    player.name = filterColor(player.name, "[red]");
                }else{
                    player.name = filterColor(player.name, "[scarlet]");
                }
                return Team.crux;
            }
        };
        AtomicBoolean infectCountOn = new AtomicBoolean(true);
        AtomicBoolean graceCountOn = new AtomicBoolean(true);
        AtomicBoolean graceOver = new AtomicBoolean(false);
        List<Team> alreadyChecked = new ArrayList<>();

        Events.on(EventType.Trigger.update, ()-> {

            if (counter+1 < infectTime && ((int) Math.ceil((roundTime - counter) / 60)) % 20 == 0){
                if(infectCountOn.get()){
                    Call.sendMessage("[accent]You have [scarlet]" + (int) Math.ceil((infectTime - counter) / 60) + " [accent]seconds left to place a core. Place any block to place a core.");
                    infectCountOn.set(false);
                }
            }else{
                infectCountOn.set(true);
            }

            if (counter+1 < gracePeriod && counter+1 > infectTime && ((int) Math.ceil((roundTime - counter) / 60)) % 60 == 0){
                if(graceCountOn.get()){
                    Call.sendMessage("[accent]Grace period ends in [scarlet]" + (int) Math.ceil((gracePeriod - counter) / 60 / 60) + " [accent]minutes.");
                    graceCountOn.set(false);
                }
            }else{
                graceCountOn.set(true);
            }

            if(counter+1 > gracePeriod && !graceOver.get()){
                Call.sendMessage("[accent]Grace period has expired");
                graceOver.set(true);
                ((UnitFactory)(dagger)).maxSpawn = 1; // 1
                ((UnitFactory)(crawler)).maxSpawn = 1; // 1
                ((UnitFactory)(titan)).maxSpawn = 4; // 4
                ((UnitFactory)(fortress)).maxSpawn = 1; // 1
            }

            if (interval.get(timerSurvivorWarn, survivorWarnTime)) {
                if(lastMin != 0) Call.sendMessage("[olive]Survivors[accent] must survive [scarlet]" + lastMin + "[accent] more minutes to win");
            }
            boolean alive = false;

            boolean draugTime = interval.get(timerDraugIncome, draugIncomeTime);

            for (Player player : playerGroup.all()) {
                Team ply_team = player.getTeam();
                if (ply_team != Team.derelict && ply_team.cores().isEmpty() && counter > infectTime) {
                    infect(player);
                }
                if (needsChanging.contains(player.uuid) && !player.dead) {
                    player.setTeam(Team.blue);
                    needsChanging.remove(player.uuid);
                }
                if(ply_team != Team.derelict && ply_team != Team.crux){
                    alive = true;
                }

                if(draugTime && draugCount.containsKey(ply_team) && !alreadyChecked.contains(ply_team) && state.teams.cores(ply_team).size > 0){
                    alreadyChecked.add(ply_team);
                    CoreBlock.CoreEntity teamCore = state.teams.cores(ply_team).get(0);
                    for(int i = 0; i < draugCount.get(ply_team).size(); i++){
                        Call.transferItemTo(Items.copper, 40, teamCore.x, teamCore.y, teamCore.tile);
                        Call.transferItemTo(Items.lead, 40, teamCore.x, teamCore.y, teamCore.tile);
                    }
                }
            }
            alreadyChecked.clear();
            if((!alive && counter > infectTime) || counter > roundTime){
                endGame(alive);
            }
            if(counter > infectTime && counter < infectTime*2 && infected == 0 && playerGroup.all().size > 0){
                Player player = playerGroup.all().random();
                infect(player);
            }

            if (interval.get(timerPlagueInflux, plagueInfluxTime)){
                Tile tile = Team.crux.cores().get(0).tile;
                for(ItemStack stack : loadouts.get(1)){
                    Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
                }
                // Call.sendMessage("The [red]Plague [white] was reinforced with resources");
            }

            if (interval.get(timerAnnouncement, announcementTime)){
                Call.sendMessage(announcements[announcementIndex]);
                announcementIndex = (announcementIndex + 1) % announcements.length;
            }


            counter += Time.delta();
            lastMin = (int) Math.ceil((roundTime - counter) / 60 / 60);
        });

        netServer.admins.addChatFilter((player, text) -> {
            for(String swear : CurseFilter.swears){
                text = text.replaceAll("(?i)" + swear, "");
            }
            return text;
        });

        netServer.admins.addActionFilter((action) -> {

            if(action.player != null){
                if(cartesianDistance(action.tile.x, action.tile.y, plagueCore[0], plagueCore[1]) < world.height()/4 && action.player.getTeam() != Team.crux) {
                    return false;
                }


                if(action.player.getTeam() == Team.crux && cartesianDistance(action.tile.x, action.tile.y, plagueCore[0], plagueCore[1]) > world.height()/4
                && action.type == Administration.ActionType.placeBlock){
                    // Scan 3 blocks in every direction
                    for(int x = -2; x < 3; x++){
                        for(int y = -2; y < 3; y++){
                            if(action.tile.x+x < 0 || action.tile.x+x > world.width() || action.tile.y+y < 0 || action.tile.y+y > world.height()){
                                continue;
                            }
                            Tile tile = world.tile(action.tile.x+x, action.tile.y+y);
                            if(tile == null) continue;
                            Team t = tile.getTeam();
                            if(t != null && t != Team.crux && t != Team.derelict && tile.block() != Blocks.air){
                                return false;
                            }
                        }
                    }
                }

                if(action.player.getTeam() == Team.crux && plagueBanned.bannedBlocks.contains(action.block)){
                    return false;
                }

                if(action.player.getTeam() != Team.crux && action.player.getTeam() != Team.blue && survivorBanned.bannedBlocks.contains(action.block)){
                    return false;
                }

                if(action.type == Administration.ActionType.withdrawItem){
                   if(action.item.flammability != 0 || action.item.explosiveness != 0){
                       return false;
                   }
                }
            }
            if((action.type == Administration.ActionType.breakBlock || action.type == Administration.ActionType.placeBlock) && (action.tile.block() == Blocks.powerSource || action.tile.block() == Blocks.itemSource)){
                return false;
            }
            if(action.type == Administration.ActionType.configure && action.tile.block() == Blocks.powerSource && action.player != null){
                // Call.sendMessage(action.player.name + " is mucking with the power infinite");
                action.player.sendMessage("[accent]You just desynced yourself. Use [scarlet]/sync[accent] to resync");
                return false;
            }

            return true;
        });

        Events.on(EventType.PlayerConnect.class, event -> {
            for(String swear : BannedNames.badNames){
                if(event.player.name.toLowerCase().contains(swear)){
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "");
                }
            }
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            // Tile tile = world.tile(255, 255);
            if(event.player.getTeam() == Team.blue){
                event.player.setTeam(Team.crux);
                needsChanging.add(event.player.uuid);
            }
            event.player.sendMessage("[accent]Map: [scarlet]" + mapName + "\n[accent]Author: [scarlet]" + mapAuthor);

        });

        Events.on(EventType.PlayerLeave.class, event -> {
            if(event.player.getTeam() != Team.crux && teamSize(event.player.getTeam()) < 2) {
                killTiles(event.player.getTeam(), event.player);
            }
            if(event.player.getTeam() == Team.crux){
                infected --;
            }else if (event.player.getTeam() != Team.blue){
                survivors --;
            }

            lastTeam.put(event.player.uuid, event.player.getTeam());
        });

        Events.on(EventType.BuildSelectEvent.class, event ->{

            if(event.breaking && draugCount.containsKey(event.team) && draugCount.get(event.team).contains(event.tile)){
                draugCount.get(event.team).remove(event.tile);
            }

            if(event.team == Team.blue){
                event.tile.removeNet();
                if(Build.validPlace(event.team, event.tile.x, event.tile.y, Blocks.spectre, 0)){ // Use spectre in place of core, as core always returns false
                    survivors ++;
                    // Check if the core is within 50 blocks of another core
                    final Team[] chosenTeam = {Team.all()[teams+6]};
                    teams ++;
                    final boolean[] breakLoop = {false};
                    state.teams.eachEnemyCore(event.team, core -> {
                        if(!breakLoop[0] && cartesianDistance(event.tile.x, event.tile.y, core.tile.x, core.tile.y) < 100 && core.getTeam() != Team.crux) {
                            chosenTeam[0] = core.getTeam();
                            teams--;
                            breakLoop[0] = true;
                        }
                    });

                    Player player = playerGroup.getByID(event.builder.getID());
                    player.setTeam(chosenTeam[0]);
                    if(player.isAdmin){
                        player.name = filterColor(player.name, "[green]");
                    }else{
                        player.name = filterColor(player.name, "[olive]");
                    }
                    event.tile.setNet(Blocks.coreFoundation, chosenTeam[0], 0);
                    state.teams.registerCore((CoreBlock.CoreEntity) event.tile.entity);
                    Log.info(state.teams.cores(chosenTeam[0]).size);
                    if (state.teams.cores(chosenTeam[0]).size == 1){
                        for(ItemStack stack : state.rules.loadout){
                            Call.transferItemTo(stack.item, stack.amount, event.tile.drawx(), event.tile.drawy(), event.tile);
                        }
                    }

                    Call.onSetRules(player.con, survivorBanned);
                }
            }
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if(event.tile.block() == Blocks.draugFactory){
                if(draugCount.containsKey(event.team)){
                    draugCount.get(event.team).add(event.tile);
                }else{
                    draugCount.put(event.team, new LinkedList<>(Arrays.asList(event.tile)));
                }
            }
        });

        Events.on(EventType.WorldLoadEvent.class, worldLoadEvent -> {
            history = new ArrayDeque[Vars.world.width()][Vars.world.height()];

            for(int x = 0; x < Vars.world.width(); x++){
                for(int y = 0; y < Vars.world.height(); y++){
                    history[x][y] = new ArrayDeque<HistoryEntry>();
                }
            }
        });

        Events.on(EventType.BlockBuildEndEvent.class, blockBuildEndEvent -> {
            HistoryEntry historyEntry = new HistoryEntry(blockBuildEndEvent.player, blockBuildEndEvent.tile.block(), blockBuildEndEvent.breaking);

            Deque<HistoryEntry> tileHistory = this.history[blockBuildEndEvent.tile.x][blockBuildEndEvent.tile.y];
            if(tileHistory.size() > 10) tileHistory.removeFirst();
            tileHistory.add(historyEntry);
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("plague", "Begin hosting with the Plague gamemode.", args -> {
            if(!state.is(State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            prefs = Preferences.userRoot().node(this.getClass().getName());
            int currMap = prefs.getInt("mapchoice",0);
            prefs.putInt("mapchoice", 0); // This is just backup so the server reverts to patient zero if a map crashes
            Log.info("Map choice: " + currMap);
            //currMap = 15;

            List<Integer> allMaps = new ArrayList<>();

            for(int i =0; i < maps.customMaps().size+1; i++){
                if(i!=currMap && maps.customMaps().size > 0) allMaps.add(i);
            }
            Collections.shuffle(allMaps);
            for(int i =0; i < voteSize; i++){
                int mapInd = allMaps.get(i);
                votableMaps.add(mapInd);
                if(mapInd == 0) mapList.add("Patient zero"); else mapList.add(maps.customMaps().get(mapInd-1).name());
                mapVotes.add(0);
            }

            String str = "";
            str += "[accent]Vote on the next map using /votemap, voting between [scarlet]1[accent] and [scarlet]" + votableMaps.size() + "[accent] on the following maps:\n";
            for(int i = 0; i < votableMaps.size(); i++){
                str += "[scarlet]" + (i+1) + "[white]: " + mapList.get(i) +  "\n";
            }
            announcements[2] = str;

            int i = 1;
            for(mindustry.maps.Map m : maps.customMaps()){
                Log.info(i + ": " + m.name());
                i ++;
            }



            logic.reset();
            if(currMap == 0){
                Log.info("Generating map...");
                PlagueGenerator generator = new PlagueGenerator();
                world.loadGenerator(generator);
                Log.info("Map generated.");
            }else{
                mindustry.maps.Map map = maps.customMaps().get(currMap-1);
                world.loadMap(map);
            }
            Tile tile = state.teams.cores(Team.crux).get(0).tile;
            plagueCore[0] = tile.x;
            plagueCore[1] = tile.y;
            PlagueGenerator.inverseFloodFill(world.getTiles(), plagueCore[0], plagueCore[1]);
            PlagueGenerator.defaultOres(world.getTiles());
            mapName = world.getMap().name();
            mapAuthor = world.getMap().author();

            Log.info("Current map: " + mapName);

            state.rules = rules.copy();
            logic.play();
            netServer.openServer();


            //Tile tile = world.tile(255,255);
            //tile.setNet(Blocks.coreFoundation, Team.blue, 0);
            //tile = world.tile(world.width()/2,world.height()/2);
            //tile.setNet(Blocks.coreFoundation, Team.crux, 0);
            for(ItemStack stack : state.rules.loadout){
                Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
            }
            // tile.block().health = Integer.MAX_VALUE; // Set core health to infinite so it can't be broken

            // Add power infinite
            //tile = world.tile(world.width()/2,world.height()/2+10);
            //tile.setNet(Blocks.powerSource, Team.crux, 0);
        });

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc{0} minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("end", "End the game.", args -> endGame(true));

        handler.register("r", "Restart the server.", args -> System.exit(2));

    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        if(registered) return;
        registered = true;

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            Call.onConnect(player.con, "aamindustry.play.ai", 6567);
        });

        handler.<Player>register("discord", "Prints the discord link", (args, player) -> {
            player.sendMessage("[purple]https://discord.gg/GEnYcSv");
        });

        handler.<Player>register("maps", "Show votable maps", (args, player) -> {
            player.sendMessage(announcements[2]);
        });

        handler.<Player>register("votemap", "<number>", "Vote for the next map", (args, player) -> {
            int vote;
            try{
                vote = Integer.parseInt(args[0]);
            }catch (NumberFormatException ignored){
                vote = -1;
            }

            if(vote < 1 || vote > votableMaps.size()){
                String str = "";
                str += "[accent]Your vote must be between [scarlet]1[accent] and [scarlet]" + votableMaps.size() + "[accent] on the following maps:\n";
                for(int i = 0; i < votableMaps.size(); i++){
                    str += "[scarlet]" + (i+1) + "[white]: " + mapList.get(i) +  " ([accent]" + mapVotes.get(i) + "[white])\n";
                }
                player.sendMessage(str);
                return;
            }
            vote -= 1;
            if(playerMapVote.containsKey(player.uuid)){
                int lastVote = playerMapVote.get(player.uuid);
                mapVotes.set(lastVote, mapVotes.get(lastVote)-1);
                playerMapVote.put(player.uuid, vote);
                mapVotes.set(vote, mapVotes.get(vote)+1);
                player.sendMessage("[accent]Changed vote from [scarlet]" + (lastVote+1) + "[accent] to [scarlet]" + (vote+1));
            }else{
                playerMapVote.put(player.uuid, vote);
                mapVotes.set(vote, mapVotes.get(vote)+1);
                player.sendMessage("[accent]You voted on map [scarlet]" + (vote+1));
            }

        });

        handler.<Player>register("uuid", "Prints your uuid", (args, player) -> {
            player.sendMessage("[accent]Your uuid is: [scarlet]" + player.uuid);
        });

        handler.<Player>register("time", "Display the time left", (args, player) -> {
            player.sendMessage(String.valueOf("[scarlet]" + lastMin + "[lightgray] mins. remaining\n"));
        });

        handler.<Player>register("enemies", "List enemies", (args, player) -> {
            player.sendMessage(String.valueOf(player.getTeam().enemies()));
        });

        handler.<Player>register("whoami", "Prints your team", (args, player) -> {
            player.sendMessage(String.valueOf(player.getTeam()));
        });

        handler.<Player>register("infect", "Infect yourself", (args, player) -> {
            Team plyTeam = player.getTeam();
            if(plyTeam != Team.crux){
                if(teamSize(plyTeam) < 2){
                    killTiles(plyTeam, player);
                }
                infect(player);
            }else{
                player.sendMessage("You are already infected!");
            }

        });

        handler.<Player>register("mechspeed", "List the mech speed to verify that aint the issue", (args, player) -> {
            for(int i = 0; i < content.units().size; i++){
                player.sendMessage(content.units().get(i).name + ": " + content.units().get(i).speed);
            }

        });

        handler.<Player>register("endgame", "End the game.", (args, player) -> {
            if(player.isAdmin){
                endGame(true);
            }else{
                player.sendMessage("[accent]You do not have the required permissions to run this command");
            }
        });
        
        handler.<Player>register("history", "", "Display history of this tile", (args, player) -> {
            Deque<HistoryEntry> tileHistory = this.history[player.tileX()][player.tileY()];

            player.sendMessage("[royal]History of tile (" + player.tileX() + "|" + player.tileY() + "):");
            for (HistoryEntry historyEntry : tileHistory) {
                if(historyEntry.breaking) {
                    player.sendMessage(filterColor(historyEntry.player.name, "[scarlet]") + "[white] broke this block" + " (uuid: [scarlet]" + historyEntry.player.uuid +"[white])");
                } else {
                    player.sendMessage(filterColor(historyEntry.player.name, "[scarlet]") + "[white] placed a " + historyEntry.block + " (uuid: [scarlet]" + historyEntry.player.uuid +"[white])");
                }
            }
        });
    }

    int teamSize(Team t){
        int size = 0;
        for(Player player : playerGroup.all()){
            if (player.getTeam() == t) size ++;
        }
        return size;
    }

    void infect(Player player){

        infected ++;
        if(player.getTeam() != Team.blue) survivors --;
        Call.sendMessage("[accent]" + player.name + "[white] was [red]infected[white]!");
        if(teamSize(player.getTeam()) < 2) killTiles(player.getTeam(), player);
        player.setTeam(Team.crux);
        if(player.isAdmin){
            player.name = filterColor(player.name, "[red]");
        }else{
            player.name = filterColor(player.name, "[scarlet]");
        }
        player.kill();
        Call.onSetRules(player.con, plagueBanned);
    }

    void endGame(boolean survivorWin){
        if(restarting) return;


        for(Player player: playerGroup.all()){
            if(survivorWin){
                Call.onInfoMessage(player.con, "[accent]--ROUND OVER--\n\n[green]Survivors[lightgray] win!");
            }else{
                Call.onInfoMessage(player.con, "[accent]--ROUND OVER--\n\n[red]Plague[lightgray] wins!");
            }
        }
        restarting = true;
        Log.info("&ly--SERVER RESTARTING--");

        int votedMap = mapVotes.indexOf(Collections.max(mapVotes));
        Log.info(votedMap);

        prefs = Preferences.userRoot().node(this.getClass().getName());
        prefs.putInt("mapchoice", votableMaps.get(votedMap));
        Call.sendMessage("[accent]Loading map [scarlet]" + mapList.get(votedMap) + "[accent] with the most votes");

        Time.runTask(60f * 10f, () -> {
            for(Player player : playerGroup.all()) {
                Call.onConnect(player.con, "aamindustry.play.ai", 6567);
                player.con.close();
            }
            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Time.runTask(5f, () -> System.exit(2));
        });
    }


    void killTiles(Team team, Player player){
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.entity != null && tile.getTeam() == team){
                    Time.run(Mathf.random(60f * 6), tile.entity::kill);
                }
            }
        }
    }

    public String filterColor(String s, String prefix){
        return prefix + Strings.stripColors(s);
    }

    public boolean active(){
        return state.rules.tags.getBool("plague") && !state.is(State.menu);
    }

    private float cartesianDistance(float x, float y, float cx, float cy){
        return (float) Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2) );
    }
}