package agent.impl.openai.taskstages.service

import agent.impl.openai.taskstages.ExpectedAction
import agent.impl.openai.taskstages.InvalidTaskTransitionException
import agent.impl.openai.taskstages.TaskStage
import agent.impl.openai.taskstages.TaskStateMachine
import agent.impl.openai.taskstages.TaskTransition

interface TaskStateMachineService {
    fun applyTransition(
        current: TaskStateMachine,
        transition: TaskTransition,
        step: String = "",
        goal: String = ""
    ): TaskStateMachine
}

class TaskStateMachineServiceImpl : TaskStateMachineService {

    private val allowedTransitions: Map<TaskStage, Set<TaskTransition>> = mapOf(
        TaskStage.PLANNING to setOf(
            TaskTransition.START,
            TaskTransition.APPROVE_PLAN,
            TaskTransition.PAUSE,
            TaskTransition.NO_CHANGE
        ),
        TaskStage.EXECUTION to setOf(
            TaskTransition.FINISH_EXECUTION,
            TaskTransition.PAUSE,
            TaskTransition.NO_CHANGE
        ),
        TaskStage.VALIDATION to setOf(
            TaskTransition.VALIDATION_OK,
            TaskTransition.VALIDATION_FAIL,
            TaskTransition.PAUSE,
            TaskTransition.NO_CHANGE
        ),
        TaskStage.DONE to setOf(
            TaskTransition.NO_CHANGE
        ),
        TaskStage.PAUSED to setOf(
            TaskTransition.RESUME,
            TaskTransition.NO_CHANGE
        )
    )

    override fun applyTransition(
        current: TaskStateMachine,
        transition: TaskTransition,
        step: String,
        goal: String
    ): TaskStateMachine {
        validateTransition(current.stage, transition)

        return when (transition) {
            TaskTransition.NO_CHANGE -> current

            TaskTransition.START -> current.copy(
                stage = TaskStage.PLANNING,
                currentStep = "Определение плана",
                expectedAction = ExpectedAction.DEFINE_PLAN,
                pausedFromStage = null,
                lastUserGoal = goal.ifBlank { current.lastUserGoal }
            )

            TaskTransition.APPROVE_PLAN -> current.copy(
                stage = TaskStage.EXECUTION,
                currentStep = step.ifBlank { "Выполнение изменений" },
                expectedAction = ExpectedAction.APPLY_CHANGE,
                pausedFromStage = null
            )

            TaskTransition.FINISH_EXECUTION -> current.copy(
                stage = TaskStage.VALIDATION,
                currentStep = step.ifBlank { "Проверка результата" },
                expectedAction = ExpectedAction.RUN_CHECK,
                pausedFromStage = null
            )

            TaskTransition.VALIDATION_OK -> current.copy(
                stage = TaskStage.DONE,
                currentStep = "Задача завершена",
                expectedAction = ExpectedAction.NONE,
                pausedFromStage = null
            )

            TaskTransition.VALIDATION_FAIL -> current.copy(
                stage = TaskStage.EXECUTION,
                currentStep = step.ifBlank { current.currentStep },
                expectedAction = ExpectedAction.APPLY_CHANGE,
                pausedFromStage = null
            )

            TaskTransition.PAUSE -> current.copy(
                stage = TaskStage.PAUSED,
                expectedAction = ExpectedAction.WAIT_FOR_USER,
                pausedFromStage = current.stage
            )

            TaskTransition.RESUME -> {
                val resumeStage = current.pausedFromStage
                    ?: throw InvalidTaskTransitionException("Cannot resume: pausedFromStage is null")

                val expected = when (resumeStage) {
                    TaskStage.PLANNING -> ExpectedAction.DEFINE_PLAN
                    TaskStage.EXECUTION -> ExpectedAction.APPLY_CHANGE
                    TaskStage.VALIDATION -> ExpectedAction.RUN_CHECK
                    TaskStage.DONE -> ExpectedAction.NONE
                    TaskStage.PAUSED -> ExpectedAction.WAIT_FOR_USER
                }

                current.copy(
                    stage = resumeStage,
                    expectedAction = expected,
                    pausedFromStage = null
                )
            }
        }
    }

    private fun validateTransition(stage: TaskStage, transition: TaskTransition) {
        val allowed = allowedTransitions[stage].orEmpty()
        if (transition !in allowed) {
            throw InvalidTaskTransitionException(
                "Transition $transition is not allowed from stage $stage"
            )
        }
    }
}