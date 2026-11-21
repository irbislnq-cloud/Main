package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChronicleEntry
import com.example.data.model.CombatState
import com.example.data.model.Combatant
import com.example.data.model.LoreEntry
import com.example.data.model.NpcDossier
import com.example.data.model.PlayerProfile
import com.example.data.model.QuestItem
import com.example.data.model.RpgState
import com.example.data.model.WorldEnvironment
import com.example.data.model.WorldLocationNode
import com.example.ui.components.LocationGraphCanvas
import com.example.ui.components.NpcDossierDialog
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorldAndCharacterStudio(
    initialState: RpgState,
    loreEntries: List<LoreEntry> = emptyList(),
    memorySummary: String = "",
    initialTab: Int = 0,
    focusedEntityId: String? = null,
    onDismiss: () -> Unit,
    onSave: (RpgState) -> Unit,
    onMoveToLocation: (String) -> Unit = {},
    onUpdateCombat: (CombatState) -> Unit = {},
    onRollTacticalD20: (statName: String, modifier: Long, purpose: String) -> Unit = { _, _, _ -> },
    onRollNpcPerception: (npcId: String) -> Unit = {},
    onBuyItemFromNpc: (npcId: String, itemName: String, priceGold: Long) -> Unit = { _, _, _ -> },
    onGenerateLocationBackdrop: (locId: String) -> Unit = {}
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }

    var enabled by remember { mutableStateOf(initialState.enabled) }
    var level by remember { mutableStateOf(initialState.level.coerceAtLeast(1L)) }
    var hp by remember { mutableStateOf(initialState.hp.coerceAtLeast(0L)) }
    var maxHp by remember { mutableStateOf(initialState.maxHp.coerceAtLeast(1L)) }
    var mp by remember { mutableStateOf(initialState.mp.coerceAtLeast(0L)) }
    var maxMp by remember { mutableStateOf(initialState.maxMp.coerceAtLeast(0L)) }
    var gold by remember { mutableStateOf(initialState.gold.coerceAtLeast(0L)) }
    var activeQuest by remember { mutableStateOf(initialState.activeQuest) }

    val statsMap = remember { mutableStateMapOf<String, Long>().apply { putAll(initialState.stats) } }
    val inventoryList = remember { mutableStateListOf<String>().apply { addAll(initialState.inventory) } }
    val statusesList = remember { mutableStateListOf<String>().apply { addAll(initialState.statuses) } }

    // Detailed player profile
    var race by remember { mutableStateOf(initialState.playerProfile.race) }
    var charClass by remember { mutableStateOf(initialState.playerProfile.charClass) }
    var origin by remember { mutableStateOf(initialState.playerProfile.origin) }
    var appearance by remember { mutableStateOf(initialState.playerProfile.appearance) }
    var speechStyle by remember { mutableStateOf(initialState.playerProfile.speechStyle) }
    val equipmentSlots = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(initialState.playerProfile.equipmentSlots)
        }
    }
    val perksList = remember { mutableStateListOf<String>().apply { addAll(initialState.playerProfile.perks) } }
    val factionReps = remember {
        mutableStateMapOf<String, Long>().apply { putAll(initialState.playerProfile.factionReputation) }
    }
    var worldEnv by remember { mutableStateOf(initialState.worldEnv) }

    var currentLocationId by remember { mutableStateOf(initialState.currentLocationId) }
    val locationsList = remember { mutableStateListOf<WorldLocationNode>().apply { addAll(initialState.locations) } }
    val npcsList = remember { mutableStateListOf<NpcDossier>().apply { addAll(initialState.npcs) } }
    val questsList = remember { mutableStateListOf<QuestItem>().apply { addAll(initialState.quests) } }
    val chronicleList = remember { mutableStateListOf<ChronicleEntry>().apply { addAll(initialState.chronicle) } }
    var combatState by remember { mutableStateOf(initialState.combat) }

    // Sync live async changes from Perception rolls, Trades, or Backdrop generation
    LaunchedEffect(initialState.npcs, initialState.gold, initialState.inventory, initialState.locations) {
        npcsList.clear()
        npcsList.addAll(initialState.npcs)
        gold = initialState.gold.coerceAtLeast(0L)
        inventoryList.clear()
        inventoryList.addAll(initialState.inventory)
        locationsList.clear()
        locationsList.addAll(initialState.locations)
        currentLocationId = initialState.currentLocationId
    }

    var openedNpcId by remember(initialTab, focusedEntityId) {
        mutableStateOf(
            if (initialTab == 1 && !focusedEntityId.isNullOrBlank()) focusedEntityId else null
        )
    }

    // Inputs
    var newStatName by remember { mutableStateOf("") }
    var newItemText by remember { mutableStateOf("") }
    var newStatusText by remember { mutableStateOf("") }
    var newPerkText by remember { mutableStateOf("") }
    var newFactionName by remember { mutableStateOf("") }
    var npcSearchQuery by remember { mutableStateOf("") }
    var newNpcName by remember { mutableStateOf("") }
    var newNpcRole by remember { mutableStateOf("") }
    var newQuestTitle by remember { mutableStateOf("") }
    var newQuestDesc by remember { mutableStateOf("") }
    var newCombatantName by remember { mutableStateOf("") }
    var newCombatantHp by remember { mutableStateOf("18") }
    var newCombatantInit by remember { mutableStateOf("12") }
    var newCombatantIsEnemy by remember { mutableStateOf(true) }
    var d20Purpose by remember { mutableStateOf("") }
    var customModifierText by remember { mutableStateOf("2") }

    fun buildUpdatedState(): RpgState = RpgState(
        enabled = enabled,
        level = level.coerceIn(1L, 999L),
        hp = hp.coerceIn(0L, maxHp.coerceAtLeast(1L)),
        maxHp = maxHp.coerceIn(1L, 99999L),
        mp = mp.coerceIn(0L, maxMp.coerceAtLeast(0L)),
        maxMp = maxMp.coerceIn(0L, 99999L),
        gold = gold.coerceIn(0L, 9999999L),
        stats = statsMap.toMap(),
        inventory = inventoryList.toList(),
        statuses = statusesList.toList(),
        activeQuest = activeQuest.trim().take(500),
        currentLocationId = currentLocationId,
        locations = locationsList.toList(),
        combat = combatState,
        playerProfile = PlayerProfile(
            race = race.trim().take(60),
            charClass = charClass.trim().take(60),
            origin = origin.trim().take(300),
            appearance = appearance.trim().take(400),
            speechStyle = speechStyle.trim().take(240),
            equipmentSlots = equipmentSlots.toMap(),
            perks = perksList.toList(),
            factionReputation = factionReps.toMap(),
            statusDurations = initialState.playerProfile.statusDurations
        ),
        worldEnv = worldEnv,
        npcs = npcsList.toList(),
        quests = questsList.toList(),
        chronicle = chronicleList.toList()
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("studio_close_button")
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Закрыть студию")
                            }
                        },
                        title = {
                            Text("Студия Мира и Персонажа", style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            Button(
                                onClick = {
                                    onSave(buildUpdatedState())
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("save_rpg_sheet_button")
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Сохранить")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Игрок") },
                            modifier = Modifier.testTag("rpg_tab_character")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("NPC (${npcsList.size})") },
                            modifier = Modifier.testTag("studio_tab_npc")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Карта и Мир (${locationsList.size})") },
                            modifier = Modifier.testTag("rpg_tab_map")
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Квесты и Хроника") },
                            modifier = Modifier.testTag("studio_tab_quests")
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text("Бой и d20") },
                            modifier = Modifier.testTag("rpg_tab_combat")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 820.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedTab) {
                                // TAB 0: PLAYER SHEET, PROFILE, EQUIPMENT, PERKS, FACTIONS
                                0 -> {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Передавать RPG-состояние в ИИ", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Агенты видят ваш профиль, экипировку, квесты, мир и применяют обновления",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = enabled,
                                                onCheckedChange = { enabled = it },
                                                modifier = Modifier.testTag("rpg_enabled_switch")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = race,
                                            onValueChange = { race = it },
                                            label = { Text("Раса / Вид") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = charClass,
                                            onValueChange = { charClass = it },
                                            label = { Text("Класс / Роль") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = origin,
                                        onValueChange = { origin = it },
                                        label = { Text("Происхождение / Предыстория") },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = appearance,
                                        onValueChange = { appearance = it },
                                        label = { Text("Внешность и приметы") },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = speechStyle,
                                        onValueChange = { speechStyle = it },
                                        label = { Text("Манера речи и повадки") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Уровень", style = MaterialTheme.typography.labelSmall)
                                                    Text("$level", style = MaterialTheme.typography.titleLarge)
                                                }
                                                Row {
                                                    IconButton(onClick = { level = (level - 1).coerceAtLeast(1L) }) {
                                                        Icon(Icons.Outlined.Remove, contentDescription = "Уменьшить уровень")
                                                    }
                                                    IconButton(onClick = { level = (level + 1).coerceAtMost(999L) }) {
                                                        Icon(Icons.Outlined.Add, contentDescription = "Увеличить уровень")
                                                    }
                                                }
                                            }
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Золото", style = MaterialTheme.typography.labelSmall)
                                                    Text("$gold g", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                                                }
                                                Row {
                                                    TextButton(onClick = { gold = (gold - 10).coerceAtLeast(0L) }) { Text("-10") }
                                                    TextButton(onClick = { gold = (gold + 10).coerceAtMost(9999999L) }) { Text("+10") }
                                                }
                                            }
                                        }
                                    }

                                    StudioResourceBar(
                                        label = "Здоровье (HP)",
                                        current = hp,
                                        max = maxHp,
                                        barColor = MaterialTheme.colorScheme.error,
                                        onDelta = { d -> hp = (hp + d).coerceIn(0L, maxHp) },
                                        onMaxDelta = { d ->
                                            maxHp = (maxHp + d).coerceIn(1L, 99999L)
                                            hp = hp.coerceAtMost(maxHp)
                                        }
                                    )

                                    StudioResourceBar(
                                        label = "Мана / Энергия (MP)",
                                        current = mp,
                                        max = maxMp,
                                        barColor = MaterialTheme.colorScheme.primary,
                                        onDelta = { d -> mp = (mp + d).coerceIn(0L, maxMp) },
                                        onMaxDelta = { d ->
                                            maxMp = (maxMp + d).coerceIn(0L, 99999L)
                                            mp = mp.coerceAtMost(maxMp)
                                        }
                                    )

                                    HorizontalDivider()
                                    Text("Характеристики и броски d20", style = MaterialTheme.typography.titleMedium)
                                    statsMap.entries.toList().forEach { (statName, statVal) ->
                                        val mod = kotlin.math.floor((statVal - 10.0) / 2.0).toLong()
                                        val modStr = if (mod >= 0) "+$mod" else "$mod"
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(statName, style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        "Мод: $modStr",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = {
                                                        onSave(buildUpdatedState())
                                                        onRollTacticalD20(statName, mod, "")
                                                        onDismiss()
                                                    }) { Text("d20") }
                                                    IconButton(onClick = { statsMap[statName] = (statVal - 1).coerceAtLeast(-20L) }) {
                                                        Icon(Icons.Outlined.Remove, contentDescription = "Уменьшить $statName")
                                                    }
                                                    Text("$statVal", style = MaterialTheme.typography.titleMedium)
                                                    IconButton(onClick = { statsMap[statName] = (statVal + 1).coerceAtMost(999L) }) {
                                                        Icon(Icons.Outlined.Add, contentDescription = "Увеличить $statName")
                                                    }
                                                    IconButton(onClick = { statsMap.remove(statName) }) {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Удалить характеристику")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newStatName,
                                            onValueChange = { newStatName = it },
                                            label = { Text("Новая характеристика") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val clean = newStatName.trim().take(32)
                                                if (clean.isNotEmpty() && statsMap.size < 16) {
                                                    statsMap[clean] = 10L
                                                    newStatName = ""
                                                }
                                            },
                                            modifier = Modifier.height(54.dp)
                                        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить характеристику") }
                                    }

                                    HorizontalDivider()
                                    Text("Слоты экипировки", style = MaterialTheme.typography.titleMedium)
                                    listOf("Оружие", "Броня", "Аксессуар", "Реликвия").forEach { slot ->
                                        var showEquipPicker by remember(slot) { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = equipmentSlots[slot].orEmpty(),
                                                onValueChange = { equipmentSlots[slot] = it },
                                                label = { Text(slot) },
                                                placeholder = { Text("Пусто") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (inventoryList.isNotEmpty()) {
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { showEquipPicker = true },
                                                        modifier = Modifier.height(54.dp)
                                                    ) { Text("Из сумки") }
                                                    DropdownMenu(
                                                        expanded = showEquipPicker,
                                                        onDismissRequest = { showEquipPicker = false }
                                                    ) {
                                                        inventoryList.forEach { item ->
                                                            DropdownMenuItem(
                                                                text = { Text(item) },
                                                                onClick = {
                                                                    equipmentSlots[slot] = item
                                                                    showEquipPicker = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider()
                                    Text("Навыки и перки (${perksList.size})", style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newPerkText,
                                            onValueChange = { newPerkText = it },
                                            label = { Text("Добавить навык / перк") },
                                            placeholder = { Text("Ночное зрение, Скрытый клинок...") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val p = newPerkText.trim().take(80)
                                                if (p.isNotEmpty() && perksList.size < 20) {
                                                    perksList.add(p)
                                                    newPerkText = ""
                                                }
                                            },
                                            modifier = Modifier.height(54.dp)
                                        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить перк") }
                                    }
                                    if (perksList.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            perksList.forEachIndexed { idx, perk ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = { perksList.removeAt(idx) },
                                                    label = { Text(perk) },
                                                    trailingIcon = {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Убрать", modifier = Modifier.size(16.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()
                                    Text("Репутация фракций", style = MaterialTheme.typography.titleMedium)
                                    factionReps.entries.toList().forEach { (faction, rep) ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("$faction: $rep", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                Row {
                                                    TextButton(onClick = { factionReps[faction] = (rep - 10).coerceIn(-100L, 100L) }) { Text("-10") }
                                                    TextButton(onClick = { factionReps[faction] = (rep + 10).coerceIn(-100L, 100L) }) { Text("+10") }
                                                    IconButton(onClick = { factionReps.remove(faction) }) {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Удалить фракцию")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newFactionName,
                                            onValueChange = { newFactionName = it },
                                            label = { Text("Добавить фракцию") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val f = newFactionName.trim().take(60)
                                                if (f.isNotEmpty() && factionReps.size < 16) {
                                                    factionReps[f] = 0L
                                                    newFactionName = ""
                                                }
                                            },
                                            modifier = Modifier.height(54.dp)
                                        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить фракцию") }
                                    }

                                    HorizontalDivider()
                                    Text("Активные статусы и эффекты", style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newStatusText,
                                            onValueChange = { newStatusText = it },
                                            label = { Text("Добавить статус") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val s = newStatusText.trim().take(80)
                                                if (s.isNotEmpty() && statusesList.size < 20) {
                                                    statusesList.add(s)
                                                    newStatusText = ""
                                                }
                                            },
                                            modifier = Modifier.height(54.dp)
                                        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить статус") }
                                    }
                                    if (statusesList.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            statusesList.forEachIndexed { idx, st ->
                                                val dur = initialState.playerProfile.statusDurations[st]
                                                val label = if (dur != null) "$st (${dur} х.)" else st
                                                InputChip(
                                                    selected = true,
                                                    onClick = { statusesList.removeAt(idx) },
                                                    label = { Text(label) },
                                                    trailingIcon = {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()
                                    Text("Инвентарь (${inventoryList.size}/40)", style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            label = { Text("Добавить предмет") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val item = newItemText.trim().take(120)
                                                if (item.isNotEmpty() && inventoryList.size < 40) {
                                                    inventoryList.add(item)
                                                    newItemText = ""
                                                }
                                            },
                                            modifier = Modifier.height(54.dp)
                                        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить предмет") }
                                    }
                                    inventoryList.forEachIndexed { idx, item ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• $item", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { inventoryList.removeAt(idx) }) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Убрать предмет")
                                                }
                                            }
                                        }
                                    }
                                }

                                // TAB 1: NPC DOSSIERS WITH FOG OF WAR
                                1 -> {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Column {
                                                Text("Досье NPC и «Туман знаний»", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Характеристики и тайны NPC скрыты до проверки Восприятия (d20) или высокого доверия.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = npcSearchQuery,
                                        onValueChange = { npcSearchQuery = it },
                                        label = { Text("Поиск NPC по имени или роли") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val filteredNpcs = npcsList.filter {
                                        npcSearchQuery.isBlank() ||
                                            it.name.contains(npcSearchQuery, ignoreCase = true) ||
                                            it.role.contains(npcSearchQuery, ignoreCase = true)
                                    }

                                    if (filteredNpcs.isEmpty()) {
                                        Text(
                                            text = "NPC ещё не добавлены. Нажмите на имя персонажа в чате или создайте досье ниже.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    filteredNpcs.forEach { npc ->
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { openedNpcId = npc.id }
                                                .testTag("npc_card_${npc.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = npc.name,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                                        )
                                                        Icon(
                                                            imageVector = if (npc.statsRevealed) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                                            contentDescription = if (npc.statsRevealed) "Раскрыто" else "Скрыто",
                                                            tint = if (npc.statsRevealed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${npc.role.ifBlank { "Персонаж" }} • ${npc.dispositionLabelRu()} (${npc.disposition}) • Настроение: ${npc.mood}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    npcsList.removeAll { it.id == npc.id }
                                                }) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Удалить NPC")
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider()
                                    Text("Добавить нового NPC", style = MaterialTheme.typography.titleSmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newNpcName,
                                            onValueChange = { newNpcName = it },
                                            label = { Text("Имя NPC") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = newNpcRole,
                                            onValueChange = { newNpcRole = it },
                                            label = { Text("Роль / Фракция") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val clean = newNpcName.trim().take(80)
                                            if (clean.isNotEmpty() && npcsList.size < 24) {
                                                npcsList.add(
                                                    NpcDossier(
                                                        id = "npc_${UUID.randomUUID().toString().take(8)}",
                                                        name = clean,
                                                        role = newNpcRole.trim().take(80).ifBlank { "Персонаж сцены" },
                                                        stats = mapOf(
                                                            "Сила" to 11L,
                                                            "Ловкость" to 12L,
                                                            "Интеллект" to 12L,
                                                            "Восприятие" to 12L,
                                                            "Харизма" to 12L
                                                        ),
                                                        secrets = listOf("Истинные мотивы скрыты.")
                                                    )
                                                )
                                                newNpcName = ""
                                                newNpcRole = ""
                                            }
                                        },
                                        enabled = newNpcName.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Создать досье NPC")
                                    }
                                }

                                // TAB 2: WORLD ENVIRONMENT & INTERACTIVE LOCATION GRAPH
                                2 -> {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text("Окружение и законы мира", style = MaterialTheme.typography.titleMedium)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = worldEnv.dayCount.toString(),
                                                    onValueChange = { v ->
                                                        worldEnv = worldEnv.copy(dayCount = (v.toLongOrNull() ?: 1L).coerceIn(1L, 99999L))
                                                    },
                                                    label = { Text("День") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(0.7f)
                                                )
                                                OutlinedTextField(
                                                    value = worldEnv.timeOfDay,
                                                    onValueChange = { worldEnv = worldEnv.copy(timeOfDay = it) },
                                                    label = { Text("Время суток") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = worldEnv.weather,
                                                    onValueChange = { worldEnv = worldEnv.copy(weather = it) },
                                                    label = { Text("Погода") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = worldEnv.worldLaws,
                                                onValueChange = { worldEnv = worldEnv.copy(worldLaws = it) },
                                                label = { Text("Законы магии / технологии / общества") },
                                                minLines = 2,
                                                maxLines = 4,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = worldEnv.economyNotes,
                                                onValueChange = { worldEnv = worldEnv.copy(economyNotes = it) },
                                                label = { Text("Экономика, валюта и цены") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    LocationGraphCanvas(
                                        locations = locationsList.toList(),
                                        currentLocationId = currentLocationId,
                                        focusedLocationId = if (selectedTab == 2) focusedEntityId else null,
                                        onUpdateNodes = { next ->
                                            locationsList.clear()
                                            locationsList.addAll(next)
                                        },
                                        onMoveToLocation = { locId ->
                                            enabled = true
                                            currentLocationId = locId
                                            val updated = locationsList.map {
                                                if (it.id == locId) it.copy(visited = true) else it
                                            }
                                            locationsList.clear()
                                            locationsList.addAll(updated)
                                            onSave(buildUpdatedState())
                                            onMoveToLocation(locId)
                                            onDismiss()
                                        },
                                        onGenerateLocationBackdrop = { locId ->
                                            onSave(buildUpdatedState())
                                            onGenerateLocationBackdrop(locId)
                                        }
                                    )
                                }

                                // TAB 3: QUEST JOURNAL, CHRONICLE TIMELINE & LOREBOOK
                                3 -> {
                                    OutlinedTextField(
                                        value = activeQuest,
                                        onValueChange = { activeQuest = it },
                                        label = { Text("Главная текущая цель") },
                                        minLines = 2,
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text("Журнал квестов (${questsList.size})", style = MaterialTheme.typography.titleMedium)
                                    questsList.forEachIndexed { idx, quest ->
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (quest.completed) {
                                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Checkbox(
                                                            checked = quest.completed,
                                                            onCheckedChange = { done ->
                                                                questsList[idx] = quest.copy(completed = done)
                                                            }
                                                        )
                                                        Column {
                                                            Text(quest.title, style = MaterialTheme.typography.titleSmall)
                                                            if (quest.description.isNotBlank()) {
                                                                Text(
                                                                    quest.description,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(onClick = { questsList.removeAt(idx) }) {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Удалить квест")
                                                    }
                                                }
                                                if (quest.stages.isNotEmpty()) {
                                                    quest.stages.forEachIndexed { stIdx, stageText ->
                                                        val done = stIdx < quest.completedStages.toInt() || quest.completed
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(
                                                                checked = done,
                                                                onCheckedChange = { checked ->
                                                                    val newDoneCount = if (checked) (stIdx + 1L) else stIdx.toLong()
                                                                    questsList[idx] = quest.copy(completedStages = newDoneCount)
                                                                }
                                                            )
                                                            Text(stageText, style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newQuestTitle,
                                            onValueChange = { newQuestTitle = it },
                                            label = { Text("Название квеста") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = newQuestDesc,
                                            onValueChange = { newQuestDesc = it },
                                            label = { Text("Суть / этап") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val t = newQuestTitle.trim().take(120)
                                            if (t.isNotEmpty() && questsList.size < 20) {
                                                questsList.add(
                                                    QuestItem(
                                                        id = "quest_${UUID.randomUUID().toString().take(8)}",
                                                        title = t,
                                                        description = newQuestDesc.trim().take(500),
                                                        stages = listOfNotNull(newQuestDesc.trim().takeIf { it.isNotBlank() })
                                                    )
                                                )
                                                newQuestTitle = ""
                                                newQuestDesc = ""
                                            }
                                        },
                                        enabled = newQuestTitle.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Добавить квест") }

                                    HorizontalDivider()
                                    Text("Хроника событий (${chronicleList.size})", style = MaterialTheme.typography.titleMedium)
                                    if (memorySummary.isNotBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Сжатая память (Хроник):", style = MaterialTheme.typography.labelLarge)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(memorySummary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    chronicleList.asReversed().forEach { entry ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "Ход ${entry.turnNumber} • ${entry.title}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }

                                    if (loreEntries.isNotEmpty()) {
                                        HorizontalDivider()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                            Text("Статьи Лорбука (${loreEntries.size})", style = MaterialTheme.typography.titleMedium)
                                        }
                                        loreEntries.forEach { le ->
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(le.title, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        le.content,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // TAB 4: TACTICAL COMBAT & d20 ROLLER
                                4 -> {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Режим тактического боя", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Раунд ${combatState.round} • Участников: ${combatState.combatants.size}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = combatState.active,
                                                onCheckedChange = { active ->
                                                    combatState = combatState.copy(active = active)
                                                    onUpdateCombat(combatState)
                                                }
                                            )
                                        }
                                    }

                                    if (combatState.combatants.isNotEmpty()) {
                                        val sorted = combatState.combatants.sortedByDescending { it.initiative }
                                        val safeIdx = combatState.turnIndex.toInt().coerceIn(0, (sorted.size - 1).coerceAtLeast(0))
                                        val currentTurn = sorted.getOrNull(safeIdx)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Ход: ${currentTurn?.name ?: "—"}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Button(
                                                onClick = {
                                                    val nextIdx = (safeIdx + 1) % sorted.size
                                                    val nextRound = if (nextIdx == 0) combatState.round + 1L else combatState.round
                                                    combatState = combatState.copy(
                                                        active = true,
                                                        round = nextRound,
                                                        turnIndex = nextIdx.toLong(),
                                                        combatants = sorted
                                                    )
                                                    onUpdateCombat(combatState)
                                                },
                                                modifier = Modifier.testTag("combat_next_turn_button")
                                            ) {
                                                Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("След. ход")
                                            }
                                        }

                                        sorted.forEachIndexed { index, c ->
                                            val isTurn = combatState.active && index == safeIdx
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = if (isTurn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("${c.name} (${if (c.enemy) "Враг" else "Союзник"})", style = MaterialTheme.typography.titleSmall)
                                                        Text("Иниц. ${c.initiative} • HP ${c.hp}/${c.maxHp}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        TextButton(onClick = {
                                                            combatState = combatState.copy(combatants = combatState.combatants.map {
                                                                if (it.id == c.id) it.copy(hp = (it.hp - 5).coerceAtLeast(0L)) else it
                                                            })
                                                        }) { Text("-5") }
                                                        TextButton(onClick = {
                                                            combatState = combatState.copy(combatants = combatState.combatants.map {
                                                                if (it.id == c.id) it.copy(hp = (it.hp + 5).coerceAtMost(it.maxHp)) else it
                                                            })
                                                        }) { Text("+5") }
                                                        IconButton(onClick = {
                                                            combatState = combatState.copy(combatants = combatState.combatants.filterNot { it.id == c.id })
                                                        }) { Icon(Icons.Outlined.Close, contentDescription = "Удалить участника") }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newCombatantName,
                                            onValueChange = { newCombatantName = it },
                                            label = { Text("Имя участника боя") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.4f)
                                        )
                                        OutlinedTextField(
                                            value = newCombatantHp,
                                            onValueChange = { newCombatantHp = it },
                                            label = { Text("HP") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                        OutlinedTextField(
                                            value = newCombatantInit,
                                            onValueChange = { newCombatantInit = it },
                                            label = { Text("Иниц.") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(selected = newCombatantIsEnemy, onClick = { newCombatantIsEnemy = true }, label = { Text("Враг") })
                                            FilterChip(selected = !newCombatantIsEnemy, onClick = { newCombatantIsEnemy = false }, label = { Text("Союзник") })
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val cname = newCombatantName.trim().take(60)
                                                if (cname.isNotEmpty() && combatState.combatants.size < 16) {
                                                    val maxH = (newCombatantHp.toLongOrNull() ?: 16L).coerceIn(1L, 99999L)
                                                    val initVal = (newCombatantInit.toLongOrNull() ?: 10L).coerceIn(-10L, 100L)
                                                    combatState = combatState.copy(
                                                        active = true,
                                                        combatants = combatState.combatants + Combatant(
                                                            id = "cmb_${UUID.randomUUID().toString().take(8)}",
                                                            name = cname,
                                                            hp = maxH,
                                                            maxHp = maxH,
                                                            initiative = initVal,
                                                            enemy = newCombatantIsEnemy
                                                        )
                                                    )
                                                    onUpdateCombat(combatState)
                                                    newCombatantName = ""
                                                }
                                            },
                                            enabled = newCombatantName.isNotBlank()
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("В бой")
                                        }
                                    }

                                    HorizontalDivider()
                                    Text("Быстрый бросок d20 + модификатор", style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = d20Purpose,
                                            onValueChange = { d20Purpose = it },
                                            label = { Text("Цель (взлом, парирование...)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.4f)
                                        )
                                        OutlinedTextField(
                                            value = customModifierText,
                                            onValueChange = { customModifierText = it },
                                            label = { Text("Мод") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val mod = customModifierText.toLongOrNull() ?: 0L
                                            onSave(buildUpdatedState())
                                            onRollTacticalD20("Проверка", mod, d20Purpose.trim())
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Outlined.Casino, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Бросить d20 в чат")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    val openedNpc = npcsList.firstOrNull { it.id == openedNpcId }
    if (openedNpc != null) {
        val perceptionRaw = statsMap["Восприятие"] ?: statsMap["Мудрость"] ?: statsMap["Интеллект"] ?: 10L
        val perceptionMod = kotlin.math.floor((perceptionRaw - 10.0) / 2.0).toLong()
        NpcDossierDialog(
            npc = openedNpc,
            playerGold = gold,
            perceptionMod = perceptionMod,
            onDismiss = { openedNpcId = null },
            onRollPerception = { npcId ->
                onSave(buildUpdatedState())
                onRollNpcPerception(npcId)
            },
            onBuyItem = { npcId, itemName, price ->
                onSave(buildUpdatedState())
                onBuyItemFromNpc(npcId, itemName, price)
            },
            onUpdateNpc = { updatedNpc ->
                val idx = npcsList.indexOfFirst { it.id == updatedNpc.id }
                if (idx >= 0) npcsList[idx] = updatedNpc
            }
        )
    }
}

@Composable
private fun StudioResourceBar(
    label: String,
    current: Long,
    max: Long,
    barColor: Color,
    onDelta: (Long) -> Unit,
    onMaxDelta: (Long) -> Unit
) {
    val progress = if (max > 0L) (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text("$current / $max", style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { progress },
                color = barColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    TextButton(onClick = { onDelta(-5) }) { Text("-5") }
                    TextButton(onClick = { onDelta(-1) }) { Text("-1") }
                    TextButton(onClick = { onDelta(1) }) { Text("+1") }
                    TextButton(onClick = { onDelta(5) }) { Text("+5") }
                }
                Row {
                    TextButton(onClick = { onMaxDelta(-5) }) { Text("Макс -5") }
                    TextButton(onClick = { onMaxDelta(5) }) { Text("Макс +5") }
                }
            }
        }
    }
}
