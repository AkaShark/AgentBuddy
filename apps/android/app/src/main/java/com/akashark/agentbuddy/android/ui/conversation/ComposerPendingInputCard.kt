package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.PendingUserInputAnswer
import uniffi.codex_mobile_client.PendingUserInputQuestion
import uniffi.codex_mobile_client.PendingUserInputRequest

/** Submits answers to the Rust store; errors stay on the card. */
@Composable
internal fun ComposerPendingInputHost(
    appModel: AppModel,
    scope: CoroutineScope,
    request: PendingUserInputRequest,
    answers: Map<String, String>,
    onAnswersChange: (Map<String, String>) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    var submitError by remember(request.id) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(request.id) { mutableStateOf(false) }
    ComposerPendingInputCard(
        pendingUserInput = request,
        userInputAnswers = answers,
        onAnswerChange = { questionId, answer -> onAnswersChange(answers + (questionId to answer)) },
        pendingUserInputSubmitError = submitError,
        isSubmittingPendingUserInput = isSubmitting,
        onDismissPendingUserInput = onDismiss,
        onSubmit = submit@{
            if (isSubmitting) return@submit
            val payload = request.questions.map { q ->
                PendingUserInputAnswer(questionId = q.id, answers = listOfNotNull(answers[q.id]))
            }
            scope.launch {
                isSubmitting = true
                submitError = null
                try {
                    appModel.store.respondToUserInput(request.id, payload)
                    onAnswersChange(emptyMap())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    LLog.e("ComposerBar", "user input response failed", error, fields = mapOf("requestId" to request.id))
                    submitError = responseSubmissionErrorMessage(error)
                } finally {
                    isSubmitting = false
                }
            }
        },
    )
}

/** Share of the visible height (screen minus keyboard) the question card may take. */
private const val PENDING_INPUT_MAX_FRACTION = 0.45f

/**
 * Inline request_user_input card above the composer (radius 20). Answers come
 * from [userInputAnswers], which the composer keys by request id so they never
 * leak into the next request. The questions scroll inside a card capped at
 * [PENDING_INPUT_MAX_FRACTION] of the visible height, so 「提交」 (outside the
 * scroll) stays on screen with many questions or the keyboard up.
 */
@Composable
internal fun ComposerPendingInputCard(
    pendingUserInput: PendingUserInputRequest,
    userInputAnswers: Map<String, String>,
    onAnswerChange: (questionId: String, answer: String) -> Unit,
    pendingUserInputSubmitError: String?,
    isSubmittingPendingUserInput: Boolean,
    onDismissPendingUserInput: (() -> Unit)?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - imeBottom) * PENDING_INPUT_MAX_FRACTION
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs)
            .heightIn(max = maxHeight)
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.confirmCard, padding = null)
            .padding(start = BuddySpacing.md, end = BuddySpacing.xxs, bottom = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.QuestionAnswer,
                contentDescription = null,
                tint = AgentBuddyTheme.warning,
                modifier = Modifier.size(BuddySize.icon),
            )
            Text(
                text = "需要你的回答",
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = BuddySpacing.xs)
                    .semantics { heading() },
            )
            if (onDismissPendingUserInput != null) {
                BuddyIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "关闭输入请求",
                    onClick = onDismissPendingUserInput,
                    iconSize = 16.dp,
                )
            }
        }
        requesterLabel(pendingUserInput)?.let { requester ->
            Text(
                text = requester,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(end = BuddySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        ) {
            for (question in pendingUserInput.questions) {
                PendingQuestion(
                    question = question,
                    answer = userInputAnswers[question.id],
                    onAnswerChange = { onAnswerChange(question.id, it) },
                )
            }
        }
        Column(
            modifier = Modifier.padding(end = BuddySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            pendingUserInputSubmitError?.let { message ->
                BuddyBanner(tone = BuddyBannerTone.DANGER, message = message)
            }
            BuddyButton(
                text = "提交",
                onClick = onSubmit,
                icon = Icons.AutoMirrored.Outlined.Send,
                isLoading = isSubmittingPendingUserInput,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingQuestion(
    question: PendingUserInputQuestion,
    answer: String?,
    onAnswerChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        question.header?.takeIf { it.isNotBlank() }?.let { header ->
            Text(
                text = header,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        Text(
            text = question.question,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textPrimary,
        )
        if (question.options.isNotEmpty()) {
            // Wraps long option labels onto new rows instead of squeezing them.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                for (option in question.options) {
                    PendingOptionChip(
                        label = option.label,
                        selected = answer == option.label,
                        onClick = { onAnswerChange(option.label) },
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BuddySize.control)
                    .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (answer.isNullOrEmpty()) {
                    Text("输入回答", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textSecondary)
                }
                BasicTextField(
                    value = answer.orEmpty(),
                    onValueChange = onAnswerChange,
                    textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                    cursorBrush = SolidColor(AgentBuddyTheme.focus),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PendingOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary
    Box(
        modifier = Modifier
            .heightIn(min = BuddySize.minHitTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .background(if (selected) AgentBuddyTheme.brand else AgentBuddyTheme.surfaceSoft, CircleShape)
                .padding(horizontal = 14.dp, vertical = BuddySpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.LABEL, if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = content,
            )
        }
    }
}

private fun requesterLabel(request: PendingUserInputRequest): String? =
    buildString {
        request.requesterAgentNickname?.takeIf { it.isNotBlank() }?.let { append(it) }
        request.requesterAgentRole?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" ")
            append("[$it]")
        }
    }.ifEmpty { null }
