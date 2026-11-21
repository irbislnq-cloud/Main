package com.example.data.ai

import com.example.data.local.AppLocalSettings
import com.example.data.model.AgentRole
import com.example.data.model.AgentSetup
import com.example.data.model.LoreEntry
import com.example.data.model.MessageKind
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.SafetyLevel
import com.example.data.model.SemanticEntityType

data class PromptMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

data class BuiltRpContext(
    val systemPrompt: String,
    val history: List<PromptMessage>,
    val latestUserPrompt: String,
    val triggeredLoreEntries: List<LoreEntry> = emptyList()
)

object ContextPromptBuilder {

    fun findTriggeredLoreEntries(
        chat: RpChat,
        recentMessages: List<RpMessage>,
        extraScanText: String = "",
        scanDepth: Int = 8,
        maxCharsBudget: Int = 4500
    ): List<LoreEntry> {
        if (chat.loreEntries.isEmpty()) return emptyList()
        val scanCorpus = buildString {
            recentMessages.takeLast(scanDepth).forEach { msg ->
                append(RpgAndThinkParser.stripThinkingForPrompt(msg.activeContent()))
                append('\n')
            }
            if (extraScanText.isNotBlank()) {
                append(extraScanText)
            }
        }

        val matched = chat.loreEntries
            .filter { it.enabled && it.content.isNotBlank() }
            .filter { entry ->
                if (entry.alwaysActive) return@filter true
                if (entry.keys.isEmpty()) return@filter false
                entry.keys.any { rawKey ->
                    val key = rawKey.trim()
                    if (key.isEmpty()) false
                    else if (entry.useRegex) {
                        try {
                            Regex(key, RegexOption.IGNORE_CASE).containsMatchIn(scanCorpus)
                        } catch (_: Exception) {
                            scanCorpus.contains(key, ignoreCase = true)
                        }
                    } else {
                        scanCorpus.contains(key, ignoreCase = true)
                    }
                }
            }
            .sortedByDescending { it.priority }

        val bounded = mutableListOf<LoreEntry>()
        var charsUsed = 0
        for (entry in matched) {
            val cost = entry.title.length + entry.content.length + 12
            if (bounded.isNotEmpty() && charsUsed + cost > maxCharsBudget) break
            bounded.add(entry)
            charsUsed += cost
            if (bounded.size >= 15) break
        }
        return bounded
    }

