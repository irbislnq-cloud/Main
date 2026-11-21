const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_rp_123";
const BOB_UID = "bob_rp_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildDefaultRpgState(enabled = true) {
  return {
    enabled,
    level: 2,
    hp: 24,
    maxHp: 28,
    mp: 12,
    maxMp: 16,
    gold: 75,
    stats: { "Сила": 12, "Ловкость": 15, "Интеллект": 14, "Восприятие": 13, "Харизма": 11 },
    inventory: ["Латунный детектор эфира", "Отмычка"],
    statuses: ["Настороже"],
    activeQuest: "Осмотреть Причал №7 и вскрыть сундук",
    currentLocationId: "loc_tavern",
    locations: [
      {
        id: "loc_tavern",
        name: "Таверна «Ржавый якорь»",
        description: "Тёплый зал у причала",
        connections: ["loc_docks"],
        visited: true,
        backdropUri: "",
        posX: 0.2,
        posY: 0.3,
        dangerLevel: "Низкая",
        shopItems: ["Эль (5 зол.)", "Факел (10 зол.)"]
      }
    ],
    combat: {
      active: false,
      round: 1,
      turnIndex: 0,
      combatants: []
    },
    playerProfile: {
      race: "Человек",
      charClass: "Следопыт",
      origin: "Береговой патруль",
      appearance: "Плащ цвета грозы",
      speechStyle: "Лаконичный",
      equipmentSlots: { "Оружие": "Клинок заката", "Броня": "Кожаная куртка" },
      perks: ["Чуткий слух"],
      factionReputation: { "Синдикат Пепла": -15 },
      statusDurations: { "Настороже": 3 }
    },
    worldEnv: {
      timeOfDay: "Полночь",
      dayCount: 2,
      weather: "Ливень и туман",
      worldLaws: "Магия эфира облагается налогом",
      economyNotes: "Торговцы ценят серебро"
    },
    npcs: [
      {
        id: "npc_vance",
        name: "Капитан Вэнс",
        role: "Капитан причала",
        faction: "Стража гавани",
        disposition: 15,
        mood: "Напряжённое",
        appearance: "Шрам на левой щеке",
        stats: { "Сила": 14, "Восприятие": 15 },
        inventory: ["Ключ от причала №7"],
        secrets: ["Должен Синдикату Пепла крупную сумму"],
        revealedSecretCount: 0,
        statsRevealed: false,
        perceptionDc: 14,
        trustThreshold: 50,
        portraitUri: "",
        notes: "Подозревает контрабанду"
      }
    ],
    quests: [
      {
        id: "quest_dock7",
        title: "Тайна Причала №7",
        description: "Выяснить, что скрывает ночной баркас.",
        stages: ["Расспросить капитана", "Проникнуть на причал"],
        completedStages: 1,
        completed: false,
        rewardText: "150 золота"
      }
    ],
    chronicle: [
      {
        id: "chr_1",
        turnNumber: 4,
        title: "Встреча в таверне",
        summary: "Кай получил наводку на Причал №7.",
        tags: ["таверна", "расследование"]
      }
    ]
  };
}

function buildScenarioPayload(uid, now) {
  return {
    userId: uid,
    title: "Chronicles of Aethelgard",
    subtitle: "Dark fantasy tavern & intrigue",
    rpMode: "GAME_MASTER",
    systemPrompt: "You are a gritty dark fantasy Game Master.",
    characterBio: "A rain-slicked port city ruled by alchemy guilds.",
    firstMessage: "The tavern door creaks open against the storm.",
    exampleDialogue: "",
    loreNotes: ["Guild of Ash controls the docks"],
    loreEntries: [
      {
        id: "lore_1",
        title: "Синдикат Пепла",
        keys: ["синдикат", "пепел", "причал"],
        content: "Тайная алхимическая гильдия, контролирующая ночную контрабанду.",
        alwaysActive: false,
        useRegex: false,
        priority: 20,
        enabled: true
      }
    ],
    defaultRpgState: buildDefaultRpgState(true),
    defaultAgentSetupJson: "{\"presetId\":\"full_rpg_squad\",\"executionMode\":\"ADAPTIVE\"}",
    participants: ["GM", "Captain Vance"],
    tags: ["fantasy", "rpg"],
    defaultProvider: "GEMINI_AI_LOGIC",
    defaultModelId: "gemini-3.8-flash",
    temperature: 0.85,
    topP: 0.95,
    topK: 40,
    maxTokens: 2048,
    contextWindowMessages: 24,
    presencePenalty: 0.0,
    frequencyPenalty: 0.0,
    stopSequences: [],
    avatarIconKey: "chronicler",
    accentColorHex: "#A78BFA",
    portraitUri: "file:///data/user/0/app/files/scene_art/p.jpg",
    backdropUri: "file:///data/user/0/app/files/scene_art/b.jpg",
    pinned: false,
    createdAt: now,
    updatedAt: now,
  };
}

