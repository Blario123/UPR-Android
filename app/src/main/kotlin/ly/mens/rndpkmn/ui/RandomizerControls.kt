package ly.mens.rndpkmn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dabomstew.pkrandom.MiscTweak
import com.dabomstew.pkrandom.Settings
import ly.mens.rndpkmn.R
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.settings.SettingsCategory
import ly.mens.rndpkmn.settings.SettingsPrefix
import ly.mens.rndpkmn.toast
import java.lang.reflect.Field

@Composable
fun TweaksList() {
	val ctx = LocalContext.current
	LazyColumn {
		item {
			Text(stringResource(R.string.title_misc), style = MaterialTheme.typography.h6)
		}
		items(MiscTweak.allTweaks.filter {
			val available = RandomizerSettings.handler?.miscTweaksAvailable()
			available != null && available and it.value != 0
		}) { tweak ->
			var checked by rememberSaveable { mutableStateOf((RandomizerSettings.currentMiscTweaks and tweak.value) == tweak.value) }
			Row(verticalAlignment = Alignment.CenterVertically) {
				Checkbox(checked, {
					checked = it
					RandomizerSettings.currentMiscTweaks = RandomizerSettings.currentMiscTweaks xor tweak.value
				})
				Text(tweak.tweakName, Modifier.clickable {
					ctx.toast(tweak.tooltipText)
				})
			}
		}
	}
}

@Composable
fun SettingsList(category: SettingsCategory) {
	val ctx = LocalContext.current
	LazyColumn {
		category.prefixes.filter { p ->
			RandomizerSettings.handler?.let { p.isSupported(it) } ?: false
		}.forEach { prefix ->
			item {
				Text(stringResource(prefix.title), style = MaterialTheme.typography.h6)
			}
			items(prefix.groups.toList()) { (subtitle, group) ->
				SettingsGroup(prefix, subtitle, group)
			}
			item {
				Divider()
			}
			items(prefix.props.toList()) { (label, field) ->
				if (RandomizerSettings.currentGen in (RandomizerSettings.generations[field]
								?: 1..7)) {
					field.SettingsComponent(prefix.strings[label] ?: label) {
						val id = ctx.resources.getIdentifier(label.replace(".text", "_toolTipText").substring(4), "string", ctx.packageName)
						if (id != 0) {
							ctx.toast(id)
						}
					}
				}
			}
		}
	}
}

