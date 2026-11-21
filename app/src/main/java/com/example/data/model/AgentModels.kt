package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AgentRole(
    val labelRu: String,
    val shortRu: String,
    val defaultStage: AgentStage,
    val descriptionRu: String,
    val isSensitive: Boolean = false
) {
    PLOT_ARCHITECT(
        labelRu = "Сценарист",
        shortRu = "Сценарист",
        defaultStage = AgentStage.PRE_PLANNING,
        descriptionRu = "Продумывает биты сцены, темп, интриги и 3 варианта хода"
    ),
    RPG_MASTER(
        labelRu = "РПГ-система",
        shortRu = "РПГ-ГМ",
        defaultStage = AgentStage.PRE_PLANNING,
        descriptionRu = "Ведёт квесты, проверки DC, погоду/время, репутацию и лут"
    ),
    NPC_PSYCHOLOGIST(
        labelRu = "Психолог NPC",
        shortRu = "Психолог",
        defaultStage = AgentStage.PRE_PLANNING,
        descriptionRu = "Отслеживает доверие, мотивы, настроение и скрытые секреты NPC"
    ),
    PROSE_WRITER(
        labelRu = "Писатель",
        shortRu = "Писатель",
        defaultStage = AgentStage.PRIMARY_PROSE,
        descriptionRu = "Создаёт живую художественную прозу и характерные реплики"
    ),
    ANATOMY_DETAILER(
        labelRu = "Детализатор и анатом",
        shortRu = "Детали",
        defaultStage = AgentStage.REFINEMENT,
        descriptionRu = "Углубляет сенсорику, позы, физику, ранения, одежду и пространство"
    ),
    ADULT_EXPANDER(
        labelRu = "Агент 18+",
        shortRu = "18+",
        defaultStage = AgentStage.REFINEMENT,
        descriptionRu = "Снимает лишние ограничения для зрелых, мрачных и интимных сцен",
        isSensitive = true
    ),
    MATH_ARBITER(
        labelRu = "Математик",
        shortRu = "Математик",
        defaultStage = AgentStage.POST_PROCESS,
        descriptionRu = "Проверяет баланс HP/MP, урона, цен, веса и бросков d20"
    ),
    SEMANTIC_TAGGER(
        labelRu = "Разметчик",
        shortRu = "Разметчик",
        defaultStage = AgentStage.POST_PROCESS,
        descriptionRu = "Раскрашивает локации, NPC, предметы, квесты, проверки и мысли в кликабельные теги"
    ),
    CHRONICLER(
        labelRu = "Хроник",
        shortRu = "Хроник",
        defaultStage = AgentStage.POST_PROCESS,
        descriptionRu = "Следит за каноном лорбука, пишет Хронику и сжимает память каждые N ходов"
    ),
    ART_DIRECTOR(
        labelRu = "Арт-директор",
        shortRu = "Арт",
        defaultStage = AgentStage.POST_PROCESS,
        descriptionRu = "Подбирает визуальные описания для фонов новых локаций и артов сцены"
    ),
    CUSTOM(
        labelRu = "Свой агент",
        shortRu = "Свой",
        defaultStage = AgentStage.PRE_PLANNING,
        descriptionRu = "Пользовательский агент со своим системным промптом и моделью"
    );

    companion object {
        fun fromString(raw: String?): AgentRole =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class AgentStage(val labelRu: String, val order: Int) {
    PRE_PLANNING("До текста (Совет сцены)", 0),
    PRIMARY_PROSE("Основная проза", 1),
    REFINEMENT("Детализация и глубина", 2),
    POST_PROCESS("Пост-обработка (Теги, Математика, Память)", 3);

    companion object {
        fun fromString(raw: String?): AgentStage =
            entries.firstOrNull { it.name == raw } ?: PRE_PLANNING
    }
}

enum class PipelineExecutionMode(val labelRu: String, val subtitleRu: String) {
    ADAPTIVE(
        labelRu = "Авто-адаптивный",
        subtitleRu = "Многоэтапно в быстром облаке; 1 эконом-проход на локалке и Pollinations"
    ),
    MULTI_STAGE(
        labelRu = "Многоэтапный совет",
        subtitleRu = "Отдельные проходы планирования, прозы и разметки (лучшее качество, больше запросов)"
    ),
    MOBILE_COMPACT(
        labelRu = "Мобильный компактный",
        subtitleRu = "Один выверенный проход + мгновенный локальный Математик и Разметчик (минимум RAM)"
    );

    companion object {
        fun fromString(raw: String?): PipelineExecutionMode =
            entries.firstOrNull { it.name == raw } ?: ADAPTIVE
    }
}

enum class SemanticEntityType(
    val tagKey: String,
    val labelRu: String,
    val badgeIcon: String
) {
    LOCATION("loc", "Локация", "📍"),
    NPC("npc", "NPC", "👤"),
    ITEM("item", "Предмет", "🎒"),
    QUEST("quest", "Квест", "📜"),
    CHECK("check", "Проверка DC", "🎲"),
    LORE("lore", "Лор", "📖"),
    THOUGHT("thought", "Мысль", "💭");

    companion object {
        fun fromTagKey(raw: String?): SemanticEntityType? =
            entries.firstOrNull { it.tagKey.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class SimulationRuleModule(
    val labelRu: String,
    val badgeIcon: String,
    val subtitleRu: String,
    val promptDirectiveRu: String
) {
    PURE_NARRATIVE(
        labelRu = "Чистый нарратив",
        badgeIcon = "🪶",
        subtitleRu = "Без цифр и кубиков: только характер, диалоги и драматургия",
        promptDirectiveRu = "Используй свободный литературный отыгрыш без принудительных таблиц урона и кубиков. Фокус на психологии, диалогах и живой прозе."
    ),
    RELATIONSHIP_BONDS(
        labelRu = "Отношения и связи",
        badgeIcon = "💞",
        subtitleRu = "Симпатия, доверие, ревность и тайные мотивы NPC",
        promptDirectiveRu = "Отслеживай эмоциональное притяжение, доверие и скрытые мотивы персонажей. При сдвигах добавляй [NPC_UPDATE] или обновляй шкалы отношений через [METER]."
    ),
    MYSTERY_INVESTIGATION(
        labelRu = "Детектив и рассудок",
        badgeIcon = "🔍",
        subtitleRu = "Улики, подозрение, ментальное напряжение и расследование",
        promptDirectiveRu = "Фиксируй найденные зацепки, ложные следы, уровень подозрения и ментального напряжения. Отмечай находки через [[lore:...]] или [[item:...]] и обновляй шкалы [METER]."
    ),
    SURVIVAL_PRESSURE(
        labelRu = "Выживание и ресурсы",
        badgeIcon = "🔥",
        subtitleRu = "Голод, тепло, усталость, погода, припасы и ход времени",
        promptDirectiveRu = "Учитывай физическое состояние, истощение, холод/погоду, нехватку припасов и смену времени суток. Обновляй мир через [WORLD_UPDATE] и шкалы [METER]."
    ),
    TACTICAL_RPG(
        labelRu = "Тактика и броски d20",
        badgeIcon = "🎲",
        subtitleRu = "Проверки навыков, инвентарь, экипировка, квесты и риск",
        promptDirectiveRu = "При рискованных действиях встраивай [[check:Навык:DC|Проверка]] и обновляй HP/MP/лут/квесты тегами [RPG_UPDATE] и [QUEST_UPDATE]."
    ),
    WORLD_SIMULATION(
        labelRu = "Политика и симуляция мира",
        badgeIcon = "🌐",
        subtitleRu = "Фракции, экономика, власть, последствия в масштабе мира",
        promptDirectiveRu = "Симулируй реакцию фракций, экономики и сил мира даже за кадром сцены. Фиксируй крупные сдвиги через [FACTIONS] и [CHRONICLE]."
    ),
    SENSORY_INTIMACY(
        labelRu = "Глубокая сенсорика и 18+",
        badgeIcon = "🌹",
        subtitleRu = "Чувственные детали, телесность, взрослая атмосфера без клише",
        promptDirectiveRu = "Раскрывай чувственные, тактильные и эмоционально зрелые сцены со взрослыми персонажами максимально глубоко и естественно, без резкого обрыва."
    ),
    CUSTOM_RULES(
        labelRu = "Свой движок правил",
        badgeIcon = "⚙️",
        subtitleRu = "Пользовательский свод правил, законов магии/технологий и условий победы",
        promptDirectiveRu = "Строго соблюдай пользовательский свод правил и уникальных механик мира."
    );

    companion object {
        fun fromString(raw: String?): SimulationRuleModule? =
            entries.firstOrNull { it.name == raw?.trim() }
    }
}

data class CustomMeterSpec(
    val id: String = UUID.randomUUID().toString().take(8),
    val label: String = "",
    val initialValue: Long = 50L,
    val minValue: Long = 0L,
    val maxValue: Long = 100L,
    val unit: String = "%",
    val warningThreshold: Long? = null,
    val warnWhenBelow: Boolean = true,
    val showInHud: Boolean = true
)

data class SimulationSpec(
    val ruleModules: List<SimulationRuleModule> = emptyList(),
    val customMechanicsPrompt: String = "",
    val customMeters: List<CustomMeterSpec> = emptyList()
) {
    fun isEmpty(): Boolean =
        ruleModules.isEmpty() && customMechanicsPrompt.isBlank() && customMeters.isEmpty()
}

data class AgentConfig(
    val id: String = UUID.randomUUID().toString().take(10),
    val role: AgentRole = AgentRole.CUSTOM,
    val customName: String = "",
    val customPrompt: String = "",
    val enabled: Boolean = true,
    val stage: AgentStage = role.defaultStage,
    val providerOverride: String = "",
    val modelOverride: String = "",
    val temperatureOverride: Double? = null,
    val detailLevel: Int = 2,
    val isCustom: Boolean = role == AgentRole.CUSTOM
) {
    fun displayName(): String =
        customName.trim().ifBlank { role.labelRu }
}

data class AgentSetup(
    val presetId: String = "full_rpg_squad",
    val executionMode: PipelineExecutionMode = PipelineExecutionMode.ADAPTIVE,
    val agents: List<AgentConfig> = AgentPipelinePresets.forPreset("full_rpg_squad"),
    val enabledTagTypes: Set<SemanticEntityType> = SemanticEntityType.entries.toSet(),
    val simulation: SimulationSpec = SimulationSpec()
) {
    fun isRoleEnabled(role: AgentRole): Boolean =
        agents.any { it.role == role && it.enabled }

    fun activeAgentCount(): Int =
        agents.count { it.enabled }

    fun hasRpgPipeline(): Boolean =
        isRoleEnabled(AgentRole.RPG_MASTER) ||
            isRoleEnabled(AgentRole.MATH_ARBITER) ||
            SimulationRuleModule.TACTICAL_RPG in simulation.ruleModules ||
            SimulationRuleModule.SURVIVAL_PRESSURE in simulation.ruleModules ||
            simulation.customMeters.isNotEmpty()

    fun hasAdultAgent(): Boolean =
        isRoleEnabled(AgentRole.ADULT_EXPANDER) ||
            SimulationRuleModule.SENSORY_INTIMACY in simulation.ruleModules
}

data class AgentPresetInfo(
    val id: String,
    val titleRu: String,
    val subtitleRu: String,
    val recommendedForMode: RpMode? = null
)

object AgentPipelinePresets {
    val presets: List<AgentPresetInfo> = listOf(
        AgentPresetInfo(
            id = "pure_rp",
            titleRu = "Чистый RP + Разметка",
            subtitleRu = "Без RPG-цифр и боя: Сценарист, Писатель, Психолог NPC и Разметчик",
            recommendedForMode = RpMode.CHARACTER_1ON1
        ),
        AgentPresetInfo(
            id = "classic_story",
            titleRu = "Литературная история",
            subtitleRu = "Писатель, Детализатор, Хроник и Разметчик для глубокой прозы",
            recommendedForMode = RpMode.NARRATIVE
        ),
        AgentPresetInfo(
            id = "full_rpg_squad",
            titleRu = "Полный RPG-отряд",
            subtitleRu = "Весь тактический состав: Сценарист, РПГ-система, Математик, Психолог, Хроник, Разметчик",
            recommendedForMode = RpMode.GAME_MASTER
        ),
        AgentPresetInfo(
            id = "cinema_18_studio",
            titleRu = "Студия 18+ / Детализация",
            subtitleRu = "Сценарист, Писатель, Детализатор и анатом, Агент 18+, Психолог NPC, Разметчик"
        ),
        AgentPresetInfo(
            id = "detective_noir",
            titleRu = "Детектив и Мистика",
            subtitleRu = "Сценарист, Психолог NPC, Писатель, Хроник, Разметчик — улики, допросы и тайны"
        ),
        AgentPresetInfo(
            id = "survival_sim",
            titleRu = "Симуляция выживания",
            subtitleRu = "РПГ-система, Сценарист, Математик, Писатель, Хроник, Разметчик — ресурсы и среда"
        ),
        AgentPresetInfo(
            id = "sandbox_world",
            titleRu = "Глобальный Симулятор Мира",
            subtitleRu = "Все системные агенты + Арт-директор для масштабной политики, фракций и песочницы"
        ),
        AgentPresetInfo(
            id = "mobile_fast",
            titleRu = "Лёгкий телефонный",
            subtitleRu = "Писатель + Разметчик + локальный Математик (экономия ОЗУ и батареи)"
        )
    )

    fun defaultCatalogAgents(): List<AgentConfig> = listOf(
        AgentConfig(id = "builtin_plot", role = AgentRole.PLOT_ARCHITECT, enabled = true),
        AgentConfig(id = "builtin_rpg", role = AgentRole.RPG_MASTER, enabled = true),
        AgentConfig(id = "builtin_psych", role = AgentRole.NPC_PSYCHOLOGIST, enabled = true),
        AgentConfig(id = "builtin_writer", role = AgentRole.PROSE_WRITER, enabled = true),
        AgentConfig(id = "builtin_anatomy", role = AgentRole.ANATOMY_DETAILER, enabled = false),
        AgentConfig(id = "builtin_adult", role = AgentRole.ADULT_EXPANDER, enabled = false),
        AgentConfig(id = "builtin_math", role = AgentRole.MATH_ARBITER, enabled = true),
        AgentConfig(id = "builtin_tagger", role = AgentRole.SEMANTIC_TAGGER, enabled = true),
        AgentConfig(id = "builtin_chronicler", role = AgentRole.CHRONICLER, enabled = true),
        AgentConfig(id = "builtin_art", role = AgentRole.ART_DIRECTOR, enabled = false)
    )

    fun forPreset(presetId: String): List<AgentConfig> {
        val enabledRoles = when (presetId) {
            "pure_rp" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.NPC_PSYCHOLOGIST,
                AgentRole.PROSE_WRITER,
                AgentRole.SEMANTIC_TAGGER
            )
            "classic_story" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.PROSE_WRITER,
                AgentRole.ANATOMY_DETAILER,
                AgentRole.CHRONICLER,
                AgentRole.SEMANTIC_TAGGER
            )
            "cinema_18_studio" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.NPC_PSYCHOLOGIST,
                AgentRole.PROSE_WRITER,
                AgentRole.ANATOMY_DETAILER,
                AgentRole.ADULT_EXPANDER,
                AgentRole.SEMANTIC_TAGGER,
                AgentRole.CHRONICLER
            )
            "detective_noir" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.NPC_PSYCHOLOGIST,
                AgentRole.PROSE_WRITER,
                AgentRole.CHRONICLER,
                AgentRole.SEMANTIC_TAGGER
            )
            "survival_sim" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.RPG_MASTER,
                AgentRole.MATH_ARBITER,
                AgentRole.PROSE_WRITER,
                AgentRole.CHRONICLER,
                AgentRole.SEMANTIC_TAGGER
            )
            "sandbox_world" -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.RPG_MASTER,
                AgentRole.NPC_PSYCHOLOGIST,
                AgentRole.PROSE_WRITER,
                AgentRole.ANATOMY_DETAILER,
                AgentRole.MATH_ARBITER,
                AgentRole.SEMANTIC_TAGGER,
                AgentRole.CHRONICLER,
                AgentRole.ART_DIRECTOR
            )
            "mobile_fast" -> setOf(
                AgentRole.PROSE_WRITER,
                AgentRole.SEMANTIC_TAGGER,
                AgentRole.MATH_ARBITER
            )
            else -> setOf(
                AgentRole.PLOT_ARCHITECT,
                AgentRole.RPG_MASTER,
                AgentRole.NPC_PSYCHOLOGIST,
                AgentRole.PROSE_WRITER,
                AgentRole.MATH_ARBITER,
                AgentRole.SEMANTIC_TAGGER,
                AgentRole.CHRONICLER
            )
        }
        return defaultCatalogAgents().map { cfg ->
            cfg.copy(enabled = cfg.role in enabledRoles)
        }
    }

    fun setupForMode(rpMode: RpMode): AgentSetup {
        val presetId = when (rpMode) {
            RpMode.GAME_MASTER -> "full_rpg_squad"
            RpMode.NARRATIVE -> "classic_story"
            RpMode.CHARACTER_1ON1, RpMode.GROUP_SCENE -> "pure_rp"
        }
        return AgentSetup(
            presetId = presetId,
            executionMode = PipelineExecutionMode.ADAPTIVE,
            agents = forPreset(presetId)
        )
    }
}