function buildChatPayload(uid, now) {
  return {
    userId: uid,
    scenarioId: "scen_1",
    rootChatId: "chat_1",
    parentChatId: "",
    branchLabel: "Основная линия",
    branchPointOrder: 0,
    title: "Storm at Blackharbor",
    rpMode: "GAME_MASTER",
    systemPrompt: "You are a gritty dark fantasy Game Master.",
    characterName: "Мастер Хроник",
    userPersonaName: "Кай",
    userPersonaDesc: "Следопыт в плаще с серебряным компасом.",
    authorsNote: "Maintain atmospheric sensory details.",
    memorySummary: "",
    loreNotes: ["Silver compass points toward cursed relics"],
    loreEntries: [
      {
        id: "lore_1",
        title: "Серебряный компас",
        keys: ["компас", "артефакт"],
        content: "Стрелка дрожит вблизи проклятых реликвий.",
        alwaysActive: false,
        useRegex: false,
        priority: 15,
        enabled: true
      }
    ],
    rpgState: buildDefaultRpgState(true),
    agentSetupJson: "{\"presetId\":\"full_rpg_squad\",\"executionMode\":\"ADAPTIVE\"}",
    participants: ["Мастер Хроник"],
    provider: "GROQ",
    modelId: "llama-3.3-70b-versatile",
    temperature: 0.85,
    topP: 0.95,
    topK: 40,
    maxTokens: 2048,
    contextWindowMessages: 24,
    presencePenalty: 0.0,
    frequencyPenalty: 0.0,
    stopSequences: [],
    portraitUri: "",
    backdropUri: "",
    lastMessagePreview: "The lantern flickers.",
    messageCount: 1,
    pinned: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write scenarios, chats, messages, or personas", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scenarios").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scenarios").doc("scen_1").set(buildScenarioPayload(ALICE_UID, now))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
});

test("Owner can create and update v2 scenarios, chats, swiped messages, RPG state, and personas", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scenarios").doc("scen_1").set(buildScenarioPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set(buildChatPayload(ALICE_UID, now))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      temperature: 1.15,
      topP: 0.9,
      rpgState: { ...buildDefaultRpgState(true), hp: 18, gold: 90 },
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "assistant",
      speakerName: "Мастер Хроник",
      content: "*Дождь барабанит по крыше таверны.* — Добро пожаловать.",
      variants: [
        "*Дождь барабанит по крыше таверны.* — Добро пожаловать.",
        "*Фонарь качается на ветру.* — Поздно же тебя занесло в гавань."
      ],
      variantModels: ["gemini-3.8-flash", "deepseek/deepseek-r1"],
      activeVariantIndex: 1,
      messageKind: "ROLEPLAY",
      modelUsed: "deepseek/deepseek-r1",
      sceneArtUri: "",
      agentTrace: ["Сценарист: задан конфликт", "Разметчик: 2 сущности"],
      orderIndex: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("personas").doc("pers_1").set({
      userId: ALICE_UID,
      name: "Кай",
      description: "Следопыт Нижнего города",
      preferredStyle: "От первого лица",
      activeDefault: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cross-user access, ghost fields, immutable createdAt, invalid swipe index, and out-of-range RPG/generation params are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set(buildChatPayload(ALICE_UID, now))
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({ title: "Hijacked" }));

  // Ghost field rejected
  const withGhost = buildChatPayload(ALICE_UID, now);
  withGhost.id = "chat_2";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_2").set(withGhost)
  );

  // Out-of-bounds swipe index (>= 12) rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").collection("messages").doc("msg_bad_swipe").set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "assistant",
      content: "Variant",
      variants: ["Variant"],
      variantModels: ["gemini-3.8-flash"],
      activeVariantIndex: 15,
      messageKind: "ROLEPLAY",
      orderIndex: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Out-of-bounds RPG HP (< 0) rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      rpgState: { ...buildDefaultRpgState(true), hp: -5 },
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
