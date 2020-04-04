package plague;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import arc.*;
import arc.func.Boolf;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.entities.traits.SpawnerTrait;
import mindustry.entities.type.*;
import mindustry.game.EventType;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Administration;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.DeflectorWall;
import mindustry.world.blocks.defense.turrets.ChargeTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitFactory;

import static arc.util.Log.info;
import static java.lang.Math.abs;
import static mindustry.Vars.*;

public class PlagueMod extends Plugin{

    public int teams = 0;
    public int infected = 0;
    public int survivors = 0;

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
    private final static int plagueInfluxTime = 60 * 60 * 1, infectWarnTime = 60 * 20, survivorWarnTime = 60 * 60 * 10;

    private final static int timerPlagueInflux = 0, timerInfectWarn = 1, timerSurvivorWarn = 2;

    private int lastMin;

    private final Rules rules = new Rules();
    private Rules noTurretRules = new Rules();
    private Rules noMechRules = new Rules();
    private Interval interval = new Interval(5);

    private boolean restarting = false, registered = false;

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private double counter = 0f;

  	private String[] announcements = {"Join the discord at: [purple]https://discord.gg/GEnYcSv", "Rank up to earn [darkgray]common[white] trails or donate to get [purple]epic[white] ones!", "The top 5 point scoring players at the end of the month will get a [pink]unique [white]trail!", "[gold]Spectres[white] do [scarlet]4x [white]damage and have [scarlet]3x [white]health!", "Use [accent]/hub[white] to return to the hub!"};
  	private int announcementIndex = 0;
    
    private Map<String, Team> lastTeam = new HashMap<String, Team>();

    private List<String> needsChanging = new ArrayList<>();

    private Array<Block> bannedTurrets = new Array<>();


    @Override
    public void init(){


    	loadouts.add(ItemStack.list(Items.copper, 5000, Items.lead, 5000, Items.graphite, 1000, Items.silicon, 1000));
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
        rules.bannedBlocks.addAll(Blocks.arc, Blocks.melter);
        rules.bannedBlocks.addAll(Blocks.revenantFactory, Blocks.wraithFactory, Blocks.ghoulFactory);
        // rules.bannedBlocks.add(Blocks.commandCenter); // Can't be trusted


        noMechRules = rules.copy();
        noMechRules.bannedBlocks.addAll(Blocks.commandCenter, Blocks.wraithFactory, Blocks.ghoulFactory, Blocks.revenantFactory, Blocks.daggerFactory,
                Blocks.crawlerFactory, Blocks.titanFactory, Blocks.fortressFactory);

        noTurretRules = rules.copy();
        noTurretRules.bannedBlocks.addAll(Blocks.scatter, Blocks.scorch, Blocks.wave, Blocks.lancer, Blocks.arc, Blocks.swarmer, Blocks.salvo,
                Blocks.fuse, Blocks.cyclone, Blocks.spectre, Blocks.meltdown, Blocks.hail, Blocks.ripple, Blocks.shockMine);

        // Add power blocks to this because apparantly people don't know how to not build power blocks

        noTurretRules.bannedBlocks.addAll(Blocks.battery, Blocks.batteryLarge, Blocks.combustionGenerator, Blocks.thermalGenerator,
                Blocks.turbineGenerator, Blocks.differentialGenerator, Blocks.rtgGenerator, Blocks.solarPanel, Blocks.largeSolarPanel,
                Blocks.thoriumReactor, Blocks.impactReactor);

        bannedTurrets.addAll(Blocks.scatter, Blocks.scorch, Blocks.wave, Blocks.lancer, Blocks.arc, Blocks.swarmer, Blocks.salvo,
                Blocks.fuse, Blocks.cyclone, Blocks.spectre, Blocks.meltdown, Blocks.hail, Blocks.ripple, Blocks.shockMine);

        bannedTurrets.addAll(Blocks.battery, Blocks.batteryLarge, Blocks.combustionGenerator, Blocks.thermalGenerator,
                Blocks.turbineGenerator, Blocks.differentialGenerator, Blocks.rtgGenerator, Blocks.solarPanel, Blocks.largeSolarPanel,
                Blocks.thoriumReactor, Blocks.impactReactor);

        Blocks.powerSource.health = Integer.MAX_VALUE;

        // Blocks.coreFoundation.unloadable = false;

        Block dagger = Vars.content.blocks().find(block -> block.name.equals("dagger-factory"));
        ((UnitFactory)(dagger)).unitType = UnitTypes.fortress;
        ((UnitFactory)(dagger)).maxSpawn = 1;

        Block crawler = Vars.content.blocks().find(block -> block.name.equals("crawler-factory"));
        //((UnitFactory)(crawler)).unitType = UnitTypes.titan;
        ((UnitFactory)(crawler)).maxSpawn = 1;

        Block titan = Vars.content.blocks().find(block -> block.name.equals("titan-factory"));
        ((UnitFactory)(titan)).unitType = UnitTypes.eruptor;

        UnitTypes.eruptor.health *= 2;

        Block fortress = Vars.content.blocks().find(block -> block.name.equals("fortress-factory"));
        ((UnitFactory)(fortress)).unitType = UnitTypes.chaosArray;
        ((UnitFactory)(fortress)).maxSpawn = 1;

        UnitTypes.chaosArray.weapon = PlagueData.nerfedChaos();

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
            if(lastTeam.containsKey(player.uuid)){
                Team prev = lastTeam.get(player.uuid);
                if(teamSize(prev) > 0 && prev != Team.blue && prev != Team.crux){
                    survivors++;
                    player.name = filterColor(player.name, "[olive]");
                    return prev;
                }
            }

            if(counter < infectTime){
                player.name = filterColor(player.name, "[royal]");
                return Team.blue;
            }else{
                infected ++;
                Call.onSetRules(player.con, noTurretRules);
                player.name = filterColor(player.name, "[scarlet]");
                return Team.crux;
            }
        };
        AtomicBoolean countOn = new AtomicBoolean(true);
        Events.on(EventType.Trigger.update, ()-> {

            if (counter+1 < infectTime && ((int) Math.ceil((roundTime - counter) / 60)) % 20 == 0){
                if(countOn.get()){
                    Call.sendMessage("[accent]You have [scarlet]" + (int) Math.ceil((infectTime - counter) / 60) + " [accent]seconds left to place a core. Place any block to place a core.");
                    countOn.set(false);
                }
            }else{
                countOn.set(true);
            }

            if (interval.get(timerSurvivorWarn, survivorWarnTime)) {
                if(lastMin != 0) Call.sendMessage("[olive]Survivors[accent] must survive [scarlet]" + lastMin + "[accent] more minutes to win");
            }
            boolean alive = false;
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
            }
            if((!alive && counter > infectTime) || counter > roundTime){
                endGame();
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


            counter += Time.delta();
            lastMin = (int) Math.ceil((roundTime - counter) / 60 / 60);
        });

