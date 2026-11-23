package com.gmail.val59000mc.maploader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.InvalidConfigurationException;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.bridge.VersionBridge;
import com.gmail.val59000mc.bridge.VersionBridgeProvider;
import com.gmail.val59000mc.configuration.MainConfig;
import com.gmail.val59000mc.configuration.YamlFile;
import com.gmail.val59000mc.game.GameManager;
import com.gmail.val59000mc.schematics.DeathmatchArena;
import com.gmail.val59000mc.schematics.Lobby;
import com.gmail.val59000mc.schematics.UndergroundNether;
import com.gmail.val59000mc.tasks.ChunkLoaderTask;
import com.gmail.val59000mc.tasks.WorldBorderTask;
import com.gmail.val59000mc.utils.FileUtils;
import com.gmail.val59000mc.utils.VersionUtils;
import com.gmail.val59000mc.versionadapters.PreGenerationProgress;
import com.gmail.val59000mc.versionadapters.adapters.SetBiomeProviderAdapter;
import com.pieterdebot.biomemapping.Biome;
import com.pieterdebot.biomemapping.BiomeMappingAPI;


public class MapLoader {

	private static final Logger LOGGER = Logger.getLogger(MapLoader.class.getCanonicalName());

	public final static String DO_DAYLIGHT_CYCLE = "doDaylightCycle";
	public final static String DO_MOB_SPAWNING = "doMobSpawning";
	public final static String NATURAL_REGENERATION = "naturalRegeneration";
	public final static String LOCATOR_BAR = "locatorBar";
	public final static String SPAWN_CHUNK_RADIUS = "spawnChunkRadius";
	public final static String ANNOUNCE_ADVANCEMENTS = "announceAdvancements";
	public final static String COMMAND_BLOCK_OUTPUT = "commandBlockOutput";
	public final static String LOG_ADMIN_COMMANDS = "logAdminCommands";

	private static final String MINECRAFT_NAMESPACE = "minecraft";

	private final MainConfig config;
	// Name Bukkit registers each world under, which is what every lookup uses.
	private final Map<Environment, String> worldNames;
	// Key of each world ("uhccore:<path>"), null for plain-named ones.
	private final Map<Environment, String> worldKeys;
	// Where each world actually landed on disk, as written to storage.yml. Paper 26+ keeps
	// worlds under world/dimensions/<namespace>/ instead of the world container root.
	private final Map<Environment, String> worldFolders;
	private final PreGenerationProgress preGenerationProgress;

	private Lobby lobby;
	private DeathmatchArena arena;

	private long mapSeed;
	private String mapName;

	public MapLoader(MainConfig config){
		this.config = config;
		worldNames = new HashMap<>();
		worldKeys = new HashMap<>();
		worldFolders = new HashMap<>();
		preGenerationProgress = new PreGenerationProgress();
		mapSeed = -1;
		mapName = null;
	}

	public PreGenerationProgress getPreGenerationProgress() {
		return preGenerationProgress;
	}

	public Lobby getLobby() {
		return lobby;
	}

	public DeathmatchArena getArena() {
		return arena;
	}

	public boolean isUhcWorld(@Nullable World world) {
		if (world == null) {
			return false;
		}
		for (Environment env : Environment.values()) {
			if (world.equals(getUhcWorld(env))) {
				return true;
			}
		}
		return false;
	}

	public double getOverworldBorderApothem(){
		World overworld = GameManager.getGameManager().getMapLoader().getUhcWorld(World.Environment.NORMAL);
		return overworld.getWorldBorder().getSize()/2;
	}

	private void removeOceansUsingBiomeMapping() throws Exception {
		final BiomeMappingAPI biomeMapping = new BiomeMappingAPI();
		Biome replacementBiome = Biome.PLAINS;

		for (Biome biome : Biome.values()) {
			if (biome.isOcean() && biomeMapping.biomeSupported(biome)) {
				biomeMapping.replaceBiomes(biome, replacementBiome);

				replacementBiome = replacementBiome == Biome.PLAINS ? Biome.FOREST : Biome.PLAINS;
			}
		}
	}