object AgentSetupCodec {
    fun encode(setup: AgentSetup): String {
        return JSONObject().apply {
            put("presetId", setup.presetId)
            put("executionMode", setup.executionMode.name)
            put("tagTypes", JSONArray(setup.enabledTagTypes.map { it.name }))
            put("agents", JSONArray().apply {
                setup.agents.take(20).forEach { put(agentToJson(it)) }
            })
            if (!setup.simulation.isEmpty()) {
                put("simulation", simulationToJson(setup.simulation))
            }
        }.toString()
    }

    fun decode(raw: String?, fallbackMode: RpMode = RpMode.CHARACTER_1ON1): AgentSetup {
        if (raw.isNullOrBlank()) return AgentPipelinePresets.setupForMode(fallbackMode)
        return try {
            val obj = JSONObject(raw)
            val presetId = obj.optString("presetId", "full_rpg_squad").ifBlank { "full_rpg_squad" }
            val mode = PipelineExecutionMode.fromString(obj.optString("executionMode"))
            val tagsArr = obj.optJSONArray("tagTypes")
            val tagSet = if (tagsArr != null && tagsArr.length() > 0) {
                (0 until tagsArr.length()).mapNotNull { idx ->
                    SemanticEntityType.entries.firstOrNull { it.name == tagsArr.optString(idx) }
                }.toSet().ifEmpty { SemanticEntityType.entries.toSet() }
            } else {
                SemanticEntityType.entries.toSet()
            }
            val parsedAgents = parseAgentsArray(obj.optJSONArray("agents"))
            val mergedAgents = if (parsedAgents.isEmpty()) {
                AgentPipelinePresets.forPreset(presetId)
            } else {
                val baseDefaults = AgentPipelinePresets.defaultCatalogAgents()
                val parsedBuiltins = parsedAgents.filter { !it.isCustom }.associateBy { it.role }
                val customs = parsedAgents.filter { it.isCustom }
                baseDefaults.map { def -> parsedBuiltins[def.role] ?: def } + customs
            }
            val simulation = parseSimulation(obj.optJSONObject("simulation"))
            AgentSetup(
                presetId = presetId,
                executionMode = mode,
                agents = mergedAgents,
                enabledTagTypes = tagSet,
                simulation = simulation
            )
        } catch (_: Exception) {
            AgentPipelinePresets.setupForMode(fallbackMode)
        }
    }

