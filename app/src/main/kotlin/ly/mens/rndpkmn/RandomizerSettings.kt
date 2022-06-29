package ly.mens.rndpkmn

import android.util.Log
import com.dabomstew.pkrandom.*
import com.dabomstew.pkrandom.constants.GlobalConstants
import com.dabomstew.pkrandom.pokemon.ExpCurve
import com.dabomstew.pkrandom.pokemon.GenRestrictions
import com.dabomstew.pkrandom.pokemon.Pokemon
import com.dabomstew.pkrandom.romhandlers.*
import java.io.*
import java.lang.NumberFormatException
import java.lang.reflect.Field
import java.util.ResourceBundle.getBundle
import kotlin.properties.Delegates
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

object RandomizerSettings : Settings() {
	private val romHandlerFactories = listOf(
			Gen1RomHandler.Factory(),
			Gen2RomHandler.Factory(),
			Gen3RomHandler.Factory(),
			Gen4RomHandler.Factory(),
			Gen5RomHandler.Factory()
	)
	private lateinit var romHandlerFactory: RomHandler.Factory
	private lateinit var romHandler: RomHandler
	private lateinit var inputFile: File
	//allocate enough space to accommodate large logs
	val outputLog = ByteArrayOutputStream(1024 * 1024)
	private val emptyLog = object : OutputStream() {
		override fun write(b: Int) {
			return
		}
	}
	//limit the number of ROMs based on amount of available memory
	val romLimit: Int get() {
		val rt = Runtime.getRuntime()
		val mem = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
		return (mem / inputFile.length() / 3).toInt()
	}
	val currentGen: Int get() = if (this::romHandler.isInitialized) romHandler.generationOfPokemon() else 1
	private var _currentStarters: Triple<Pokemon, Pokemon, Pokemon>? = null
	var currentStarters: Triple<Pokemon, Pokemon, Pokemon>
		set(value) {
			customStarters = intArrayOf(value.first.number, value.second.number, value.third.number)
			_currentStarters = value
		}
		get() {
			val poke = Pokemon().apply {
				name = ""
			}
			return _currentStarters ?: Triple(poke, poke, poke)
		}
	val pokeTrie = Trie()
	var currentSeed by Delegates.notNull<Long>()
	lateinit var romFileName: String
	val versionString: String get() = "$VERSION$this"
	val limits: Map<Field?, ClosedFloatingPointRange<Float>> = mapOf(
			this::staticLevelModifier.javaField to -50f..50f,
			this::guaranteedMoveCount.javaField to 2f..4f,
			this::movesetsGoodDamagingPercent.javaField to 0f..100f,
			this::trainersForceFullyEvolvedLevel.javaField to 30f..65f,
			this::trainersLevelModifier.javaField to -50f..50f,
			this::totemLevelModifier.javaField to -50f..50f,
			this::minimumCatchRateLevel.javaField to 1f..4f,
			this::wildLevelModifier.javaField to -50f..50f,
			this::tmsGoodDamagingPercent.javaField to 0f..100f,
			this::tutorsGoodDamagingPercent.javaField to 0f..100f,
			this::additionalBossTrainerPokemon.javaField to 0f..5f,
			this::additionalImportantTrainerPokemon.javaField to 0f..5f,
			this::additionalRegularTrainerPokemon.javaField to 0f..5f,
			this::eliteFourUniquePokemonNumber.javaField to 0f..2f,
	)
	val toggles: Map<Field?, Field?> = mapOf(
			this::staticLevelModifier.javaField to this::staticLevelModified.javaField,
			this::guaranteedMoveCount.javaField to this::startWithGuaranteedMoves.javaField,
			this::movesetsGoodDamagingPercent.javaField to this::movesetsForceGoodDamaging.javaField,
			this::trainersForceFullyEvolvedLevel.javaField to this::trainersForceFullyEvolved.javaField,
			this::trainersLevelModifier.javaField to this::trainersLevelModified.javaField,
			this::totemLevelModifier.javaField to this::totemLevelsModified.javaField,
			this::minimumCatchRateLevel.javaField to this::useMinimumCatchRate.javaField,
			this::wildLevelModifier.javaField to this::wildLevelsModified.javaField,
			this::tmsGoodDamagingPercent.javaField to this::tmsForceGoodDamaging.javaField,
			this::tutorsGoodDamagingPercent.javaField to this::tutorsForceGoodDamaging.javaField,
			this::additionalBossTrainerPokemon.javaField to null,
			this::additionalImportantTrainerPokemon.javaField to null,
			this::additionalRegularTrainerPokemon.javaField to null,
			this::eliteFourUniquePokemonNumber.javaField to null,
			this::updateBaseStats.javaField to this::updateBaseStatsToGeneration.javaField,
			this::standardizeEXPCurves.javaField to this::selectedEXPCurve.javaField,
			this::updateMoves.javaField to this::updateMovesToGeneration.javaField,
	)
	val selections: MutableMap<Field?, List<Any>> = mutableMapOf()
	val nameLists: List<Pair<String, MutableList<String>>>

	private const val TAG = "Settings"

