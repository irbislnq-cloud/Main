package com.example.data.ai

import android.content.Context
import com.example.data.local.AppLocalSettings
import com.example.data.model.AgentConfig
import com.example.data.model.AgentRole
import com.example.data.model.AgentStage
import com.example.data.model.LlmProvider
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.diagnostics.AppLogBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AgentPipelineEvent {
    data class StageChanged(
        val stageLabel: String,
        val activeAgentNames: List<String>
    ) : AgentPipelineEvent

    data class TextChunk(val accumulatedText: String) : AgentPipelineEvent

    data class Completed(val result: PipelineExecutionResult) : AgentPipelineEvent
}

data class PipelineExecutionResult(
    val finalRawText: String,
    val agentTrace: List<String>,
    val postRpgDelta: RpgUpdateDelta?,
    val auditNotes: List<String>,
    val triggeredCollapsedMode: Boolean,
    val triggeredLoreTitles: List<String> = emptyList()
)

private const val PIPELINE_TAG = "AgentPipelineEngine"

class AgentPipelineEngine(
    private val llmRouter: LlmRouterService
) {

    fun executeTurn(
        chat: RpChat,
        messages: List<RpMessage>,
        settings: AppLocalSettings,
        overrideInstruction: String? = null,
        deviceContext: Context? = null
    ): Flow<AgentPipelineEvent> = flow {
        val setup = chat.agentSetup()
        val enabledAgents = setup.agents.filter { it.enabled }
        val primaryProvider = chat.providerEnum()
        val primaryModel = chat.modelId.ifBlank {
            primaryProvider.suggestedModels.firstOrNull() ?: "openai"
        }
        val baseParams = chat.generationParams()
        val primaryKey = settings.configFor(primaryProvider).apiKey

        val (collapsed, collapseReason) = LocalRamWatchdog.shouldCollapsePipelineForDevice(
            context = deviceContext,
            provider = primaryProvider,
            apiKey = primaryKey,
            mode = setup.executionMode,
            preset = settings.localModelProfile.ramPreset
        )

        val trace = mutableListOf<String>()
        if (collapseReason != null) {
            trace += collapseReason
        }

        val preAgents = enabledAgents.filter { it.stage == AgentStage.PRE_PLANNING }
        val writerAgent = enabledAgents.firstOrNull { it.role == AgentRole.PROSE_WRITER }
            ?: enabledAgents.firstOrNull { it.stage == AgentStage.PRIMARY_PROSE }
        val refineAgents = enabledAgents.filter { it.stage == AgentStage.REFINEMENT }
        val taggerEnabled = setup.isRoleEnabled(AgentRole.SEMANTIC_TAGGER)
        val mathEnabled = setup.isRoleEnabled(AgentRole.MATH_ARBITER)

        var planningDirective: String? = null

        // Stage 1: Multi-Agent Council (PRE_PLANNING) when not collapsed
        if (!collapsed && preAgents.isNotEmpty()) {
            emit(
                AgentPipelineEvent.StageChanged(
                    stageLabel = "Совет агентов планирует сцену...",
                    activeAgentNames = preAgents.map { it.displayName() }
                )
            )
            val councilAgent = preAgents.firstOrNull { it.providerOverride.isNotBlank() } ?: preAgents.first()
            val councilProvider = resolveAgentProvider(councilAgent, primaryProvider, settings)
            val councilModel = resolveAgentModel(councilAgent, councilProvider, primaryModel)
            val councilTemp = councilAgent.temperatureOverride ?: 0.55

            val councilRolesSummary = preAgents.joinToString("\n") { agent ->
                val detailHint = when (agent.detailLevel) {
                    1 -> "кратко"
                    3 -> "максимально подробно"
                    else -> "сбалансированно"
                }
                val baseRole = when (agent.role) {
                    AgentRole.PLOT_ARCHITECT -> "Сценарист: наметь 2-3 бита сцены, темп, интригу и возможный поворот."
                    AgentRole.RPG_MASTER -> "РПГ-система: укажи какие проверки DC ([[check:Стат:DC|...]]), изменения HP/MP/лута или квеста уместны."
                    AgentRole.NPC_PSYCHOLOGIST -> "Психолог NPC: раскрой скрытые мотивы присутствующих NPC и сдвиг доверия ([NPC_UPDATE])."
                    else -> "${agent.displayName()}: ${agent.customPrompt.take(300)}"
                }
                "- $baseRole ($detailHint)"
            }
            val recentTurnText = messages.takeLast(4).joinToString("\n") { m ->
                "${m.speakerName.ifBlank { m.role }}: ${RpgAndThinkParser.stripThinkingForPrompt(m.activeContent()).take(240)}"
            }
            val councilSystem = "Ты — закрытый Совет Агентов ролевой студии LoreCraft. Твой ответ увидит только Писатель, не игрок."
            val councilUser = """
                Мир: ${chat.title} (${chat.rpModeEnum().labelRu})
                Текущая локация: ${chat.rpgState.currentLocation()?.name ?: "Не указана"}
                Задачи совета:
                $councilRolesSummary

                Последние сообщения:
                $recentTurnText

                Дай компактную режиссёрскую директиву (4-7 пунктов на русском языке) для следующего ответа: драматургия, действия NPC, проверки/последствия, важные детали.
            """.trimIndent()

            val councilRes = runCatching {
                llmRouter.completeSinglePrompt(
                    provider = councilProvider,
                    modelId = councilModel,
                    systemPrompt = councilSystem,
                    userPrompt = councilUser,
                    settings = settings,
                    maxTokens = 360,
                    temperature = councilTemp,
                    deviceContext = deviceContext
                )
            }.getOrNull()

            val planText = councilRes?.getOrNull()?.trim()
            if (!planText.isNullOrBlank()) {
                planningDirective = planText
                trace += "Совет (${preAgents.joinToString(", ") { it.role.shortRu }}): $councilModel"
                val singleLinePlan = planText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
                if (singleLinePlan.isNotBlank()) {
                    trace += "[Совет] ${singleLinePlan.take(145)}"
                }
            }
        } else if (preAgents.isNotEmpty()) {
            trace += "Единый промпт (${preAgents.joinToString(", ") { it.role.shortRu }})"
        }

        // Stage 2: Primary Prose Generation
        val writerProvider = resolveAgentProvider(writerAgent, primaryProvider, settings)
        val writerModel = resolveAgentModel(writerAgent, writerProvider, primaryModel)
        val writerParams = if (writerAgent?.temperatureOverride != null) {
            baseParams.copy(temperature = writerAgent.temperatureOverride)
        } else {
            baseParams
        }
        emit(
            AgentPipelineEvent.StageChanged(
                stageLabel = "${writerAgent?.displayName() ?: "Писатель"} пишет сцену...",
                activeAgentNames = (listOfNotNull(writerAgent) + refineAgents).map { it.displayName() }
            )
        )

        val builtContext = ContextPromptBuilder.build(
            chat = chat,
            allMessages = messages,
            overrideInstruction = overrideInstruction,
            settings = settings,
            agentSetup = setup,
            planningDirective = planningDirective
        )
        val triggeredLoreTitles = builtContext.triggeredLoreEntries.map { it.title }.distinct()
        if (triggeredLoreTitles.isNotEmpty()) {
            trace += "[Лор] ${triggeredLoreTitles.joinToString(", ").take(145)}"
        }

        val writerBuffer = StringBuilder()
        llmRouter.streamBuiltContext(
            provider = writerProvider,
            modelId = writerModel,
            context = builtContext,
            params = writerParams,
            settings = settings,
            deviceContext = deviceContext
        ).collect { soFar ->
            writerBuffer.clear()
            writerBuffer.append(soFar)
            emit(AgentPipelineEvent.TextChunk(soFar))
        }

        var workingText = writerBuffer.toString().trim()
        trace += "${writerAgent?.role?.shortRu ?: "Писатель"}: $writerModel"

        // Stage 3: Optional Refinement pass if user explicitly set MULTI_STAGE and configured custom/override refiner
        val dedicatedRefiner = refineAgents.firstOrNull {
            it.providerOverride.isNotBlank() || it.modelOverride.isNotBlank()
        }
        if (!collapsed && setup.executionMode == com.example.data.model.PipelineExecutionMode.MULTI_STAGE &&
            dedicatedRefiner != null && workingText.isNotBlank()
        ) {
            emit(
                AgentPipelineEvent.StageChanged(
                    stageLabel = "${dedicatedRefiner.displayName()} углубляет детали...",
                    activeAgentNames = listOf(dedicatedRefiner.displayName())
                )
            )
            val refProvider = resolveAgentProvider(dedicatedRefiner, writerProvider, settings)
            val refModel = resolveAgentModel(dedicatedRefiner, refProvider, writerModel)
            val refDirective = when (dedicatedRefiner.role) {
                AgentRole.ANATOMY_DETAILER ->
                    "Углуби тактильную сенсорику, анатомически точные позы, дыхание, мимику и физику окружения без искажения событий."
                AgentRole.ADULT_EXPANDER ->
                    "Разверни зрелую, напряжённую и чувственную сцену с максимальной выразительностью, сохранив характеры и разметку [[...]]."
                else -> dedicatedRefiner.customPrompt.ifBlank {
                    "Отредактируй и обогати текст, сохранив все сюжетные факты и разметку."
                }
            }
            val refUser = """
                Инструкция редактора: $refDirective
                Сохраняй все существующие теги [[...]] и скрытые строки [...] в конце!

                Исходный черновик:
                $workingText
            """.trimIndent()
            val refinedRes = llmRouter.completeSinglePrompt(
                provider = refProvider,
                modelId = refModel,
                systemPrompt = "Ты — литературный редактор-детализатор студии LoreCraft. Верни улучшенную версию текста на русском языке.",
                userPrompt = refUser,
                settings = settings,
                maxTokens = writerParams.maxTokens.coerceIn(512, 3072),
                temperature = dedicatedRefiner.temperatureOverride ?: 0.8,
                deviceContext = deviceContext
            )
            refinedRes.getOrNull()?.takeIf { it.isNotBlank() }?.let { refined ->
                workingText = refined
                emit(AgentPipelineEvent.TextChunk(refined))
                trace += "${dedicatedRefiner.role.shortRu}: $refModel"
            }
        } else if (refineAgents.isNotEmpty()) {
            trace += "Слой: ${refineAgents.joinToString(", ") { it.role.shortRu }}"
        }

        // Stage 4: Local Post-Processing (Math Arbiter + Semantic Markup Fallback)
        val parsedBeforePost = RpgAndThinkParser.parseForDisplay(workingText)
        val auditNotes = mutableListOf<String>()
        var postDelta = parsedBeforePost.rpgDelta

        if (mathEnabled && postDelta != null) {
            val proposedState = postDelta.applyTo(
                state = chat.rpgState,
                currentTurn = messages.size.toLong(),
                customMeters = setup.simulation.customMeters
            )
            val (_, mathNotes) = MathEngine.validateAndClampRpgState(chat.rpgState, proposedState)
            auditNotes += mathNotes
            trace += "[Математик] баланс параметров/бросков"
        }

        if (taggerEnabled && setup.enabledTagTypes.isNotEmpty()) {
            // Re-enrich only the visible prose, keeping <think> and machine tags intact
            val enrichedVisible = SemanticMarkupEngine.enrichPlainTextFallback(
                text = parsedBeforePost.visibleMarkdown,
                rpgState = chat.rpgState,
                loreEntries = chat.loreEntries,
                enabledTypes = setup.enabledTagTypes
            )
            if (enrichedVisible != parsedBeforePost.visibleMarkdown) {
                val thinkPrefix = parsedBeforePost.thinkingText?.let { "<think>$it</think>\n\n" }.orEmpty()
                val deltaSuffix = postDelta?.rawTag?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
                workingText = "$thinkPrefix$enrichedVisible$deltaSuffix".trim()
                postDelta = RpgAndThinkParser.parseForDisplay(workingText).rpgDelta ?: postDelta
            }
            val spanCount = SemanticMarkupEngine.extractUniqueSpans(workingText).size
            trace += "[Разметчик] $spanCount тег(ов)"
        }

        // Enforce Firestore string-list bounds (<= 16 entries, <= 155 chars each)
        val boundedTrace = trace
            .map { it.replace('\n', ' ').trim().take(155) }
            .filter { it.isNotBlank() }
            .take(16)

        AppLogBuffer.d(PIPELINE_TAG, "Pipeline complete (${boundedTrace.joinToString(" | ")})")

        emit(
            AgentPipelineEvent.Completed(
                PipelineExecutionResult(
                    finalRawText = workingText,
                    agentTrace = boundedTrace,
                    postRpgDelta = postDelta,
                    auditNotes = auditNotes,
                    triggeredCollapsedMode = collapsed,
                    triggeredLoreTitles = triggeredLoreTitles
                )
            )
        )
    }

    suspend fun runDetailerRewrite(
        chat: RpChat,
        targetText: String,
        settings: AppLocalSettings,
        focusHint: String = "Углуби сенсорику, анатомию движений, мимику, тактильные детали и атмосферу сцены.",
        deviceContext: Context? = null
    ): Result<String> {
        val setup = chat.agentSetup()
        val detailAgent = setup.agents.firstOrNull { it.role == AgentRole.ANATOMY_DETAILER }
            ?: setup.agents.firstOrNull { it.role == AgentRole.ADULT_EXPANDER }
        val primaryProvider = chat.providerEnum()
        val provider = resolveAgentProvider(detailAgent, primaryProvider, settings)
        val model = resolveAgentModel(detailAgent, provider, chat.modelId)
        val cleanInput = RpgAndThinkParser.stripMachineTagsOnly(
            RpgAndThinkParser.stripThinkingForPrompt(targetText)
        )
        val adultSuffix = if (setup.hasAdultAgent()) {
            " Допустим смелый, зрелый и чувственный тон 18+ без обрыва сцен."
        } else ""

        val res = llmRouter.completeSinglePrompt(
            provider = provider,
            modelId = model,
            systemPrompt = "Ты — Агент детализации и анатомии студии LoreCraft.$adultSuffix Отвечай на русском языке, сохраняя все [[loc:...]], [[npc:...]] и прочие теги.",
            userPrompt = "$focusHint\n\nПерепиши и детализируй этот фрагмент сцены:\n$cleanInput",
            settings = settings,
            maxTokens = 1400,
            temperature = detailAgent?.temperatureOverride ?: 0.82,
            deviceContext = deviceContext
        )
        return res.map { rewritten ->
            SemanticMarkupEngine.enrichPlainTextFallback(
                text = rewritten,
                rpgState = chat.rpgState,
                loreEntries = chat.loreEntries,
                enabledTypes = setup.enabledTagTypes
            )
        }
    }

    private fun resolveAgentProvider(
        agent: AgentConfig?,
        fallback: LlmProvider,
        settings: AppLocalSettings
    ): LlmProvider {
        val raw = agent?.providerOverride?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        val parsed = runCatching { LlmProvider.valueOf(raw) }.getOrNull() ?: return fallback
        return if (!parsed.requiresApiKey || settings.configFor(parsed).apiKey.isNotBlank()) parsed else fallback
    }

    private fun resolveAgentModel(
        agent: AgentConfig?,
        provider: LlmProvider,
        fallbackModel: String
    ): String {
        val custom = agent?.modelOverride?.trim().orEmpty()
        if (custom.isNotBlank()) return custom
        return fallbackModel.ifBlank { provider.suggestedModels.firstOrNull() ?: "openai" }
    }
}
