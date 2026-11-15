package io.github.victoresidoro.esidraw.domain.model

/** Quem participa do sorteio. [phone] fica normalizado (só dígitos, com DDI), ex.: 5531988887777. */
data class Participant(
    val id: Long,
    val name: String,
    val phone: String,
)

/** Uma relação "[giverId] tira [receiverId]", usada tanto em bloqueios quanto em pares forçados. */
data class Pairing(
    val giverId: Long,
    val receiverId: Long,
)

/**
 * Regras do organizador.
 *
 * - [blocks]: [Pairing.giverId] NÃO pode tirar [Pairing.receiverId] ("brigados"). Um bloqueio nos
 *   dois sentidos são dois itens.
 * - [forced]: [Pairing.giverId] DEVE tirar [Pairing.receiverId] ("combinado").
 */
data class DrawRules(
    val blocks: Set<Pairing> = emptySet(),
    val forced: Set<Pairing> = emptySet(),
)

/** O quanto o sorteio conseguiu evitar que alguém descubra o par por eliminação. */
enum class CycleGuarantee {
    /** Todos formam um único ciclo (A→B→C→...→A): ninguém tira quem o tirou nem forma "ilhas". */
    SINGLE_CYCLE,

    /** Ninguém tirou quem o tirou (sem duplas A↔B), mas pode haver mais de um ciclo. */
    NO_MUTUAL_PAIRS,

    /** As regras não permitiram evitar ciclos curtos; vale só o que as regras exigem. */
    NONE,
}

data class DrawOptions(
    /** Tenta, nesta ordem, um ciclo único, depois sem duplas A↔B, e por fim qualquer sorteio válido. */
    val avoidShortCycles: Boolean = true,
)

sealed interface DrawOutcome {
    /** [assignments] mapeia o id de quem tira para o id de quem foi tirado. */
    data class Success(
        val assignments: Map<Long, Long>,
        val cycleGuarantee: CycleGuarantee,
    ) : DrawOutcome

    data class Impossible(val diagnosis: Diagnosis) : DrawOutcome
}

enum class IssueKind {
    TOO_FEW_PARTICIPANTS,
    SELF_FORCED,
    FORCED_AND_BLOCKED,
    FORCED_TWICE_BY_GIVER,
    FORCED_TWICE_ON_RECEIVER,
    ISOLATED_GIVER,
    ISOLATED_RECEIVER,
    HALL_VIOLATION,
}

/** Um problema que impede o sorteio. [culpritIds] são os participantes que causam o gargalo. */
data class Issue(
    val kind: IssueKind,
    val culpritIds: List<Long>,
    val message: String,
)

/**
 * Por que o sorteio é matematicamente impossível.
 *
 * [neutralsNeeded] é o menor número de participantes SEM regras que, se adicionados, tornam o sorteio
 * possível; é `null` quando adicionar gente não resolve (regras contraditórias).
 */
data class Diagnosis(
    val issues: List<Issue>,
    val neutralsNeeded: Int?,
) {
    val neutralsMessage: String
        get() = when (neutralsNeeded) {
            null -> "Adicionar participantes não resolve: corrija as regras em conflito."
            1 -> "Adicione 1 participante sem bloqueios nem pares forçados para o sorteio funcionar."
            else -> "Adicione $neutralsNeeded participantes sem bloqueios nem pares forçados " +
                "para o sorteio funcionar."
        }

    val summary: String
        get() = (issues.map { it.message } + neutralsMessage).joinToString("\n")
}