    private fun simulationToJson(sim: SimulationSpec): JSONObject = JSONObject().apply {
        put("ruleModules", JSONArray(sim.ruleModules.map { it.name }))
        if (sim.customMechanicsPrompt.isNotBlank()) {
            put("customMechanicsPrompt", sim.customMechanicsPrompt.take(3000))
        }
        if (sim.customMeters.isNotEmpty()) {
            put("customMeters", JSONArray().apply {
                sim.customMeters.take(12).forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("label", m.label.take(40))
                        put("initialValue", m.initialValue)
                        put("minValue", m.minValue)
                        put("maxValue", m.maxValue)
                        put("unit", m.unit.take(12))
                        if (m.warningThreshold != null) put("warningThreshold", m.warningThreshold)
                        put("warnWhenBelow", m.warnWhenBelow)
                        put("showInHud", m.showInHud)
                    })
                }
            })
        }
    }

    private fun parseSimulation(obj: JSONObject?): SimulationSpec {
        if (obj == null) return SimulationSpec()
        val modulesArr = obj.optJSONArray("ruleModules")
        val modules = if (modulesArr != null) {
            (0 until modulesArr.length()).mapNotNull { idx ->
                SimulationRuleModule.fromString(modulesArr.optString(idx))
            }.distinct()
        } else emptyList()
        val customPrompt = obj.optString("customMechanicsPrompt", "").take(3000)
        val metersArr = obj.optJSONArray("customMeters")
        val meters = mutableListOf<CustomMeterSpec>()
        if (metersArr != null) {
            for (i in 0 until metersArr.length()) {
                val m = metersArr.optJSONObject(i) ?: continue
                val label = m.optString("label", "").trim().take(40)
                if (label.isEmpty()) continue
                val minV = m.optLong("minValue", 0L)
                val maxV = m.optLong("maxValue", 100L).coerceAtLeast(minV + 1L)
                val initV = m.optLong("initialValue", 50L).coerceIn(minV, maxV)
                val warn = if (m.has("warningThreshold") && !m.isNull("warningThreshold")) {
                    m.optLong("warningThreshold")
                } else null
                meters.add(
                    CustomMeterSpec(
                        id = m.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
                        label = label,
                        initialValue = initV,
                        minValue = minV,
                        maxValue = maxV,
                        unit = m.optString("unit", "%").take(12),
                        warningThreshold = warn,
                        warnWhenBelow = m.optBoolean("warnWhenBelow", true),
                        showInHud = m.optBoolean("showInHud", true)
                    )
                )
            }
        }
        return SimulationSpec(
            ruleModules = modules,
            customMechanicsPrompt = customPrompt,
            customMeters = meters.take(12)
        )
    }

    fun encodeCustomLibrary(agents: List<AgentConfig>): String =
        JSONArray().apply {
            agents.filter { it.isCustom }.take(16).forEach { put(agentToJson(it)) }
        }.toString()

    fun decodeCustomLibrary(raw: String?): List<AgentConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            parseAgentsArray(JSONArray(raw)).filter { it.isCustom }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseAgentsArray(arr: JSONArray?): List<AgentConfig> {
        if (arr == null) return emptyList()
        val list = mutableListOf<AgentConfig>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val role = AgentRole.fromString(a.optString("role"))
            val isCustom = a.optBoolean("isCustom", role == AgentRole.CUSTOM)
            list.add(
                AgentConfig(
                    id = a.optString("id").ifBlank { UUID.randomUUID().toString().take(10) },
                    role = role,
                    customName = a.optString("customName", "").take(60),
                    customPrompt = a.optString("customPrompt", "").take(2500),
                    enabled = a.optBoolean("enabled", true),
                    stage = AgentStage.fromString(a.optString("stage").ifBlank { role.defaultStage.name }),
                    providerOverride = a.optString("providerOverride", "").take(40),
                    modelOverride = a.optString("modelOverride", "").take(120),
                    temperatureOverride = if (a.has("temperatureOverride") && !a.isNull("temperatureOverride")) {
                        a.optDouble("temperatureOverride").coerceIn(0.0, 2.0)
                    } else null,
                    detailLevel = a.optInt("detailLevel", 2).coerceIn(1, 3),
                    isCustom = isCustom
                )
            )
        }
        return list.take(20)
    }

    private fun agentToJson(a: AgentConfig): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("role", a.role.name)
        put("customName", a.customName)
        put("customPrompt", a.customPrompt)
        put("enabled", a.enabled)
        put("stage", a.stage.name)
        put("providerOverride", a.providerOverride)
        put("modelOverride", a.modelOverride)
        if (a.temperatureOverride != null) put("temperatureOverride", a.temperatureOverride)
        put("detailLevel", a.detailLevel)
        put("isCustom", a.isCustom)
    }
}