	init {
		this::class.memberProperties.forEach { prop ->
			val fld = prop.javaField
			if (fld != null) {
				val type = fld.type
				fld.isAccessible = true
				if (type.isPrimitive) {
					val suffix = when (type.name) {
						"boolean" -> "CheckBox"
						"int" -> "Slider"
						else -> ""
					}
					for (pre in SettingsPrefix.values()) {
						if (pre.search(fld, suffix)) break
					}
				} else if (type.enclosingClass == SettingsMod::class.java) {
					val prefix = type.getField("PREFIX").get(null) as String
					type.enumConstants.forEach {
						for (pre in SettingsPrefix.values()) {
							if (prefix == pre.prefix && pre.searchEnum(fld, it as Enum<*>)) break
						}
					}
				}
			}
		}
		customNames = try {
			FileFunctions.getCustomNames()
		} catch (e: IOException) {
			Log.e(TAG, "Unable to load custom names.", e)
			CustomNamesSet()
		}
		nameLists = customNames::class.memberProperties.reversed().associate {
			it.isAccessible = true
			val title = SettingsPrefix.customNamesTitle(it.name) ?: it.name
			@Suppress("UNCHECKED_CAST")
			val names = it.javaField?.get(customNames) as? MutableList<String> ?: mutableListOf()
			title to names
		}.toList()
		currentRestrictions = GenRestrictions()
	}

	fun loadRom(file: File): Boolean {
		inputFile = file
		currentSeed = RandomSource.pickSeed()
		romFileName = Triple(file.nameWithoutExtension.substringAfter(':'), currentSeed.toString(16), file.extension).fileName
		try {
			romHandlerFactory = romHandlerFactories.first { it.isLoadable(file.absolutePath) }
			romHandler = createRomHandler()
			romName = romHandler.romName
		} catch (e: Exception) {
			Log.e(TAG, "${file.name} cannot be loaded.", e)
			return false
		}

		val (first, second, third) = romHandler.starters
		currentStarters = Triple(first, second, third)
		pokeTrie.clear()
		for (i in 1 until romHandler.pokemon.size) {
			pokeTrie.insert(romHandler.pokemon[i].name)
		}

		val baseStatGenerationNumbers = arrayOfNulls<Int>(Math.min(3, GlobalConstants.HIGHEST_POKEMON_GEN - currentGen))
		var j = Math.max(6, currentGen + 1)
		updateBaseStatsToGeneration = j
		for (i in baseStatGenerationNumbers.indices) {
			baseStatGenerationNumbers[i] = j++
		}
		selections[this::updateBaseStats.javaField] = baseStatGenerationNumbers.filterNotNull()

		val moveGenerationNumbers = arrayOfNulls<Int>(GlobalConstants.HIGHEST_POKEMON_GEN - currentGen)
		j = currentGen + 1
		updateMovesToGeneration = j
		for (i in moveGenerationNumbers.indices) {
			moveGenerationNumbers[i] = j++
		}
		selections[this::updateMoves.javaField] = moveGenerationNumbers.filterNotNull()

		val expCurves = if (currentGen < 3) {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW)
		} else {
			arrayOf(ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW, ExpCurve.ERRATIC, ExpCurve.FLUCTUATING)
		}
		selections[this::standardizeEXPCurves.javaField] = expCurves.toList()

		return true
	}

	fun saveRom(file: File): Boolean {
		outputLog.reset()
		return saveRom(file, currentSeed, romHandler, PrintStream(outputLog))
	}

	fun saveRom(file: File, seed: Long, log: OutputStream? = null): Boolean {
		val handler = try {
			createRomHandler()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to create ROM handler.", e)
			return false
		}
		return saveRom(file, seed, handler, PrintStream(log ?: emptyLog))
	}

	private fun saveRom(file: File, seed: Long, handler: RomHandler, log: PrintStream): Boolean {
		return try {
			handler.setLog(log)
			Randomizer(this, handler, getBundle("com.dabomstew.pkrandom.newgui.Bundle"), false).randomize(file.absolutePath, log, seed)
			true
		} catch (e: Exception) {
			Log.e(TAG, "Failed to randomize ROM.", e)
			false
		} finally {
			log.close()
		}
	}

	private fun createRomHandler(): RomHandler {
		val romHandler = romHandlerFactory.create(random)
		romHandler.loadRom(inputFile.absolutePath)
		if (!romHandler.isRomValid) {
			Log.i(TAG, "The loaded ROM is not valid.")
		}
		return romHandler
	}

	fun reloadRomHandler() {
		romHandler = try {
			createRomHandler()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to create new ROM handler.", e)
			romHandler
		}
	}

	fun getPokemon(name: String): Pokemon? {
		for (i in 1 until romHandler.pokemon.size) {
			if (romHandler.pokemon[i].name.equals(name, true)) return romHandler.pokemon[i]
		}
		return null
	}

	fun updateFromString(text: String): Boolean {
		val other: Settings
		try {
			val version = text.substring(0..2).toInt()
			var config = text.substring(3)
			if (version < VERSION) {
				config = SettingsUpdater().update(version, config)
			} else if (version > VERSION) {
				throw Exception("Settings string created by newer version.")
			}
			other = fromString(config)
		} catch (e: Exception) {
			Log.e(TAG, "Failed to load settings string.", e)
			return false
		}
		if (romName != other.romName) {
			Log.i(TAG, "Settings string created for ${other.romName} but $romName is loaded.")
		}
		other.javaClass.declaredFields.forEach { fld ->
			fld.isAccessible = true
			//don't copy null values
			fld.get(other)?.let { fld.set(this, it) }
		}
		return true
	}

	fun updateSeed(text: String, base: Int): Boolean {
		return try {
			currentSeed = text.toLong(base)
			true
		} catch (e: NumberFormatException) {
			Log.e(TAG, "Unable to convert seed to number.", e)
			false
		}
	}
}