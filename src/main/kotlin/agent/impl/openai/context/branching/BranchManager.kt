package agent.impl.openai.context.branching

import store.BranchState
import store.ConversationState

class BranchManager {
    fun createCheckpoint(state: ConversationState, checkpointId: String): ConversationState {
        val branch = state.branches[state.currentBranchId] ?: error("No branch")
        branch.checkpoints[checkpointId] = branch.messages.size
        return state
    }

    fun forkBranch(state: ConversationState, checkpointId: String, newBranchId: String): ConversationState {
        val current = state.branches[state.currentBranchId] ?: error("No branch")
        val idx = current.checkpoints[checkpointId] ?: error("Checkpoint not found: $checkpointId")
        val forkedMessages = current.messages.subList(0, idx).toMutableList()

        val newBranches = state.branches.toMutableMap()
        newBranches[newBranchId] = BranchState(
            messages = forkedMessages,
            checkpoints = current.checkpoints.toMutableMap()
        )
        return state.copy(branches = newBranches)
    }

    fun switchBranch(state: ConversationState, branchId: String): ConversationState {
        require(state.branches.containsKey(branchId)) { "Branch not found: $branchId" }
        return state.copy(currentBranchId = branchId)
    }
}