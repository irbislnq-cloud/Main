package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.ContextPromptBuilder
import com.example.data.ai.LlmRouterService
import com.example.data.ai.RpgAndThinkParser
import com.example.data.export.JsonExportManager
import com.example.data.local.AppLocalSettings
import com.example.data.local.cache.OfflineLocalRpRepository
import com.example.data.local.cache.OfflineRpDatabase
import com.example.data.model.CombatState
import com.example.data.model.Combatant
import com.example.data.model.FreeAiPresets
import com.example.data.model.FreeModelCatalog
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.MessageKind
import com.example.data.model.ModelTag
import com.example.data.model.PresetScenarios
import com.example.data.model.RpChat
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.data.model.SafetyLevel
import com.example.data.model.WorldLocationNode
import com.example.diagnostics.CrashGuard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LoreCraft", appName)
    }

    @Test
    fun `launch MainActivity without crash`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(!activity.isFinishing)
            }
        }
    }

    @Test
    fun `free ai providers and fallback chain rotate cleanly`() {
        val freeProviders = LlmProvider.freeProviders()
        assertTrue(freeProviders.contains(LlmProvider.POLLINATIONS))
        assertTrue(freeProviders.contains(LlmProvider.GEMINI_AI_LOGIC))
        assertTrue(freeProviders.contains(LlmProvider.GEMINI_DIRECT))
        assertTrue(freeProviders.contains(LlmProvider.OPENROUTER))
        assertTrue(freeProviders.contains(LlmProvider.GROQ))
        assertTrue(freeProviders.contains(LlmProvider.LOCAL_LLM))
        assertFalse(freeProviders.contains(LlmProvider.OPENAI))

        val firstFallback = FreeAiPresets.nextFallback(
            LlmProvider.GEMINI_AI_LOGIC,
            "gemini-3.8-flash"
        )
        assertEquals("gemini-3.5-flash-lite", firstFallback.modelId)

        val secondFallback = FreeAiPresets.nextFallback(
            LlmProvider.GEMINI_AI_LOGIC,
            "gemini-3.5-flash-lite"
        )
        assertEquals(LlmProvider.GEMINI_DIRECT, secondFallback.provider)

        // Smart fallback when Firebase App Check blocks with 403 -> jumps straight to keyless Pollinations
        val appCheckFallback = FreeAiPresets.nextSmartFallback(
            currentProvider = LlmProvider.GEMINI_AI_LOGIC,
            currentModel = "gemini-3.8-flash",
            isKeyConfigured = { false },
            lastError = "HTTP 403 App Check token is invalid"
        )
        assertEquals(LlmProvider.POLLINATIONS, appCheckFallback.provider)
        assertEquals("openai", appCheckFallback.modelId)

        // Smart fallback skips keyed providers when user has no API keys
        val noKeyRotation = FreeAiPresets.nextSmartFallback(
            currentProvider = LlmProvider.POLLINATIONS,
            currentModel = "openai",
            isKeyConfigured = { false },
            lastError = "HTTP 402"
        )
        assertEquals(LlmProvider.POLLINATIONS, noKeyRotation.provider)
        assertEquals("openai-fast", noKeyRotation.modelId)
    }

    @Test
    fun `llm router resolves pollinations endpoints and parses array or object models`() {
        assertEquals(
            "https://text.pollinations.ai/openai/chat/completions",
            LlmRouterService.resolveChatCompletionsEndpoint(
                LlmProvider.POLLINATIONS,
                "https://text.pollinations.ai/openai"
            )
        )
        assertEquals(
            "https://text.pollinations.ai/models",
            LlmRouterService.resolveModelsEndpoint(
                provider = LlmProvider.POLLINATIONS,
                baseUrl = "https://text.pollinations.ai/openai"
            )
        )
        val pollinationsArray = """[{"name":"openai","aliases":["gpt"]},{"name":"openai-fast"}]"""
        assertEquals(
            listOf("openai", "openai-fast"),
            LlmRouterService.parseModelsResponse(pollinationsArray)
        )
        val openAiObject = """{"data":[{"id":"openrouter/free"},{"id":"google/gemma-4-31b-it:free"}]}"""
        assertEquals(
            listOf("openrouter/free", "google/gemma-4-31b-it:free"),
            LlmRouterService.parseModelsResponse(openAiObject)
        )
    }

    @Test
    fun `rpg update parser applies location node transitions and stats`() {
        val initial = PresetScenarios.starterTemplates()
            .first { it.id == "template_dm_blackharbor" }
            .defaultRpgState
        val rawReply = """
            *Стражник бросает взгляд на ваши доспехи и отступает.*
            [RPG_UPDATE: hp=-3, gold=+15, +item=Отмычка гильдии, loc=Подземелья Маяка]
        """.trimIndent()

        val parsed = RpgAndThinkParser.parseForDisplay(rawReply)
        assertNotNull(parsed.rpgDelta)
        assertFalse(parsed.visibleMarkdown.contains("[RPG_UPDATE"))

        val updated = parsed.rpgDelta!!.applyTo(initial)
        assertEquals(initial.hp - 3L, updated.hp)
        assertEquals(initial.gold + 15L, updated.gold)
        assertTrue(updated.inventory.contains("Отмычка гильдии"))
        assertEquals("Подземелья Маяка", updated.currentLocation()?.name)
        assertTrue(updated.locations.any { it.name == "Подземелья Маяка" && it.visited })
    }

    @Test
    fun `offline room repository seeds presets and persists chats and messages`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OfflineRpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = OfflineLocalRpRepository(db.offlineRpDao())
            val userId = OfflineLocalRpRepository.LOCAL_OFFLINE_USER_ID

            val seedRes = repo.ensureStarterDataIfEmpty(userId)
            assertTrue(seedRes.isSuccess)

            val scenarios = repo.observeScenarios(userId).first()
            assertEquals(PresetScenarios.starterTemplates().size, scenarios.size)

            val gmScenario = scenarios.first { it.defaultRpgState.locations.isNotEmpty() }
            val chatRes = repo.createChatFromScenario(
                userId = userId,
                scenario = gmScenario,
                userPersona = null,
                overrideProvider = LlmProvider.LOCAL_LLM,
                overrideModelId = "local-gguf-q4",
                overrideParams = GenerationParams()
            )
            assertTrue(chatRes.isSuccess)
            val chatId = chatRes.getOrThrow()

            val addRes = repo.addMessage(
                userId = userId,
                chatId = chatId,
                role = "user",
                speakerName = "Странник",
                content = "*Осматривает зал.*",
                messageKind = MessageKind.ROLEPLAY,
                modelUsed = "",
                orderIndex = 1L,
                newTotalCount = 2L
            )
            assertTrue(addRes.isSuccess)

            val msgs = repo.observeMessages(userId, chatId).first()
            assertTrue(msgs.size >= 2)
            assertEquals("*Осматривает зал.*", msgs.last().content)

            val updatedCount = repo.updateAllChatsProvider(
                userId = userId,
                provider = LlmProvider.POLLINATIONS,
                modelId = "openai-fast"
            ).getOrThrow()
            assertTrue(updatedCount >= 1)
            val allChats = repo.observeChats(userId).first()
            assertTrue(allChats.all { it.provider == LlmProvider.POLLINATIONS.name && it.modelId == "openai-fast" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `crash guard formats diagnostics and json export preserves v3 art map and combat`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = CrashGuard.formatCrashReport(
            context = context,
            threadName = "main",
            summary = "IllegalStateException: Test failure",
            throwable = IllegalStateException("Test failure")
        )
        assertTrue(report.contains("LoreCraft"))
        assertTrue(report.contains("IllegalStateException: Test failure"))

        val scenario = RpScenario(
            id = "sc_v3",
            title = "Тест v3",
            portraitUri = "file:///tmp/portrait.jpg",
            backdropUri = "file:///tmp/bg.jpg",
            defaultProvider = LlmProvider.GROQ.name,
            defaultRpgState = RpgState(
                enabled = true,
                currentLocationId = "loc_1",
                locations = listOf(
                    WorldLocationNode(id = "loc_1", name = "Арена", description = "Каменный круг", visited = true)
                ),
                combat = CombatState(
                    active = true,
                    round = 2L,
                    turnIndex = 0L,
                    combatants = listOf(
                        Combatant(id = "c1", name = "Страж", hp = 14L, maxHp = 20L, initiative = 15L, enemy = true)
                    )
                )
            )
        )
        val jsonObj = JsonExportManager.scenarioToJson(scenario)
        val decoded = JsonExportManager.scenarioFromJson(jsonObj)
        assertEquals("file:///tmp/portrait.jpg", decoded.portraitUri)
        assertEquals("file:///tmp/bg.jpg", decoded.backdropUri)
        assertEquals(LlmProvider.GROQ.name, decoded.defaultProvider)
        assertEquals("Арена", decoded.defaultRpgState.currentLocation()?.name)
        assertTrue(decoded.defaultRpgState.combat.active)
        assertEquals(2L, decoded.defaultRpgState.combat.round)
        assertEquals("Страж", decoded.defaultRpgState.combat.combatants.first().name)
    }

    @Test
    fun `creative rp framing catalog tags and ai scenario expansion parse cleanly`() {
        val chat = RpChat(
            id = "c1",
            title = "Тест",
            rpMode = RpMode.GAME_MASTER.name,
            characterName = "Архонт",
            systemPrompt = "Веди мрачный детектив."
        )
        val settingsMinimal = AppLocalSettings(
            safetyLevel = SafetyLevel.MINIMAL,
            creativeRpFraming = true
        )
        val ctx = ContextPromptBuilder.build(chat = chat, allMessages = emptyList(), settings = settingsMinimal)
        assertTrue(ctx.systemPrompt.contains("Creative RP"))

        val curated = FreeModelCatalog.curated()
        assertTrue(curated.any { it.tags.contains(ModelTag.NO_KEY) && it.tags.contains(ModelTag.UNCENSORED_FRIENDLY) })

        val safetyList = LlmRouterService.buildGeminiSafetySettings(SafetyLevel.MINIMAL)
        assertEquals(4, safetyList.size)

        val aiJson = """
            ```json
            {
              "title": "Шпиль Пепла",
              "subtitle": "Тёмное фэнтези • Выживание",
              "characterName": "Магистр Кайн",
              "systemPrompt": "Ты ведёшь мрачный ролевой мир.",
              "characterBio": "Старый инквизитор с серебряным протезом.",
              "firstMessage": "*Пепел сыплется на булыжник.* «Ты опоздал, путник.»",
              "tags": ["Тёмное фэнтези", "RPG"],
              "loreNotes": ["В городе запрещена магия огня."],
              "loreEntries": [
                {"title": "Орден Пепла", "keys": ["орден", "инквизитор"], "content": "Тайная стража цитадели."}
              ],
              "rpgEnabled": true,
              "stats": {"Сила": 14, "Интеллект": 16},
              "inventory": ["Фонарь с углём", "Старый ключ"],
              "activeQuest": "Найти пропавшего летописца",
              "locations": [
                {"id": "loc_1", "name": "Ворота Пепла", "description": "Вход в город", "connections": ["loc_2"]},
                {"id": "loc_2", "name": "Нижний архив", "description": "Сырые своды", "connections": ["loc_1"]}
              ]
            }
            ```
        """.trimIndent()
        val draft = LlmRouterService.parseExpandedScenarioDraft(
            rawText = aiJson,
            fallbackRpMode = RpMode.GAME_MASTER
        )
        assertEquals("Шпиль Пепла", draft.title)
        assertEquals("Магистр Кайн", draft.characterName)
        assertEquals(1, draft.loreEntries.size)
        assertEquals("Орден Пепла", draft.loreEntries.first().title)
        assertEquals(2, draft.rpgState.locations.size)
        assertEquals("Ворота Пепла", draft.rpgState.currentLocation()?.name)
        assertEquals("Найти пропавшего летописца", draft.rpgState.activeQuest)
        assertTrue(draft.rpgState.inventory.contains("Фонарь с углём"))
        assertEquals(16L, draft.rpgState.stats["Интеллект"])
    }

    @Test
    fun `agent setup codec and custom agent library persist cleanly`() {
        val customAgent = com.example.data.model.AgentConfig(
            id = "custom_poet_1",
            role = com.example.data.model.AgentRole.CUSTOM,
            customName = "Скальд Севера",
            customPrompt = "Добавляй скандинавскую метафорику.",
            enabled = true,
            stage = com.example.data.model.AgentStage.REFINEMENT,
            providerOverride = LlmProvider.GROQ.name,
            modelOverride = "llama-3.3-70b-versatile",
            temperatureOverride = 0.92,
            detailLevel = 3,
            isCustom = true
        )
        val setup = com.example.data.model.AgentSetup(
            presetId = "cinema_18_studio",
            executionMode = com.example.data.model.PipelineExecutionMode.MULTI_STAGE,
            agents = com.example.data.model.AgentPipelinePresets.forPreset("cinema_18_studio") + customAgent,
            enabledTagTypes = setOf(
                com.example.data.model.SemanticEntityType.LOCATION,
                com.example.data.model.SemanticEntityType.NPC
            )
        )
        val encoded = com.example.data.model.AgentSetupCodec.encode(setup)
        val decoded = com.example.data.model.AgentSetupCodec.decode(encoded)
        assertEquals("cinema_18_studio", decoded.presetId)
        assertEquals(com.example.data.model.PipelineExecutionMode.MULTI_STAGE, decoded.executionMode)
        assertTrue(decoded.hasAdultAgent())
        assertTrue(decoded.agents.any { it.isCustom && it.customName == "Скальд Севера" && it.temperatureOverride == 0.92 })
        assertEquals(2, decoded.enabledTagTypes.size)

        val libJson = com.example.data.model.AgentSetupCodec.encodeCustomLibrary(listOf(customAgent))
        val decodedLib = com.example.data.model.AgentSetupCodec.decodeCustomLibrary(libJson)
        assertEquals(1, decodedLib.size)
        assertEquals("Скальд Севера", decodedLib.first().customName)
    }

    @Test
    fun `math engine validates rolls npc fog of war and rpg clamping`() {
        val crit = com.example.data.ai.MathEngine.rollCheck(
            statName = "Восприятие",
            modifier = 2L,
            dc = 25L,
            purpose = "Поиск тайника",
            rollOverride = 20
        )
        assertTrue(crit.success)
        assertTrue(crit.criticalSuccess)

        val npc = com.example.data.model.NpcDossier(
            id = "npc_kain",
            name = "Магистр Кайн",
            disposition = 10L,
            trustThreshold = 45L,
            perceptionDc = 14L,
            secrets = listOf("Хранит ключ от шлюза", "Боится огня", "Бывший алхимик"),
            revealedSecretCount = 0L,
            statsRevealed = false
        )
        val (revealedNpc, percRoll) = com.example.data.ai.MathEngine.resolveNpcPerceptionCheck(
            npc = npc,
            perceptionMod = 1L,
            rollOverride = 20
        )
        assertTrue(percRoll.success)
        assertTrue(revealedNpc.statsRevealed)
        assertEquals(2L, revealedNpc.revealedSecretCount)

        val trusted = com.example.data.ai.MathEngine.applyTrustAutoReveal(
            npc.copy(disposition = 50L)
        )
        assertTrue(trusted.statsRevealed)
        assertEquals(1L, trusted.revealedSecretCount)

        val before = RpgState(enabled = true, hp = 20L, maxHp = 20L, gold = 50L)
        val hallucinatedAfter = before.copy(hp = 999L, gold = -20L)
        val (clamped, notes) = com.example.data.ai.MathEngine.validateAndClampRpgState(before, hallucinatedAfter)
        assertEquals(20L, clamped.hp)
        assertEquals(0L, clamped.gold)
        assertTrue(notes.isNotEmpty())
    }

    @Test
    fun `semantic markup engine parses streaming safe tags and auto registers new locations`() {
        val raw = "Вы входите в [[loc:field_light|Поле светлых]] где ждёт [[npc:elara|Элара]]. [[item:incomplete"
        val streamingSegs = com.example.data.ai.SemanticMarkupEngine.parseSegments(raw, streaming = true)
        val spans = streamingSegs.filterIsInstance<com.example.data.ai.SemanticSegment.SemanticSpan>()
        assertEquals(2, spans.size)
        assertEquals(com.example.data.model.SemanticEntityType.LOCATION, spans[0].type)
        assertEquals("Поле светлых", spans[0].displayText)
        assertEquals(com.example.data.model.SemanticEntityType.NPC, spans[1].type)

        val (updatedState, resolvedLocId) = com.example.data.ai.SemanticMarkupEngine.resolveOrRegisterEntity(
            state = RpgState(enabled = true),
            type = com.example.data.model.SemanticEntityType.LOCATION,
            entityId = "field_light",
            displayText = "Поле светлых"
        )
        assertTrue(updatedState.locations.any { it.id == resolvedLocId && it.name == "Поле светлых" })

        val plainFallback = com.example.data.ai.SemanticMarkupEngine.enrichPlainTextFallback(
            text = "Страж ведёт вас в Поле светлых для проверки Восприятие (DC 14).",
            rpgState = updatedState,
            loreEntries = emptyList(),
            enabledTypes = com.example.data.model.SemanticEntityType.entries.toSet()
        )
        assertTrue(plainFallback.contains("[[loc:"))
    }

    @Test
    fun `rpg parser decodes npc world quest chronicle and faction tags together`() {
        val raw = """
            *Ворота [[loc:gate_north|Северного бастиона]] скрипнули под порывом дождя.*
            [NPC_UPDATE: name=Лира, disp=+12, mood=Тревога, +secret=Она из ордена Тени, reveal_stats=true]
            [WORLD_UPDATE: time=Полночь, day=+1, weather=Ливень]
            [QUEST_UPDATE: title=Сломанная печать, +stage=Найти ключ, stage_done=+1]
            [CHRONICLE: title=Вход в бастион | Лира впустила отряд под проливным дождём | теги=Бастион, Лира]
            [FACTIONS: Стража Города=+5, Культ Пепла=-10]
        """.trimIndent()
        val parsed = RpgAndThinkParser.parseForDisplay(raw)
        val delta = parsed.rpgDelta
        assertNotNull(delta)
        assertFalse(parsed.visibleMarkdown.contains("[NPC_UPDATE"))
        assertFalse(parsed.visibleMarkdown.contains("[WORLD_UPDATE"))
        assertFalse(parsed.visibleMarkdown.contains("[CHRONICLE"))

        val updated = delta!!.applyTo(RpgState(enabled = true), currentTurn = 3L)
        assertEquals("Полночь", updated.worldEnv.timeOfDay)
        assertEquals(2L, updated.worldEnv.dayCount)
        assertEquals("Ливень", updated.worldEnv.weather)
        val lyr = updated.npcs.firstOrNull { it.name == "Лира" }
        assertNotNull(lyr)
        assertTrue(lyr!!.statsRevealed)
        assertEquals(12L, lyr.disposition)
        assertEquals(1, updated.quests.size)
        assertEquals(1L, updated.quests.first().completedStages)
        assertEquals(1, updated.chronicle.size)
        assertEquals(5L, updated.playerProfile.factionReputation["Стража Города"])
    }

    @Test
    fun `room character profile entity dao and viewmodel manage name bio and traits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OfflineRpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = com.example.data.local.cache.CharacterProfileRepository(db.characterProfileDao())
            val vm = com.example.ui.viewmodel.CharacterProfileViewModel(repo)

            val savedId = repo.saveProfile(
                name = "Элара Вейн",
                bio = "Бывший картограф затопленных архивов.",
                personalityTraits = "Наблюдательная, ироничная, верная"
            )
            assertTrue(savedId > 0L)

            val listAfterInsert = repo.allProfiles.first()
            assertEquals(1, listAfterInsert.size)
            assertEquals("Элара Вейн", listAfterInsert.first().name)
            assertEquals(listOf("Наблюдательная", "ироничная", "верная"), listAfterInsert.first().traitList())

            val searchHit = repo.searchProfiles("картограф").first()
            assertEquals(1, searchHit.size)
            val searchMiss = repo.searchProfiles("дракон").first()
            assertTrue(searchMiss.isEmpty())

            var updatedIdFromVm = 0L
            vm.startEditing(listAfterInsert.first())
            vm.saveProfile(
                name = "Элара Вейн",
                bio = "Старший картограф маяка.",
                personalityTraits = "Решительная, спокойная",
                onSaved = { updatedIdFromVm = it }
            )
            repeat(5) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(25)
            }
            assertEquals(savedId, updatedIdFromVm)
            val updated = repo.getById(savedId)
            assertNotNull(updated)
            assertEquals("Старший картограф маяка.", updated!!.bio)

            vm.deleteProfile(savedId)
            repeat(5) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(25)
            }
            assertTrue(repo.allProfiles.first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `local and lan http urls allowed while external http blocked and dead pollinations models removed`() {
        assertTrue(com.example.data.ai.LlmRouterService.isPermittedEndpointUrl("http://127.0.0.1:8080/v1"))
        assertTrue(com.example.data.ai.LlmRouterService.isPermittedEndpointUrl("http://localhost:11434/v1"))
        assertTrue(com.example.data.ai.LlmRouterService.isPermittedEndpointUrl("http://192.168.1.55:11434/v1"))
        assertTrue(com.example.data.ai.LlmRouterService.isPermittedEndpointUrl("https://text.pollinations.ai/openai"))
        assertFalse(com.example.data.ai.LlmRouterService.isPermittedEndpointUrl("http://api.openai.com/v1"))

        val pollCurated = com.example.data.model.FreeModelCatalog.curated()
            .filter { it.provider == com.example.data.model.LlmProvider.POLLINATIONS }
            .map { it.modelId }
        assertTrue(pollCurated.contains("openai"))
        assertTrue(pollCurated.contains("openai-fast"))
        assertFalse(pollCurated.contains("mistral"))
        assertFalse(pollCurated.contains("qwen-coder"))
    }

    @Test
    fun `simulation spec custom meters codec and bounded meter parser work across genres`() {
        val meterSanity = com.example.data.model.CustomMeterSpec(
            id = "m_sanity",
            label = "Рассудок",
            initialValue = 80L,
            minValue = 0L,
            maxValue = 100L,
            unit = "%",
            warningThreshold = 25L,
            warnWhenBelow = true
        )
        val meterClues = com.example.data.model.CustomMeterSpec(
            id = "m_clues",
            label = "Улики",
            initialValue = 1L,
            minValue = 0L,
            maxValue = 10L,
            unit = "",
            warnWhenBelow = false
        )
        val sim = com.example.data.model.SimulationSpec(
            ruleModules = listOf(
                com.example.data.model.SimulationRuleModule.MYSTERY_INVESTIGATION,
                com.example.data.model.SimulationRuleModule.RELATIONSHIP_BONDS
            ),
            customMechanicsPrompt = "Каждая ложная версия снижает Рассудок на 10%.",
            customMeters = listOf(meterSanity, meterClues)
        )
        val setup = com.example.data.model.AgentSetup(
            presetId = "detective_noir",
            simulation = sim
        )
        val encoded = com.example.data.model.AgentSetupCodec.encode(setup)
        val decoded = com.example.data.model.AgentSetupCodec.decode(encoded)
        assertEquals("detective_noir", decoded.presetId)
        assertEquals(2, decoded.simulation.ruleModules.size)
        assertEquals(2, decoded.simulation.customMeters.size)
        assertEquals("Рассудок", decoded.simulation.customMeters.first().label)
        assertEquals("Каждая ложная версия снижает Рассудок на 10%.", decoded.simulation.customMechanicsPrompt)

        val reply = """
            *Туман на набережной скрывает следы экипажа.*
            [METER: Рассудок=-95, Улики=+2, Тепло=75]
        """.trimIndent()
        val parsed = RpgAndThinkParser.parseForDisplay(reply)
        assertNotNull(parsed.rpgDelta)
        assertFalse(parsed.visibleMarkdown.contains("[METER:"))

        val applied = parsed.rpgDelta!!.applyTo(
            state = RpgState(enabled = false, stats = mapOf("Рассудок" to 80L, "Улики" to 1L)),
            customMeters = decoded.simulation.customMeters
        )
        // Bounded clamping prevents negative sanity below minValue=0L
        assertEquals(0L, applied.stats["Рассудок"])
        assertEquals(3L, applied.stats["Улики"])
        assertEquals(75L, applied.stats["Тепло"])
    }

    @Test
    fun `ambient scene resolver multi genre scenarios and router cooldowns work cleanly`() {
        assertEquals(
            com.example.device.AmbientScenePreset.RAIN,
            com.example.device.AmbientSceneResolver.resolve(
                requested = com.example.device.AmbientScenePreset.AUTO,
                weather = "Проливной дождь",
                locationName = "Причал",
                locationDesc = "",
                combatActive = false
            )
        )
        assertEquals(
            com.example.device.AmbientScenePreset.SPACE_CYBER,
            com.example.device.AmbientSceneResolver.resolve(
                requested = com.example.device.AmbientScenePreset.AUTO,
                weather = "Вакуум",
                locationName = "Крио-отсек космической станции",
                locationDesc = "",
                combatActive = false
            )
        )
        assertEquals(
            com.example.device.AmbientScenePreset.COMBAT,
            com.example.device.AmbientSceneResolver.resolve(
                requested = com.example.device.AmbientScenePreset.AUTO,
                weather = "Ясно",
                locationName = "Таверна",
                locationDesc = "",
                combatActive = true
            )
        )

        val templates = PresetScenarios.starterTemplates()
        assertEquals(9, templates.size)
        templates.forEach { sc ->
            assertEquals(LlmProvider.POLLINATIONS.name, sc.defaultProvider)
            assertTrue(sc.defaultModelId.isNotBlank())
            val setup = com.example.data.model.AgentSetupCodec.decode(sc.defaultAgentSetupJson)
            assertTrue(setup.agents.any { it.enabled })
        }

        val router = LlmRouterService()
        router.recordCooldown(LlmProvider.POLLINATIONS, 35)
        assertTrue(router.remainingCooldownSeconds(LlmProvider.POLLINATIONS) in 33..35)
        assertTrue(router.activeCooldownSnapshot().containsKey(LlmProvider.POLLINATIONS))
        router.clearCooldown(LlmProvider.POLLINATIONS)
        assertEquals(0, router.remainingCooldownSeconds(LlmProvider.POLLINATIONS))
    }
}