	private void removeOceans() {
		try {
			if (!VersionUtils.isAtLeast(17)) {
				LOGGER.fine("Removing oceans using BiomeMapping");
				removeOceansUsingBiomeMapping();
			} else {
				LOGGER.info("The 'replace-ocean-biomes' setting is not supported on Minecraft 1.17+");
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Unable to remove ocean biomes", e);
		}
	}

	public void loadWorlds(boolean debug) {
		if (config.get(MainConfig.REPLACE_OCEAN_BIOMES)) {
			removeOceans();
		}

		deleteOldPlayersFiles();

		if(debug){
			loadOldWorld(Environment.NORMAL);
			if (config.get(MainConfig.ENABLE_NETHER)) {
				loadOldWorld(Environment.NETHER);
			}
			if (config.get(MainConfig.ENABLE_THE_END)) {
				loadOldWorld(Environment.THE_END);
			}
		}else{
			deleteLastWorld(Environment.NORMAL);
			deleteLastWorld(Environment.NETHER);
			deleteLastWorld(Environment.THE_END);
			// Never in debug mode: that one reuses the stored worlds.
			deleteOrphanWorlds();

			createNewWorld(Environment.NORMAL);
			if (config.get(MainConfig.ENABLE_NETHER)) {
				createNewWorld(Environment.NETHER);
			}
			if (config.get(MainConfig.ENABLE_THE_END)) {
				createNewWorld(Environment.THE_END);
			}
		}
	}

	public void deleteLastWorld(Environment env){
		String worldName = worldNames.get(env);

		if(!isUsableWorldName(worldName)){
			LOGGER.info("No world to delete");
			return;
		}

		World loadedWorld = Bukkit.getWorld(worldName);
		if (loadedWorld != null) {
			// Deleting region files of a loaded world fails on Windows (file locks) and
			// corrupts it everywhere else, so it has to leave memory first.
			if (isMainWorld(loadedWorld) || !Bukkit.unloadWorld(loadedWorld, false)) {
				LOGGER.warning("World " + worldName + " is still loaded and can't be unloaded, leaving its files alone");
				return;
			}
		}

		List<File> worldDirs = findWorldFolders(worldName, worldKeys.get(env), worldFolders.get(env));
		if (worldDirs.isEmpty()) {
			LOGGER.info("World "+worldName+" can't be removed, directory not found");
			return;
		}

		for (File worldDir : worldDirs) {
			LOGGER.info("Deleting last world : " + worldDir.getPath());
			if (!FileUtils.deleteFile(worldDir)) {
				LOGGER.warning("Unable to fully delete " + worldDir.getPath() + ", some files are probably still locked");
			}
		}
	}

	/**
	 * Removes UHC worlds left behind by crashes, kill -9 or older versions that looked in the
	 * wrong folder. Everything under {@code dimensions/uhccore} is ours. In {@code dimensions/minecraft}
	 * only UUID-named world folders are touched, so vanilla and other plugins' dimensions survive.
	 */
	private void deleteOrphanWorlds() {
		Set<Path> loadedFolders = getLoadedWorldFolders();

		for (File worldDir : listSubdirectories(getDimensionsFolder(VersionBridge.UHC_WORLD_NAMESPACE))) {
			deleteOrphanWorld(worldDir, loadedFolders);
		}

		for (File worldDir : listSubdirectories(getDimensionsFolder(MINECRAFT_NAMESPACE))) {
			if (isUuid(worldDir.getName()) && looksLikeWorld(worldDir)) {
				deleteOrphanWorld(worldDir, loadedFolders);
			}
		}
	}

	private void deleteOrphanWorld(File worldDir, Set<Path> loadedFolders) {
		if (loadedFolders.contains(normalize(worldDir))) {
			return;
		}
		LOGGER.info("Deleting leftover world from a previous game : " + worldDir.getPath());
		if (!FileUtils.deleteFile(worldDir)) {
			LOGGER.warning("Unable to fully delete " + worldDir.getPath());
		}
	}

	public void createNewWorld(Environment env){
		String worldPath = UUID.randomUUID().toString();
		if (UhcCore.getPlugin().getConfig().getBoolean("permanent-world-names", false)){
			worldPath = "uhc-"+env.name().toLowerCase();
		}

		LOGGER.info("Creating new world : "+worldPath);

		GameManager gm = GameManager.getGameManager();

		List<Long> seeds = gm.getConfig().get(MainConfig.SEEDS);
		List<String> worlds = gm.getConfig().get(MainConfig.WORLDS);
		boolean useSeedList = gm.getConfig().get(MainConfig.PICK_RANDOM_SEED_FROM_LIST) && !seeds.isEmpty();
		boolean copyMapFromList = !useSeedList && gm.getConfig().get(MainConfig.PICK_RANDOM_WORLD_FROM_LIST) && !worlds.isEmpty();

		// Map copies land in the world container root, which is where a plain-named world loads from up
		// to 1.21. Builds that keep worlds under world/dimensions never pick the copy up.
		WorldCreator wc = copyMapFromList
			? new WorldCreator(worldPath)
			: VersionBridgeProvider.getBridge().createUhcWorldCreator(worldPath);
		// Not worldPath: Paper 26.x registers uhccore:<path> as uhccore_<path>. WorldInitEvent fires
		// inside createWorld, so the name has to be known before that.
		String worldName = wc.name();
		String worldKey = copyMapFromList ? null : VersionBridgeProvider.getBridge().getWorldKey(wc);
		wc.generateStructures(config.get(MainConfig.GENERATE_STRUCTURES));
		if (env == Environment.NORMAL) {
			final String cgOverworld = config.get(MainConfig.CHUNK_GENERATOR_OVERWORLD);
			if (!cgOverworld.isEmpty()) {
				wc.generator(cgOverworld);
			}
			final String bpOverworld = config.get(MainConfig.BIOME_PROVIDER_OVERWORLD);
			if (!bpOverworld.isEmpty()) {
				UhcCore.getVersionAdapterLoader().getVersionAdapter(SetBiomeProviderAdapter.class)
					.setBiomeProvider(wc, bpOverworld);
			}
		} else if (env == Environment.NETHER) {
			final String cgNether = config.get(MainConfig.CHUNK_GENERATOR_NETHER);
			if (!cgNether.isEmpty()) {
				wc.generator(cgNether);
			}
			final String bpNether = config.get(MainConfig.BIOME_PROVIDER_NETHER);
			if (!bpNether.isEmpty()) {
				UhcCore.getVersionAdapterLoader().getVersionAdapter(SetBiomeProviderAdapter.class)
					.setBiomeProvider(wc, bpNether);
			}
		} else if (env == Environment.THE_END) {
			final String cgEnd = config.get(MainConfig.CHUNK_GENERATOR_END);
			if (!cgEnd.isEmpty()) {
				wc.generator(cgEnd);
			}
			final String bpEnd = config.get(MainConfig.BIOME_PROVIDER_END);
			if (!bpEnd.isEmpty()) {
				UhcCore.getVersionAdapterLoader().getVersionAdapter(SetBiomeProviderAdapter.class)
					.setBiomeProvider(wc, bpEnd);
			}
		}
		wc.environment(env);

		if(useSeedList){
			if (mapSeed == -1) {
				Random r = new Random();
				mapSeed = seeds.get(r.nextInt(seeds.size()));
				LOGGER.info("Picking random seed from list : "+mapSeed);
			}
			wc.seed(mapSeed);
		}else if(copyMapFromList){
			if (mapName == null) {
				Random r = new Random();
				mapName = worlds.get(r.nextInt(worlds.size()));
			}

			String copyWorld = mapName;
			if (env != Environment.NORMAL){
				copyWorld = copyWorld + "_" + env.name().toLowerCase();
			}

			try {
				copyWorld(copyWorld, worldPath);
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Unable to copy world " + copyWorld, e);
			}
		}

		// Saved before the world exists too, so a crash during generation still
		// leaves enough behind for the next start to clean it up.
		worldNames.put(env, worldName);
		worldKeys.put(env, worldKey);
		worldFolders.remove(env);
		saveWorldStorage(env);

		wc.type(WorldType.NORMAL);
		World createdWorld = Bukkit.getServer().createWorld(wc);
		LOGGER.info("World seed: " + createdWorld.getSeed());

		if (!worldName.equals(createdWorld.getName())) {
			// Trust what Bukkit actually registered.
			LOGGER.warning("World " + worldName + " was registered as " + createdWorld.getName() + ", using that name");
			worldNames.put(env, createdWorld.getName());
		}

		File worldFolder = createdWorld.getWorldFolder();
		LOGGER.info("World folder: " + worldFolder.getPath());
		worldFolders.put(env, toStoragePath(worldFolder));
		saveWorldStorage(env);
	}

	private void saveWorldStorage(Environment env) {
		YamlFile storage;

		try{
			storage = FileUtils.saveResourceIfNotAvailable(UhcCore.getPlugin(), "storage.yml");
		}catch (IOException | InvalidConfigurationException ex){
			LOGGER.log(Level.WARNING, "Unable to load storage.yml", ex);
			return;
		}

		String key = env.name().toLowerCase(Locale.ROOT);
		storage.set("worlds." + key, worldNames.get(env));
		storage.set("world-keys." + key, worldKeys.get(env));
		storage.set("world-folders." + key, worldFolders.get(env));
		try {
			storage.save();
		}catch (IOException ex){
			LOGGER.log(Level.WARNING, "Unable to save storage.yml", ex);
		}
	}

	public void loadOldWorld(Environment env){
		String worldName = worldNames.get(env);

		if(!isUsableWorldName(worldName)){
			LOGGER.info("No world to load, defaulting to default behavior");
			this.createNewWorld(env);
		}else{
			final String key = worldKeys.get(env);
			final List<File> worldDirs = findWorldFolders(worldName, key, worldFolders.get(env));
			if(!worldDirs.isEmpty()){
				LOGGER.info("Loading existing world : " + worldDirs.get(0).getPath());
				// Same key as at creation, otherwise 26.x looks in another folder and generates a new world.
				WorldCreator creator = key == null
					? new WorldCreator(worldName)
					: VersionBridgeProvider.getBridge().createUhcWorldCreator(getKeyPath(key, worldName));
				World loadedWorld = Bukkit.getServer().createWorld(creator.environment(env));
				LOGGER.info("World seed: " + loadedWorld.getSeed());
				worldNames.put(env, loadedWorld.getName());
				worldFolders.put(env, toStoragePath(loadedWorld.getWorldFolder()));
			}else{
				this.createNewWorld(env);
			}
		}
	}

	public void loadWorldUuids(){
		YamlFile storage;

		try{
			storage = FileUtils.saveResourceIfNotAvailable(UhcCore.getPlugin(), "storage.yml");
		}catch (IOException | InvalidConfigurationException ex){
			LOGGER.log(Level.WARNING, "Unable to load storage.yml", ex);
			return;
		}

		worldNames.put(Environment.NORMAL, storage.getString("worlds.normal"));
		worldNames.put(Environment.NETHER, storage.getString("worlds.nether"));
		worldNames.put(Environment.THE_END, storage.getString("worlds.the_end"));
		worldKeys.put(Environment.NORMAL, storage.getString("world-keys.normal"));
		worldKeys.put(Environment.NETHER, storage.getString("world-keys.nether"));
		worldKeys.put(Environment.THE_END, storage.getString("world-keys.the_end"));
		worldFolders.put(Environment.NORMAL, storage.getString("world-folders.normal"));
		worldFolders.put(Environment.NETHER, storage.getString("world-folders.nether"));
		worldFolders.put(Environment.THE_END, storage.getString("world-folders.the_end"));
	}

	/**
	 * Every folder on disk that could hold the given UHC world: the path recorded in storage.yml,
	 * the Paper 26+ dimension folders (ours and the vanilla namespace) and the classic world
	 * container root.
	 */
	private List<File> findWorldFolders(String worldName, @Nullable String worldKey, @Nullable String storedFolder) {
		Set<Path> candidates = new LinkedHashSet<>();
		String worldPath = getKeyPath(worldKey, worldName);

		Path stored = resolveStoredFolder(storedFolder, worldName, worldPath);
		if (stored != null) {
			candidates.add(stored);
		}
		File uhcDimensions = getDimensionsFolder(VersionBridge.UHC_WORLD_NAMESPACE);
		if (uhcDimensions != null) {
			candidates.add(normalize(new File(uhcDimensions, worldPath)));
		}
		File minecraftDimensions = getDimensionsFolder(MINECRAFT_NAMESPACE);
		if (minecraftDimensions != null) {
			candidates.add(normalize(new File(minecraftDimensions, worldName)));
		}
		candidates.add(normalize(new File(Bukkit.getWorldContainer(), worldName)));

		Path container = normalize(Bukkit.getWorldContainer());
		File mainWorldFolder = getMainWorldFolder();
		Path mainWorld = mainWorldFolder == null ? null : normalize(mainWorldFolder);

		List<File> folders = new ArrayList<>();
		for (Path candidate : candidates) {
			if (candidate.equals(container) || candidate.equals(mainWorld)) {
				continue;
			}
			File folder = candidate.toFile();
			if (folder.isDirectory()) {
				folders.add(folder);
			}
		}
		return folders;
	}

	/**
	 * storage.yml is hand-editable, so the recorded path only counts when it sits inside the
	 * world container and is named after the world. Anything else gets ignored instead of
	 * recursively deleted.
	 */
	@Nullable
	private Path resolveStoredFolder(@Nullable String storedFolder, String worldName, String worldPath) {
		if (storedFolder == null || storedFolder.isEmpty() || storedFolder.equals("null")) {
			return null;
		}

		Path container = normalize(Bukkit.getWorldContainer());
		Path folder;
		try {
			Path stored = Paths.get(storedFolder);
			folder = (stored.isAbsolute() ? stored : container.resolve(stored)).normalize();
		} catch (RuntimeException ex) {
			LOGGER.warning("Ignoring invalid world folder in storage.yml: " + storedFolder);
			return null;
		}

		String folderName = folder.getFileName() == null ? null : folder.getFileName().toString();
		if (!folder.startsWith(container) || folderName == null
			|| !(folderName.equals(worldName) || folderName.equals(worldPath))) {
			LOGGER.warning("Ignoring world folder " + storedFolder + " from storage.yml, it doesn't belong to world " + worldName);
			return null;
		}
		return folder;
	}

	private String toStoragePath(File folder) {
		Path container = normalize(Bukkit.getWorldContainer());
		Path path = normalize(folder);
		if (path.startsWith(container)) {
			return container.relativize(path).toString().replace(File.separatorChar, '/');
		}
		return path.toString();
	}

	@Nullable
	private File getMainWorldFolder() {
		if (Bukkit.getWorlds().isEmpty()) {
			return null;
		}
		return Bukkit.getWorlds().get(0).getWorldFolder();
	}

	/**
	 * Folder that holds {@code dimensions/}. On Paper 26.x the main world's own folder is already
	 * {@code <level>/dimensions/minecraft/overworld}, so it has to be walked back up to the level.
	 */
	@Nullable
	private File getLevelFolder() {
		File mainWorldFolder = getMainWorldFolder();
		if (mainWorldFolder == null) {
			return null;
		}
		File namespaceFolder = mainWorldFolder.getParentFile();
		File dimensionsFolder = namespaceFolder == null ? null : namespaceFolder.getParentFile();
		if (dimensionsFolder != null && dimensionsFolder.getName().equals("dimensions") && dimensionsFolder.getParentFile() != null) {
			return dimensionsFolder.getParentFile();
		}
		return mainWorldFolder;
	}

	@Nullable
	private File getDimensionsFolder(String namespace) {
		File levelFolder = getLevelFolder();
		if (levelFolder == null) {
			return null;
		}
		return new File(new File(levelFolder, "dimensions"), namespace);
	}

	private boolean isMainWorld(World world) {
		return !Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).equals(world);
	}

