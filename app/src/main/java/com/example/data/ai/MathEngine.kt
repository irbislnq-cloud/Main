package com.example.data.ai

import com.example.data.model.NpcDossier
import com.example.data.model.RpgState
import kotlin.math.abs
import kotlin.random.Random

data class DiceRollResult(
    val statName: String,
    val d20: Int,
    val modifier: Long,
    val total: Long,
    val dc: Long,
    val success: Boolean,
    val criticalSuccess: Boolean,
    val criticalFail: Boolean,
    val formattedSummary: String
)

object MathEngine {

    fun rollCheck(
        statName: String,
        modifier: Long,
        dc: Long,
        purpose: String = "",
        rollOverride: Int? = null
    ): DiceRollResult {
        val raw = (rollOverride ?: Random.nextInt(1, 21)).coerceIn(1, 20)
        val total = raw + modifier
        val critSuccess = raw == 20
        val critFail = raw == 1
        val success = when {
            critSuccess -> true
            critFail -> false
            else -> total >= dc
        }
        val modStr = if (modifier >= 0) "+$modifier" else "$modifier"
        val outcomeRu = when {
            critSuccess -> "КРИТИЧЕСКИЙ УСПЕХ!"
            critFail -> "КРИТИЧЕСКИЙ ПРОВАЛ!"
            success -> "УСПЕХ"
            else -> "ПРОВАЛ"
        }
        val purposePart = if (purpose.isNotBlank()) " [$purpose]" else ""
        val formatted = "🎲 $statName$purposePart (DC $dc): d20($raw) $modStr = $total → $outcomeRu"
        return DiceRollResult(
            statName = statName,
            d20 = raw,
            modifier = modifier,
            total = total,
            dc = dc,
            success = success,
            criticalSuccess = critSuccess,
            criticalFail = critFail,
            formattedSummary = formatted
        )
    }

    fun resolveNpcPerceptionCheck(
        npc: NpcDossier,
        perceptionMod: Long,
        rollOverride: Int? = null
    ): Pair<NpcDossier, DiceRollResult> {
        val result = rollCheck(
            statName = "Восприятие",
            modifier = perceptionMod,
            dc = npc.perceptionDc.coerceIn(1L, 35L),
            purpose = "Изучить ${npc.name}",
            rollOverride = rollOverride
        )
        if (!result.success) {
            return npc to result
        }
        val nextRevealedCount = if (result.criticalSuccess) {
            (npc.revealedSecretCount + 2L).coerceAtMost(npc.secrets.size.toLong())
        } else {
            (npc.revealedSecretCount + 1L).coerceAtMost(npc.secrets.size.toLong())
        }
        val updated = npc.copy(
            statsRevealed = true,
            revealedSecretCount = maxOf(npc.revealedSecretCount, nextRevealedCount)
        )
        return updated to result
    }

    fun applyTrustAutoReveal(npc: NpcDossier): NpcDossier {
        if (npc.disposition < npc.trustThreshold) return npc
        val minSecrets = if (npc.secrets.isNotEmpty() && npc.revealedSecretCount == 0L) 1L else npc.revealedSecretCount
        return npc.copy(
            statsRevealed = true,
            revealedSecretCount = minSecrets.coerceAtMost(npc.secrets.size.toLong())
        )
    }

    fun validateAndClampRpgState(
        before: RpgState,
        after: RpgState
    ): Pair<RpgState, List<String>> {
        val auditNotes = mutableListOf<String>()

        val safeMaxHp = after.maxHp.coerceIn(1L, 99999L)
        val safeMaxMp = after.maxMp.coerceIn(0L, 99999L)
        var safeHp = after.hp.coerceIn(0L, safeMaxHp)
        var safeMp = after.mp.coerceIn(0L, safeMaxMp)
        var safeGold = after.gold.coerceIn(0L, 9999999L)

        if (after.hp != safeHp) {
            auditNotes += "Математик: HP скорректирован к допустимым границам ($safeHp/$safeMaxHp)"
        }
        if (after.mp != safeMp) {
            auditNotes += "Математик: MP скорректирован к допустимым границам ($safeMp/$safeMaxMp)"
        }
        if (after.gold != safeGold) {
            auditNotes += "Математик: баланс золота зафиксирован на $safeGold"
        }

        // Guard against wild LLM hallucinated jumps on a single turn
        val hpJump = safeHp - before.hp
        val maxReasonableHpStep = maxOf(safeMaxHp, 40L)
        if (abs(hpJump) > maxReasonableHpStep) {
            safeHp = (before.hp + hpJump.coerceIn(-maxReasonableHpStep, maxReasonableHpStep)).coerceIn(0L, safeMaxHp)
            auditNotes += "Математик: скорректирован скачок HP до $safeHp/$safeMaxHp"
        }

        val goldJump = safeGold - before.gold
        if (goldJump < 0L && before.gold + goldJump < 0L) {
            safeGold = 0L
            auditNotes += "Математик: баланс золота зафиксирован на 0"
        }

        // Tick down temporary status durations by 1 turn if tracked
        val tickedDurations = LinkedHashMap<String, Long>()
        after.playerProfile.statusDurations.forEach { (status, turnsLeft) ->
            if (turnsLeft > 1L) {
                tickedDurations[status] = turnsLeft - 1L
            } else if (turnsLeft == 1L) {
                auditNotes += "Математик: эффект «$status» истёк"
            }
        }
        val activeAfterDuration = after.statuses.filter { s ->
            val tracked = after.playerProfile.statusDurations[s]
            tracked == null || tracked > 1L
        }

        // Apply trust threshold Fog-of-War unlocks on NPCs
        var autoRevealedCount = 0
        val updatedNpcs = after.npcs.map { npc ->
            val evaluated = applyTrustAutoReveal(npc)
            if (evaluated != npc) autoRevealedCount++
            evaluated
        }
        if (autoRevealedCount > 0) {
            auditNotes += "Математик/Психолог: доверие раскрыло досье ($autoRevealedCount NPC)"
        }

        val normalized = after.copy(
            maxHp = safeMaxHp,
            hp = safeHp,
            maxMp = safeMaxMp,
            mp = safeMp,
            gold = safeGold,
            statuses = activeAfterDuration,
            npcs = updatedNpcs,
            playerProfile = after.playerProfile.copy(statusDurations = tickedDurations)
        )
        return normalized to auditNotes
    }
}