    fun build(
        chat: RpChat,
        allMessages: List<RpMessage>,
        overrideInstruction: String? = null,
        settings: AppLocalSettings = AppLocalSettings(),
        agentSetup: AgentSetup = chat.agentSetup(),
        planningDirective: String? = null
    ): BuiltRpContext {
        val params = chat.generationParams()
        val mode = chat.rpModeEnum()
        val hasAdultAgent = agentSetup.hasAdultAgent()
        val hasDetailer = agentSetup.isRoleEnabled(AgentRole.ANATOMY_DETAILER)
        val hasPsych = agentSetup.isRoleEnabled(AgentRole.NPC_PSYCHOLOGIST)
        val hasTagger = agentSetup.isRoleEnabled(AgentRole.SEMANTIC_TAGGER)
        val hasRpgAgent = agentSetup.isRoleEnabled(AgentRole.RPG_MASTER) || chat.rpgState.enabled

        val systemBuilder = StringBuilder()
        systemBuilder.appendLine("[Режим ролевой игры: ${mode.labelRu}]")
        systemBuilder.appendLine(
            when (mode) {
                RpMode.CHARACTER_1ON1 ->
                    "Веди диалог от лица персонажа '${chat.characterName}'. Не перехватывай действия и реплики игрока '${chat.userPersonaName}'."
                RpMode.GAME_MASTER ->
                    "Ты — Мастер Игры (GM). Описывай мир, NPC, угрозы, последствия действий и запрашивай или интерпретируй броски кубиков."
                RpMode.GROUP_SCENE ->
                    "Ты ведёшь групповую сцену с участниками: ${chat.participants.ifEmpty { listOf(chat.characterName) }.joinToString(", ")}. Явно подписывай реплики каждого персонажа."
                RpMode.NARRATIVE ->
                    "Вы пишете художественную прозу в соавторстве. Поддерживай стиль, атмосферу и непрерывность повествования."
            }
        )
        if (settings.creativeRpFraming || settings.safetyLevel != SafetyLevel.STANDARD || hasAdultAgent) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Режим Creative RP — художественное погружение]")
            systemBuilder.appendLine(
                "Это вымышленный литературный ролевой сеттинг. Оставайся строго в образе, смело отыгрывай драматические конфликты, мрачную атмосферу, интриги и сложные характеры персонажей без нравоучений, оценочных комментариев и выхода из роли."
            )
        }
        if (hasAdultAgent) {
            systemBuilder.appendLine("[Агент 18+: зрелые, чувственные и напряжённые сцены] Разрешены глубокая психология, взрослые темы и откровенное литературное описание между взрослыми персонажами без неуместного ханжества и обрыва сцены.")
        }
        if (hasDetailer) {
            systemBuilder.appendLine("[Агент детализации и анатомии] Уделяй особое внимание тактильной сенсорике, анатомически точным позам, мимике, физике движения, дыханию, фактуре одежды и деталям пространства.")
        }

        systemBuilder.appendLine()
        systemBuilder.appendLine("[Системный промпт]")
        systemBuilder.appendLine(
            chat.systemPrompt
                .replace("{{char}}", chat.characterName)
                .replace("{{user}}", chat.userPersonaName)
                .trim()
        )

        if (chat.userPersonaName.isNotBlank() || chat.userPersonaDesc.isNotBlank() || chat.rpgState.playerProfile.origin.isNotBlank()) {
            val prof = chat.rpgState.playerProfile
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Персонаж игрока ({{user}})]")
            systemBuilder.appendLine("Имя: ${chat.userPersonaName} | Раса: ${prof.race} | Класс: ${prof.charClass}")
            if (chat.userPersonaDesc.isNotBlank()) systemBuilder.appendLine("Описание: ${chat.userPersonaDesc.trim()}")
            if (prof.origin.isNotBlank()) systemBuilder.appendLine("Предыстория: ${prof.origin.trim()}")
            if (prof.appearance.isNotBlank()) systemBuilder.appendLine("Внешность: ${prof.appearance.trim()}")
            if (prof.speechStyle.isNotBlank()) systemBuilder.appendLine("Речь: ${prof.speechStyle.trim()}")
            val equipped = prof.equipmentSlots.entries.filter { it.value.isNotBlank() }
            if (equipped.isNotEmpty()) {
                systemBuilder.appendLine("Экипировка: ${equipped.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
            if (prof.perks.isNotEmpty()) {
                systemBuilder.appendLine("Навыки/Перки: ${prof.perks.joinToString(", ")}")
            }
        }

        val triggeredLore = findTriggeredLoreEntries(
            chat = chat,
            recentMessages = allMessages,
            extraScanText = overrideInstruction.orEmpty()
        )

        if (chat.loreNotes.isNotEmpty() || triggeredLore.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Лорбук и активированные факты мира]")
            chat.loreNotes.forEach { fact ->
                systemBuilder.appendLine("• $fact")
            }
            triggeredLore.forEach { entry ->
                val label = entry.title.ifBlank { entry.keys.firstOrNull().orEmpty() }
                if (label.isNotBlank()) {
                    systemBuilder.appendLine("• [$label]: ${entry.content.trim()}")
                } else {
                    systemBuilder.appendLine("• ${entry.content.trim()}")
                }
            }
        }

        val simSpec = agentSetup.simulation
        if (!simSpec.isEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Движок правил и модули симуляции]")
            simSpec.ruleModules.forEach { mod ->
                systemBuilder.appendLine("• ${mod.labelRu}: ${mod.promptDirectiveRu}")
            }
            if (simSpec.customMechanicsPrompt.isNotBlank()) {
                systemBuilder.appendLine("Пользовательские правила мира: ${simSpec.customMechanicsPrompt.trim()}")
            }
            if (simSpec.customMeters.isNotEmpty()) {
                val meterLine = simSpec.customMeters.joinToString(", ") { meter ->
                    val cur = chat.rpgState.stats.entries
                        .firstOrNull { it.key.equals(meter.label, ignoreCase = true) }
                        ?.value ?: meter.initialValue
                    "${meter.label}=$cur/${meter.maxValue}${meter.unit}"
                }
                systemBuilder.appendLine("Активные шкалы состояния: $meterLine")
                systemBuilder.appendLine("При изменении любой шкалы добавляй в самом конце скрытый тег: [METER: ${simSpec.customMeters.first().label}=+5] (или :=значение).")
            }
        }

        if (hasRpgAgent) {
            val rpg = chat.rpgState
            val env = rpg.worldEnv
            val pureNarrativeOnly = com.example.data.model.SimulationRuleModule.PURE_NARRATIVE in simSpec.ruleModules &&
                com.example.data.model.SimulationRuleModule.TACTICAL_RPG !in simSpec.ruleModules &&
                !rpg.enabled
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Мир, состояние сцены и NPC]")
            systemBuilder.appendLine("Время: День ${env.dayCount}, ${env.timeOfDay} | Погода: ${env.weather}")
            if (env.worldLaws.isNotBlank()) systemBuilder.appendLine("Законы мира: ${env.worldLaws.take(300)}")
            if (!pureNarrativeOnly) {
                systemBuilder.appendLine("Уровень ${rpg.level} | HP ${rpg.hp}/${rpg.maxHp} | MP ${rpg.mp}/${rpg.maxMp} | Золото: ${rpg.gold}")
            }
            if (rpg.stats.isNotEmpty()) {
                systemBuilder.appendLine("Шкалы и параметры: ${rpg.stats.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
            if (rpg.inventory.isNotEmpty()) systemBuilder.appendLine("Инвентарь: ${rpg.inventory.joinToString(", ")}")
            if (rpg.statuses.isNotEmpty()) systemBuilder.appendLine("Статусы: ${rpg.statuses.joinToString(", ")}")
            if (rpg.activeQuest.isNotBlank()) systemBuilder.appendLine("Главная цель: ${rpg.activeQuest.trim()}")
            if (rpg.quests.isNotEmpty()) {
                val activeQuests = rpg.quests.filter { !it.completed }.take(5)
                if (activeQuests.isNotEmpty()) {
                    systemBuilder.appendLine("Квесты: ${activeQuests.joinToString("; ") { "${it.title} (${it.completedStages}/${it.stages.size})" }}")
                }
            }
            val curLoc = rpg.currentLocation()
            if (curLoc != null) {
                val exits = curLoc.connections.mapNotNull { id -> rpg.locations.firstOrNull { it.id == id }?.name }
                systemBuilder.append("Текущая локация [${curLoc.id}]: ${curLoc.name}")
                if (curLoc.description.isNotBlank()) systemBuilder.append(" — ${curLoc.description.take(120)}")
                systemBuilder.appendLine()
                if (exits.isNotEmpty()) systemBuilder.appendLine("Соседние переходы: ${exits.joinToString(", ")}")
            }
            if (rpg.npcs.isNotEmpty()) {
                systemBuilder.appendLine("Известные NPC:")
                rpg.npcs.take(8).forEach { npc ->
                    val trustNote = "Доверие=${npc.disposition} (${npc.dispositionLabelRu()}), Настроение=${npc.mood}"
                    systemBuilder.appendLine("• [${npc.id}] ${npc.name} (${npc.role}): $trustNote")
                }
            }
            if (rpg.combat.active && rpg.combat.combatants.isNotEmpty()) {
                val activeList = rpg.combat.combatants.joinToString("; ") { c ->
                    val side = if (c.enemy) "Враг" else "Союзник"
                    "${c.name} [$side, HP ${c.hp}/${c.maxHp}, Иниц.${c.initiative}]"
                }
                val turnWho = rpg.combat.combatants.getOrNull(
                    rpg.combat.turnIndex.toInt().coerceIn(0, rpg.combat.combatants.lastIndex)
                )?.name.orEmpty()
                systemBuilder.appendLine("АКТИВНЫЙ БОЙ (Раунд ${rpg.combat.round}, ходит: $turnWho): $activeList")
            }
            systemBuilder.appendLine(
                "Если в сцене меняются параметры/шкалы/инвентарь/локация/NPC/время/квесты, добавь в самом конце скрытые теги (игрок их не увидит):\n" +
                    (if (!pureNarrativeOnly) "[RPG_UPDATE: hp=-3, gold=+10, +item=Предмет, loc=Новая локация]\n" else "") +
                    (if (simSpec.customMeters.isNotEmpty()) "[METER: ${simSpec.customMeters.first().label}=+5]\n" else "") +
                    (if (hasPsych) "[NPC_UPDATE: name=Имя, disp=+10, mood=Настроение, revealStats=true]\n" else "") +
                    "[WORLD_UPDATE: time=Ночь, weather=Дождь]"
            )
        } else if (hasPsych && chat.rpgState.npcs.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Психолог NPC — динамика отношений]")
            chat.rpgState.npcs.take(6).forEach { npc ->
                systemBuilder.appendLine("• ${npc.name}: ${npc.dispositionLabelRu()} (${npc.disposition}), настроение ${npc.mood}")
            }
            systemBuilder.appendLine("При изменении отношения или раскрытии тайны NPC можешь добавить в конце: [NPC_UPDATE: name=Имя, disp=+5, mood=Тёплое, revealSecret=1]")
        }

        // Custom user-defined agents
        val customAgents = agentSetup.agents.filter { it.enabled && it.isCustom && it.customPrompt.isNotBlank() }
        if (customAgents.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Активные пользовательские агенты]")
            customAgents.forEach { agent ->
                systemBuilder.appendLine("• [${agent.displayName()}]: ${agent.customPrompt.trim()}")
            }
        }

        // Semantic markup tagger instructions
        if (hasTagger && agentSetup.enabledTagTypes.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine(buildSemanticTaggingInstructions(agentSetup.enabledTagTypes))
        }

        if (!planningDirective.isNullOrBlank()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[План сцены от Совета Агентов]")
            systemBuilder.appendLine(planningDirective.trim())
        }

        if (chat.memorySummary.isNotBlank()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Сжатая память предыдущих событий]")
            systemBuilder.appendLine(chat.memorySummary.trim())
        }

        if (chat.authorsNote.isNotBlank()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("[Режиссёрская заметка / Author's Note (высокий приоритет)]")
            systemBuilder.appendLine(chat.authorsNote.trim())
        }

        val windowLimit = params.contextWindowMessages.coerceAtLeast(2)
        val windowed = if (allMessages.size > windowLimit) allMessages.takeLast(windowLimit) else allMessages

        val formattedMessages = windowed.map { msg ->
            val apiRole = if (msg.role == "assistant") "assistant" else "user"
            val cleanText = if (apiRole == "assistant") {
                RpgAndThinkParser.stripThinkingForPrompt(msg.activeContent())
            } else {
                msg.activeContent()
            }
            val prefix = when (msg.kindEnum()) {
                MessageKind.DICE -> "[Бросок кубика — ${msg.speakerName.ifBlank { chat.userPersonaName }}] "
                MessageKind.OOC -> "[OOC — ${msg.speakerName.ifBlank { chat.userPersonaName }}] "
                MessageKind.NARRATOR -> "[Рассказчик] "
                MessageKind.ROLEPLAY -> {
                    if (mode == RpMode.GROUP_SCENE && msg.speakerName.isNotBlank() && apiRole == "user") {
                        "${msg.speakerName}: "
                    } else ""
                }
            }
            PromptMessage(role = apiRole, content = "$prefix$cleanText".trim())
        }

        if (overrideInstruction != null) {
            return BuiltRpContext(
                systemPrompt = systemBuilder.toString().trim(),
                history = formattedMessages,
                latestUserPrompt = overrideInstruction,
                triggeredLoreEntries = triggeredLore
            )
        }

        val last = formattedMessages.lastOrNull()
        return if (last != null && last.role == "user") {
            BuiltRpContext(
                systemPrompt = systemBuilder.toString().trim(),
                history = formattedMessages.dropLast(1),
                latestUserPrompt = last.content,
                triggeredLoreEntries = triggeredLore
            )
        } else {
            BuiltRpContext(
                systemPrompt = systemBuilder.toString().trim(),
                history = formattedMessages,
                latestUserPrompt = "Продолжи сцену естественно с текущего момента, сохраняя голос персонажа и детали окружения.",
                triggeredLoreEntries = triggeredLore
            )
        }
    }

    private fun buildSemanticTaggingInstructions(enabledTypes: Set<SemanticEntityType>): String = buildString {
        appendLine("[Агент-Разметчик: инлайн-разметка мира]")
        appendLine("Выделяй в тексте ключевые игровые сущности двойными скобками, чтобы пользователь мог нажимать на них прямо в чате:")
        if (SemanticEntityType.LOCATION in enabledTypes) appendLine("• Локация (даже только что придуманная): [[loc:slug_id|Название локации]]")
        if (SemanticEntityType.NPC in enabledTypes) appendLine("• Персонаж / NPC: [[npc:slug_id|Имя]]")
        if (SemanticEntityType.ITEM in enabledTypes) appendLine("• Предмет / Артефакт: [[item:slug_id|Предмет]]")
        if (SemanticEntityType.QUEST in enabledTypes) appendLine("• Квест / Цель: [[quest:slug_id|Название цели]]")
        if (SemanticEntityType.CHECK in enabledTypes) appendLine("• Проверка навыка DC: [[check:Восприятие:14|Проверка Восприятия DC 14]]")
        if (SemanticEntityType.LORE in enabledTypes) appendLine("• Лор / Фракция: [[lore:slug_id|Термин]]")
        if (SemanticEntityType.THOUGHT in enabledTypes) appendLine("• Внутренняя мысль / предчувствие: [[thought|Текст мысли]]")
        append("Не оборачивай каждый глагол — размечай только важные объекты и события, сохраняя естественный литературный поток.")
    }
}