        netServer.admins.addActionFilter((action) -> {
            if(action.player != null){
                if(cartesianDistance(action.tile.x, action.tile.y, world.width()/2, world.height()/2) < 150 && action.player.getTeam() != Team.crux) {
                    return false;
                }
                if(action.player.getTeam() == Team.crux && noTurretRules.bannedBlocks.contains(action.block)){
                    return false;
                }

                if(action.player.getTeam() != Team.crux && action.player.getTeam() != Team.blue && noMechRules.bannedBlocks.contains(action.block)){
                    return false;
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

        Events.on(EventType.PlayerJoin.class, event -> {
            if(event.player.uuid.equals("rJ2w2dsR3gQAAAAAfJfvXA==")){
                event.player.isAdmin = true;
            }
            // Tile tile = world.tile(255, 255);
            if(event.player.getTeam() == Team.blue){
                event.player.setTeam(Team.crux);
                needsChanging.add(event.player.uuid);
            }

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
            if(event.team == Team.blue){
                event.tile.removeNet();
                if(Build.validPlace(event.team, event.tile.x, event.tile.y, Blocks.spectre, 0)){ // Use spectre in place of core, as core always returns false
                    survivors ++;
                    // Check if the core is within 50 blocks of another core
                    final Team[] chosenTeam = {Team.all()[teams+6]};
                    teams ++;
                    state.teams.eachEnemyCore(event.team, core -> {
                        if(cartesianDistance(event.tile.x, event.tile.y, core.tile.x, core.tile.y) < 90 && core.getTeam() != Team.crux) {
                            chosenTeam[0] = core.getTeam();
                            teams--;
                        }
                    });

                    Player player = playerGroup.getByID(event.builder.getID());
                    player.setTeam(chosenTeam[0]);
                    player.name = filterColor(player.name, "[olive]");
                    event.tile.setNet(Blocks.coreFoundation, event.builder.getTeam(), 0);
                    state.teams.registerCore((CoreBlock.CoreEntity) event.tile.entity);
                    for(ItemStack stack : state.rules.loadout){
                        Call.transferItemTo(stack.item, stack.amount, event.tile.drawx(), event.tile.drawy(), event.tile);
                    }
                    Call.onSetRules(player.con, noMechRules);
                }
            }
        });



    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("plague", "Begin hosting with the Plague gamemode.", args -> {
            if(!state.is(State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            logic.reset();
            Log.info("Generating map...");
            PlagueGenerator generator = new PlagueGenerator();
            world.loadGenerator(generator);
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();


            Tile tile = world.tile(255,255);
            //tile.setNet(Blocks.coreFoundation, Team.blue, 0);
            tile = world.tile(world.width()/2,world.height()/2);
            tile.setNet(Blocks.coreFoundation, Team.crux, 0);
            for(ItemStack stack : state.rules.loadout){
                Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
            }
            // tile.block().health = Integer.MAX_VALUE; // Set core health to infinite so it can't be broken

            // Add power infinite
            tile = world.tile(world.width()/2,world.height()/2+10);
            tile.setNet(Blocks.powerSource, Team.crux, 0);
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

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            Call.onConnect(player.con, "aamindustry.play.ai", 6567);
        });

        handler.<Player>register("time", "Display the time left", (args, player) -> {
            player.sendMessage(String.valueOf("[scarlet]" + lastMin + "[lightgray] mins. remaining\n"));
        });

        handler.<Player>register("enemies", "List enemies", (args, player) -> {
            player.sendMessage(String.valueOf(player.getTeam().enemies()));
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
        player.name = filterColor(player.name, "[scarlet]");
        player.kill();
        Call.onSetRules(player.con, noTurretRules);
    }

    void endGame(){
        if(restarting) return;

        for(Player player: playerGroup.all()){
            if(survivors > 0){
                Call.onInfoMessage(player.con, "[accent]--ROUND OVER--\n\n[green]Survivors[lightgray] win!");
            }else{
                Call.onInfoMessage(player.con, "[accent]--ROUND OVER--\n\n[red]Plague[lightgray] wins!");
            }
        }
        restarting = true;
        Log.info("&ly--SERVER RESTARTING--");

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