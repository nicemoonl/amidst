package amidst.mojangapi.world;

import java.io.IOException;
import java.util.List;

import amidst.documentation.Immutable;
import amidst.mojangapi.file.MojangApiParsingException;
import amidst.mojangapi.file.directory.SaveDirectory;
import amidst.mojangapi.file.nbt.LevelDatNbt;
import amidst.mojangapi.minecraftinterface.MinecraftInterface;
import amidst.mojangapi.minecraftinterface.MinecraftInterfaceException;
import amidst.mojangapi.world.coordinates.Resolution;
import amidst.mojangapi.world.icon.locationchecker.EndCityLocationChecker;
import amidst.mojangapi.world.icon.locationchecker.NetherFortressAlgorithm;
import amidst.mojangapi.world.icon.locationchecker.OceanMonumentLocationChecker;
import amidst.mojangapi.world.icon.locationchecker.TempleLocationChecker;
import amidst.mojangapi.world.icon.locationchecker.VillageLocationChecker;
import amidst.mojangapi.world.icon.producer.PlayerProducer;
import amidst.mojangapi.world.icon.producer.SpawnProducer;
import amidst.mojangapi.world.icon.producer.StrongholdProducer;
import amidst.mojangapi.world.icon.producer.StructureProducer;
import amidst.mojangapi.world.icon.type.DefaultWorldIconTypes;
import amidst.mojangapi.world.icon.type.EndCityWorldIconTypeProvider;
import amidst.mojangapi.world.icon.type.ImmutableWorldIconTypeProvider;
import amidst.mojangapi.world.icon.type.TempleWorldIconTypeProvider;
import amidst.mojangapi.world.oracle.BiomeDataOracle;
import amidst.mojangapi.world.oracle.EndIsland;
import amidst.mojangapi.world.oracle.EndIslandOracle;
import amidst.mojangapi.world.oracle.SlimeChunkOracle;
import amidst.mojangapi.world.player.MovablePlayerList;
import amidst.mojangapi.world.player.PlayerInformationCache;
import amidst.mojangapi.world.player.WorldPlayerType;
import amidst.mojangapi.world.versionfeatures.DefaultVersionFeatures;
import amidst.mojangapi.world.versionfeatures.VersionFeatures;

@Immutable
public class WorldBuilder {
	private final PlayerInformationCache playerInformationCache;
	private final SeedHistoryLogger seedHistoryLogger;

	public WorldBuilder(PlayerInformationCache playerInformationCache,
			SeedHistoryLogger seedHistoryLogger) {
		this.playerInformationCache = playerInformationCache;
		this.seedHistoryLogger = seedHistoryLogger;
	}

	public World fromSeed(MinecraftInterface minecraftInterface,
			WorldSeed worldSeed, WorldType worldType)
			throws MinecraftInterfaceException {
		return create(minecraftInterface, worldSeed, worldType, "",
				MovablePlayerList.dummy(),
				DefaultVersionFeatures.create(minecraftInterface
						.getRecognisedVersion()));
	}

	public World fromFile(MinecraftInterface minecraftInterface,
			SaveDirectory saveDirectory) throws IOException,
			MinecraftInterfaceException, MojangApiParsingException {
		VersionFeatures versionFeatures = DefaultVersionFeatures
				.create(minecraftInterface.getRecognisedVersion());
		LevelDatNbt levelDat = saveDirectory.createLevelDat();
		MovablePlayerList movablePlayerList = new MovablePlayerList(
				playerInformationCache, saveDirectory,
				versionFeatures.isSaveEnabled(), WorldPlayerType.from(
						saveDirectory, levelDat));
		return create(minecraftInterface,
				WorldSeed.fromFile(levelDat.getSeed()),
				levelDat.getWorldType(), levelDat.getGeneratorOptions(),
				movablePlayerList, versionFeatures);
	}

	private World create(MinecraftInterface minecraftInterface,
			WorldSeed worldSeed, WorldType worldType, String generatorOptions,
			MovablePlayerList movablePlayerList, VersionFeatures versionFeatures)
			throws MinecraftInterfaceException {
		seedHistoryLogger.log(worldSeed);
		long seed = worldSeed.getLong();
		// @formatter:off
		minecraftInterface.createWorld(seed, worldType, generatorOptions);
		BiomeDataOracle biomeDataOracle = new BiomeDataOracle(minecraftInterface);
		return new World(
				worldSeed,
				worldType,
				generatorOptions,
				movablePlayerList,
				versionFeatures,
				biomeDataOracle,
				EndIslandOracle.from(  seed),
				new SlimeChunkOracle(  seed),
				new SpawnProducer(     seed, biomeDataOracle, versionFeatures.getValidBiomesForStructure_Spawn()),
				new StrongholdProducer(seed, biomeDataOracle, versionFeatures.getValidBiomesAtMiddleOfChunk_Stronghold(), versionFeatures.getNumberOfStrongholds()),
				new PlayerProducer(movablePlayerList),
				new StructureProducer<Void>(
						Resolution.CHUNK,
						4,
						new VillageLocationChecker(seed, biomeDataOracle, versionFeatures.getValidBiomesForStructure_Village()),
						new ImmutableWorldIconTypeProvider(DefaultWorldIconTypes.VILLAGE),
						Dimension.OVERWORLD
				), new StructureProducer<Void>(
						Resolution.CHUNK,
						8,
						new TempleLocationChecker(seed, biomeDataOracle, versionFeatures.getValidBiomesAtMiddleOfChunk_Temple()),
						new TempleWorldIconTypeProvider(biomeDataOracle),
						Dimension.OVERWORLD
				), new StructureProducer<Void>(
						Resolution.CHUNK,
						8,
						versionFeatures.getMineshaftAlgorithmFactory().apply(seed),
						new ImmutableWorldIconTypeProvider(DefaultWorldIconTypes.MINESHAFT),
						Dimension.OVERWORLD
				), new StructureProducer<Void>(
						Resolution.CHUNK,
						8,
						new OceanMonumentLocationChecker(seed, biomeDataOracle, versionFeatures.getValidBiomesAtMiddleOfChunk_OceanMonument(), versionFeatures.getValidBiomesForStructure_OceanMonument()),
						new ImmutableWorldIconTypeProvider(DefaultWorldIconTypes.OCEAN_MONUMENT),
						Dimension.OVERWORLD
				), new StructureProducer<Void>(
						Resolution.NETHER_CHUNK,
						88,
						new NetherFortressAlgorithm(seed),
						new ImmutableWorldIconTypeProvider(DefaultWorldIconTypes.NETHER_FORTRESS),
						Dimension.NETHER
				), new StructureProducer<List<EndIsland>>(
						Resolution.CHUNK,
						8,
						new EndCityLocationChecker(seed),
						new EndCityWorldIconTypeProvider(),
						Dimension.END
				)
		);
		// @formatter:on
	}
}