	private Set<Path> getLoadedWorldFolders() {
		Set<Path> folders = new HashSet<>();
		for (World world : Bukkit.getWorlds()) {
			folders.add(normalize(world.getWorldFolder()));
		}
		return folders;
	}

	private static List<File> listSubdirectories(@Nullable File folder) {
		List<File> subdirectories = new ArrayList<>();
		File[] files = folder == null ? null : folder.listFiles();
		if (files == null) {
			return subdirectories;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				subdirectories.add(file);
			}
		}
		return subdirectories;
	}

	private static boolean isUsableWorldName(@Nullable String worldName) {
		return worldName != null
			&& !worldName.isEmpty()
			&& !worldName.equals("null")
			&& !worldName.equals(".")
			&& !worldName.equals("..")
			&& worldName.indexOf('/') == -1
			&& worldName.indexOf('\\') == -1;
	}

	/**
	 * Folder name under {@code dimensions/<namespace>/}. Falls back to the world name if a hand-edited
	 * storage.yml has a key we can't use.
	 */
	private static String getKeyPath(@Nullable String worldKey, String worldName) {
		if (worldKey == null) {
			return worldName;
		}
		String path = worldKey.substring(worldKey.indexOf(':') + 1);
		return isUsableWorldName(path) ? path : worldName;
	}

	private static boolean isUuid(String name) {
		try {
			return UUID.fromString(name).toString().equals(name.toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static boolean looksLikeWorld(File folder) {
		return new File(folder, "level.dat").isFile() || new File(folder, "region").isDirectory();
	}

	private static Path normalize(File file) {
		return file.toPath().toAbsolutePath().normalize();
	}

	/**
	 * @return the key the world was created under (e.g. {@code uhccore:<uuid>}), or null for a plain-named one
	 */
	@Nullable
	public String getUhcWorldKey(Environment environment) {
		return worldKeys.get(environment);
	}

	/**
	 * Bukkit name of the UHC world matching the given environment, e.g. {@code uhccore_<uuid>} on Paper 26.x.
	 * @param environment The environment of the world name you want to obtain.
	 * @return Returns the UHC world name matching the environment or null if it doesn't exist.
	 */
	@Nullable
	public String getUhcWorldUuid(Environment environment){
		Validate.notNull(environment);
		return worldNames.get(environment);
	}

	/**
	 * Used to obtain the UHC world matching the given environment.
	 * @param environment The environment of the world you want to obtain.
	 * @return Returns the UHC world matching the environment or null if it doesn't exist.
	 */
	@Nullable
	public World getUhcWorld(Environment environment){
		Validate.notNull(environment);

		String worldName = worldNames.get(environment);
		if (worldName == null){
			return null;
		}

		return Bukkit.getWorld(worldName);
	}

	public void setWorldsStartGame() {
		World overworld = getUhcWorld(Environment.NORMAL);
		VersionUtils.getVersionUtils().setGameRuleValue(overworld, DO_MOB_SPAWNING, true);

		if(config.get(MainConfig.ENABLE_DAY_NIGHT_CYCLE)) {
			VersionUtils.getVersionUtils().setGameRuleValue(overworld, DO_DAYLIGHT_CYCLE, true);
			overworld.setTime(0);
		}

		if (!config.get(MainConfig.LOBBY_IN_DEFAULT_WORLD)) {
			lobby.destroyBoundingBox();
		}

		if(config.get(MainConfig.BORDER_IS_MOVING)){
			int borderEndApothem = config.get(MainConfig.BORDER_END_SIZE);
			int borderEndSideLength = 2 * borderEndApothem;
			int timeToShrink = config.get(MainConfig.BORDER_TIME_TO_SHRINK);
			int timeBeforeShrink = config.get(MainConfig.BORDER_TIME_BEFORE_SHRINK);

			Bukkit.getScheduler().runTask(UhcCore.getPlugin(), new WorldBorderTask(timeBeforeShrink, borderEndSideLength, timeToShrink));
		}
	}

	public void prepareWorlds() {
		Difficulty difficulty = config.get(MainConfig.GAME_DIFFICULTY);
		boolean healthRegen = config.get(MainConfig.ENABLE_HEALTH_REGEN);
		boolean locatorBar = config.get(MainConfig.ENABLE_LOCATOR_BAR);
		boolean announceAdvancements = config.get(MainConfig.ANNOUNCE_ADVANCEMENTS);
		int borderStartApothem = config.get(MainConfig.BORDER_START_SIZE);
		int borderStartSideLength = 2 * borderStartApothem;

		World overworld = getUhcWorld(Environment.NORMAL);
		prepareWorld(overworld, difficulty, healthRegen, locatorBar, announceAdvancements, borderStartSideLength);

		VersionUtils.getVersionUtils().setGameRuleValue(overworld, DO_DAYLIGHT_CYCLE, false);
		VersionUtils.getVersionUtils().setGameRuleValue(overworld, DO_MOB_SPAWNING, false);

		overworld.setTime(6000);
		overworld.setWeatherDuration(999999999);

		if (config.get(MainConfig.ENABLE_NETHER)){
			World nether = getUhcWorld(Environment.NETHER);
			prepareWorld(nether, difficulty, healthRegen, locatorBar, announceAdvancements, borderStartSideLength / config.get(MainConfig.NETHER_SCALE));
		}

		if (config.get(MainConfig.ENABLE_THE_END)){
			World theEnd = getUhcWorld(Environment.THE_END);
			prepareWorld(theEnd, difficulty, healthRegen, locatorBar, announceAdvancements, borderStartSideLength);
		}

		if (config.get(MainConfig.LOBBY_IN_DEFAULT_WORLD)){
			final World defaultWorld = Bukkit.getWorlds().get(0);
			final Location spawnLocation = config.get(MainConfig.USE_DEFAULT_WORLD_SPAWN_FOR_LOBBY)
				? defaultWorld.getSpawnLocation().clone().add(0.5, 0, 0.5)
				: new Location(defaultWorld, 0.5, 100, 0.5);
			lobby = new Lobby(spawnLocation);
		}else {
			lobby = new Lobby(new Location(overworld, 0.5, 200, 0.5));
			lobby.build();
		}

		arena = new DeathmatchArena(new Location(overworld, 10000, config.get(MainConfig.ARENA_PASTE_AT_Y), 10000));
		arena.build();

		if (config.get(MainConfig.ENABLE_UNDERGROUND_NETHER)) {
			UndergroundNether undergoundNether = new UndergroundNether();
			undergoundNether.build(config, getUhcWorld(Environment.NORMAL));
		}
	}

	private void prepareWorld(World world, Difficulty difficulty, boolean healthRegen, boolean locatorBar, boolean announceAdvancements, double borderSideLength) {
		world.save();
		if (!healthRegen){
			VersionUtils.getVersionUtils().setGameRuleValue(world, NATURAL_REGENERATION, false);
		}
		if (!locatorBar) {
			setGameRuleIfAvailable(world, LOCATOR_BAR, false);
		}
		if (VersionUtils.isAtLeast(20, 5)) {
			int spawnChunkRadius = config.get(MainConfig.SPAWN_CHUNK_RADIUS);
			setGameRuleIfAvailable(world, SPAWN_CHUNK_RADIUS, spawnChunkRadius);
		}
		if (!announceAdvancements && VersionUtils.isAtLeast(12)){
			setGameRuleIfAvailable(world, ANNOUNCE_ADVANCEMENTS, false);
		}
		VersionUtils.getVersionUtils().setGameRuleValue(world, COMMAND_BLOCK_OUTPUT, false);
		VersionUtils.getVersionUtils().setGameRuleValue(world, LOG_ADMIN_COMMANDS, false);
		world.setDifficulty(difficulty);

		setWorldBorder(world, 0, 0, borderSideLength);
	}

	private void setGameRuleIfAvailable(World world, String gameRuleName, Object value) {
		// GameRule is 1.13+, so on 1.12 and older touching it throws NoClassDefFoundError
		// and the rule got silently skipped. Those versions only speak the string API.
		if (!VersionUtils.isAtLeast(13)) {
			if (world.isGameRule(gameRuleName)) {
				VersionUtils.getVersionUtils().setGameRuleValue(world, gameRuleName, value);
			}
			return;
		}
		try {
			GameRule<?> rule = GameRule.getByName(gameRuleName);
			if (rule != null) {
				VersionUtils.getVersionUtils().setGameRuleValue(world, gameRuleName, value);
			}
		} catch (Throwable ignored) {
			// Game rule not available on this server version
		}
	}

	public void setWorldBorder(World world, int x, int z, double sideLength) {
		WorldBorder worldborder = world.getWorldBorder();
		worldborder.setCenter(x, z);
		worldborder.setSize(sideLength);
	}

	private void copyWorld(String sourceName, String destinationName) throws IOException {
		final Path worldsDir = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
		final Path sourceDir = worldsDir.resolve(sourceName);
		final Path destinationDir = worldsDir.resolve(destinationName);

		if (!Files.isDirectory(sourceDir)) {
			LOGGER.warning("Unable to copy world " + sourceName + " as it does not exist");
			return;
		}

		// In case the source directory is a symbolic link
		final Path realSourceDir = sourceDir.toRealPath();

		LOGGER.info("Copying " + sourceName + " to " + destinationName);
		Files.walkFileTree(realSourceDir, new CopyWorldFileVisitor(realSourceDir, destinationDir));
	}

	private void deleteOldPlayersFiles() {
		if (Bukkit.getServer().getWorlds().isEmpty()) {
			return;
		}

		final File mainWorldFolder = Bukkit.getWorlds().get(0).getWorldFolder();

		// Deleting old players files
		final File playerdata = new File(mainWorldFolder, "playerdata");
		if(playerdata.exists() && playerdata.isDirectory()){
			for(File playerFile : playerdata.listFiles()){
				playerFile.delete();
			}
		}

		// Deleting old players stats
		final File stats = new File(mainWorldFolder, "stats");
		if(stats.exists() && stats.isDirectory()){
			for(File statFile : stats.listFiles()){
				statFile.delete();
			}
		}

		// Deleting old players advancements
		final File advancements = new File(mainWorldFolder, "advancements");
		if(advancements.exists() && advancements.isDirectory()){
			for(File advancementFile : advancements.listFiles()){
				advancementFile.delete();
			}
		}
	}

	public void generateChunks(Environment env){
		World world = getUhcWorld(env);
		int size = config.get(MainConfig.BORDER_START_SIZE);
		double netherScale = config.get(MainConfig.NETHER_SCALE);

		if(env == Environment.NETHER){
			size = (int) (size / netherScale);
		}

		int restEveryNumOfChunks = config.get(MainConfig.REST_EVERY_NUM_OF_CHUNKS);
		int restDuration = config.get(MainConfig.REST_DURATION);

		ChunkLoaderTask chunkLoaderTask = new ChunkLoaderTask(world, size, restEveryNumOfChunks, restDuration, preGenerationProgress) {
			@Override
			public void onDoneLoadingWorld() {
				LOGGER.info("Environment "+env.toString()+" 100% loaded");
				if(env.equals(Environment.NORMAL) && config.get(MainConfig.ENABLE_NETHER)) {
					generateChunks(Environment.NETHER);
				}else {
					GameManager.getGameManager().startWaitingPlayers();
				}
			}

			@Override
			public void onDoneLoadingChunk(Chunk chunk) {}
		};

		chunkLoaderTask.printSettings();

		if (ChunkLoaderTask.canRunOffMainThread()){
			Bukkit.getScheduler().runTaskAsynchronously(UhcCore.getPlugin(), chunkLoaderTask);
		}else {
			Bukkit.getScheduler().runTask(UhcCore.getPlugin(), chunkLoaderTask);
		}
	}

}