@Composable
fun SettingsGroup(prefix: SettingsPrefix, subtitle: String, group: MutableMap<String, Field>) {
	Column {
		if (subtitle.isNotEmpty()) {
			Text(prefix.strings[subtitle]!!, fontSize = 16.sp, fontWeight = FontWeight.Bold)
		}
		val groupField = group.values.first()
		val groupEnum = groupField.get(RandomizerSettings) as Enum<*>
		val selectedIndex = rememberSaveable { mutableStateOf(groupEnum.ordinal) }
		val ctx = LocalContext.current
		group.keys.forEachIndexed { index, label ->
			groupField.SettingsComponent(prefix.strings[label] ?: label, index, selectedIndex) {
				val id = ctx.resources.getIdentifier(label.replace(".text", "_toolTipText").substring(4), "string", ctx.packageName)
				if (id != 0) {
					ctx.toast(id)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Field.SettingsComponent(label: String, index: Int = -1, selectedIndex: MutableState<Int>? = null, onClick: () -> Unit) {
	if (type.isPrimitive) {
		when (type.name) {
			"boolean" -> {
				var checked by rememberSaveable { mutableStateOf(getBoolean(RandomizerSettings)) }
				Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						setBoolean(RandomizerSettings, it)
					})
					Text(label, Modifier.weight(1f).clickable(onClick = onClick))
					if (this@SettingsComponent in RandomizerSettings.selections && checked) {
						val toggle = RandomizerSettings.toggles[this@SettingsComponent]
						val options = RandomizerSettings.selections[this@SettingsComponent]
								?: emptyList()
						var selected by rememberSaveable { mutableStateOf(toggle?.get(RandomizerSettings).toString()) }
						var expanded by remember { mutableStateOf(false) }
						val width = (options.maxOf { it.toString().length } * MaterialTheme.typography.body2.fontSize.value).coerceAtLeast(48f)
						ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
							TextField(selected, {}, Modifier.width(width.dp).offset(x = 2.dp), readOnly = true)
							ExposedDropdownMenu(expanded, { expanded = false }) {
								options.forEach { option ->
									DropdownMenuItem({
										selected = option.toString()
										toggle?.set(RandomizerSettings, option)
										expanded = false
									}) { Text(option.toString()) }
								}
							}
						}
					}
				}
			}
			"int" -> {
				val limit = RandomizerSettings.limits[this] ?: 0f..1f
				val length = (limit.endInclusive - limit.start).toInt()
				val toggle = RandomizerSettings.toggles[this]
				//toggle?.isAccessible = true
				var position by rememberSaveable { mutableStateOf(getInt(RandomizerSettings).toFloat()) }
				var checked by rememberSaveable {
					mutableStateOf(toggle?.getBoolean(RandomizerSettings) ?: (position > 0f))
				}
				Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						toggle?.setBoolean(RandomizerSettings, it)
					})
					Text(String.format(label, position.toInt()), Modifier.clickable(onClick = onClick))
					if (length > 10) {
						val numPattern = Regex("^-?\\d+$")
						var input by rememberSaveable { mutableStateOf(position.toString().substringBefore('.')) }
						val keyCon = LocalSoftwareKeyboardController.current
						TextField(input, {
							input = it
							position = numPattern.matchEntire(it)?.run {
								val num = value.toFloat().coerceIn(limit)
								setInt(RandomizerSettings, num.toInt())
								num
							} ?: position
						}, Modifier.width(64.dp).offset(x = 2.dp), checked,
								keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
								keyboardActions = KeyboardActions { keyCon?.hide() })
					}
				}
				if (length <= 10) {
					Slider(position, { position = it }, Modifier.fillMaxWidth(), checked, limit, length - 1, { setInt(RandomizerSettings, position.toInt()) })
				}
			}
		}
	} else if (type.isEnum) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			RadioButton(index == selectedIndex!!.value, {
				selectedIndex.value = index
				set(RandomizerSettings, type.enumConstants[index])
			})
			Text(label, Modifier.clickable(onClick = onClick))
		}
		if (get(RandomizerSettings) === Settings.StartersMod.CUSTOM && index == Settings.StartersMod.CUSTOM.ordinal) {
			var (first, second, third) = RandomizerSettings.currentStarters
			val firstName = rememberSaveable {
				mutableStateOf(first?.name ?: "")
			}; SearchField(firstName)
			val secondName = rememberSaveable {
				mutableStateOf(second?.name ?: "")
			}; SearchField(secondName)
			val thirdName = rememberSaveable {
				mutableStateOf(third?.name ?: "")
			}; SearchField(thirdName)
			Button({
				first = RandomizerSettings.getPokemon(firstName.value)
				second = RandomizerSettings.getPokemon(secondName.value)
				third = RandomizerSettings.getPokemon(thirdName.value)
				RandomizerSettings.currentStarters = Triple(first, second, third)
			}) { Text(stringResource(R.string.action_save_starters)) }
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchField(search: MutableState<String>) {
	var expanded by remember { mutableStateOf(false) }
	var useRandom by rememberSaveable { mutableStateOf(search.value.isBlank()) }
	val options = RandomizerSettings.pokeTrie[search.value.uppercase()]?.children() ?: emptyList()
	val keyCon = LocalSoftwareKeyboardController.current
	Row(verticalAlignment = Alignment.CenterVertically) {
		ExposedDropdownMenuBox(expanded, { expanded = !expanded }, Modifier.weight(1f)) {
			TextField(search.value, { search.value = it }, Modifier.padding(vertical = 2.dp), !useRandom, singleLine = true)
			ExposedDropdownMenu(expanded && !useRandom, { expanded = false }) {
				options.forEach { option ->
					DropdownMenuItem({
						expanded = false
						search.value = option
						keyCon?.hide()
						//TODO update cursor position
					}) { Text(option) }
				}
			}
		}
		Checkbox(useRandom, {
			useRandom = it
			keyCon?.hide()
			search.value = ""
		})
		Text(stringResource(R.string.random_starter), Modifier.weight(1f))
	}
}