@Keep
@IgnoreExtraProperties
data class NpcDossier(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val faction: String = "",
    val disposition: Long = 0L, // -100..+100
    val mood: String = "Нейтральное",
    val appearance: String = "",
    val stats: Map<String, Long> = emptyMap(),
    val inventory: List<String> = emptyList(),
    val secrets: List<String> = emptyList(),
    val revealedSecretCount: Long = 0L,
    val statsRevealed: Boolean = false,
    val perceptionDc: Long = 13L,
    val trustThreshold: Long = 45L,
    val portraitUri: String = "",
    val notes: String = ""
) {
    fun visibleSecrets(): List<String> =
        secrets.take(revealedSecretCount.toInt().coerceIn(0, secrets.size))

    fun hasHiddenSecrets(): Boolean =
        revealedSecretCount < secrets.size

    fun dispositionLabelRu(): String = when {
        disposition >= 70L -> "Союзник"
        disposition >= 30L -> "Дружелюбие"
        disposition > -20L -> "Нейтралитет"
        disposition > -60L -> "Неприязнь"
        else -> "Вражда"
    }
}

@Keep
@IgnoreExtraProperties
data class QuestItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val stages: List<String> = emptyList(),
    val completedStages: Long = 0L,
    val completed: Boolean = false,
    val rewardText: String = ""
)

@Keep
@IgnoreExtraProperties
data class ChronicleEntry(
    val id: String = "",
    val turnNumber: Long = 0L,
    val title: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class PlayerProfile(
    val race: String = "Человек",
    val charClass: String = "Странник",
    val origin: String = "",
    val appearance: String = "",
    val speechStyle: String = "",
    val equipmentSlots: Map<String, String> = mapOf(
        "Оружие" to "",
        "Броня" to "",
        "Аксессуар" to "",
        "Реликвия" to ""
    ),
    val perks: List<String> = emptyList(),
    val factionReputation: Map<String, Long> = emptyMap(),
    val statusDurations: Map<String, Long> = emptyMap()
)

@Keep
@IgnoreExtraProperties
data class WorldEnvironment(
    val timeOfDay: String = "Сумерки",
    val dayCount: Long = 1L,
    val weather: String = "Ясно",
    val worldLaws: String = "",
    val economyNotes: String = ""
)
