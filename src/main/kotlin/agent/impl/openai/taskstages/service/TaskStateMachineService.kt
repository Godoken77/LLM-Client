package agent.impl.openai.taskstages.service

import agent.impl.openai.taskstages.ExpectedAction
import agent.impl.openai.taskstages.TaskStage
import agent.impl.openai.taskstages.TaskStateMachine
import agent.impl.openai.taskstages.TaskTransition
import agent.impl.openai.taskstages.TaskTransitionDecision

interface TaskStateMachineService {
    fun start(goal: String): TaskStateMachine
    fun nextAfterPlanning(current: TaskStateMachine, step: String): TaskStateMachine
    fun nextAfterExecution(current: TaskStateMachine, step: String): TaskStateMachine
    fun nextAfterValidation(current: TaskStateMachine, isValid: Boolean, nextStep: String = ""): TaskStateMachine
    fun pause(current: TaskStateMachine): TaskStateMachine
    fun resume(current: TaskStateMachine): TaskStateMachine

    fun applyTransition(
        current: TaskStateMachine,
        decision: TaskTransitionDecision
    ): TaskStateMachine
}

class TaskStateMachineServiceImpl : TaskStateMachineService {

    override fun start(goal: String): TaskStateMachine {
        return TaskStateMachine(
            stage = TaskStage.PLANNING,
            currentStep = "Определение плана",
            expectedAction = ExpectedAction.DEFINE_PLAN,
            pausedFromStage = null,
            lastUserGoal = goal
        )
    }

    override fun nextAfterPlanning(current: TaskStateMachine, step: String): TaskStateMachine {
        return current.copy(
            stage = TaskStage.EXECUTION,
            currentStep = step,
            expectedAction = ExpectedAction.APPLY_CHANGE,
            pausedFromStage = null
        )
    }

    override fun nextAfterExecution(current: TaskStateMachine, step: String): TaskStateMachine {
        return current.copy(
            stage = TaskStage.VALIDATION,
            currentStep = step,
            expectedAction = ExpectedAction.RUN_CHECK,
            pausedFromStage = null
        )
    }

    override fun nextAfterValidation(
        current: TaskStateMachine,
        isValid: Boolean,
        nextStep: String
    ): TaskStateMachine {
        return if (isValid) {
            current.copy(
                stage = TaskStage.DONE,
                currentStep = "Задача завершена",
                expectedAction = ExpectedAction.NONE,
                pausedFromStage = null
            )
        } else {
            current.copy(
                stage = TaskStage.EXECUTION,
                currentStep = nextStep.ifBlank { current.currentStep },
                expectedAction = ExpectedAction.APPLY_CHANGE,
                pausedFromStage = null
            )
        }
    }

    override fun pause(current: TaskStateMachine): TaskStateMachine {
        if (current.stage == TaskStage.DONE || current.stage == TaskStage.PAUSED) return current

        return current.copy(
            stage = TaskStage.PAUSED,
            expectedAction = ExpectedAction.WAIT_FOR_USER,
            pausedFromStage = current.stage
        )
    }

    override fun resume(current: TaskStateMachine): TaskStateMachine {
        if (current.stage != TaskStage.PAUSED) return current

        val resumeStage = current.pausedFromStage ?: TaskStage.PLANNING
        val expected = when (resumeStage) {
            TaskStage.PLANNING -> ExpectedAction.DEFINE_PLAN
            TaskStage.EXECUTION -> ExpectedAction.APPLY_CHANGE
            TaskStage.VALIDATION -> ExpectedAction.RUN_CHECK
            TaskStage.DONE -> ExpectedAction.NONE
            TaskStage.PAUSED -> ExpectedAction.WAIT_FOR_USER
        }

        return current.copy(
            stage = resumeStage,
            expectedAction = expected,
            pausedFromStage = null
        )
    }

    override fun applyTransition(
        current: TaskStateMachine,
        decision: TaskTransitionDecision
    ): TaskStateMachine {
        return when (decision.transition) {
            TaskTransition.NO_CHANGE -> current

            TaskTransition.START ->
                start(
                    goal = decision.goal.ifBlank {
                        if (current.lastUserGoal.isNotBlank()) current.lastUserGoal else current.currentStep
                    }
                )

            TaskTransition.PAUSE ->
                pause(current)

            TaskTransition.RESUME ->
                resume(current)

            TaskTransition.NEXT_AFTER_PLANNING ->
                nextAfterPlanning(
                    current = current,
                    step = decision.step.ifBlank { "Выполнение изменений" }
                )

            TaskTransition.NEXT_AFTER_EXECUTION ->
                nextAfterExecution(
                    current = current,
                    step = decision.step.ifBlank { "Проверка результата" }
                )

            TaskTransition.NEXT_AFTER_VALIDATION_OK ->
                nextAfterValidation(
                    current = current,
                    isValid = true
                )

            TaskTransition.NEXT_AFTER_VALIDATION_FAIL ->
                nextAfterValidation(
                    current = current,
                    isValid = false,
                    nextStep = decision.step.ifBlank { current.currentStep }
                )
        }
    }
}