package com.simats.courtease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.label
import com.simats.courtease.ui.theme.CE

@Composable
fun JudgeCaseCard(c: CaseFile, onClick: () -> Unit) {
    val late = JudgeOps.overdue(c)
    val detail = when {
        late -> "Response overdue since ${c.responseDue}"
        c.status == CaseStatus.ALLOCATED -> "Newly allocated to you"
        c.status == CaseStatus.NOTICE -> "Waiting for the respondent · due ${c.responseDue}"
        c.status == CaseStatus.PLEADINGS -> "Both sides have appeared"
        c.status == CaseStatus.REVIEW -> "Under your review"
        c.status == CaseStatus.JUDGMENT -> "Judgment issued · close the case when done"
        else -> null
    }
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                c.label, color = CE.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            CaseStatusPill(c.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(c.title, color = CE.Navy, fontSize = 20.sp)
        BodyText("${c.claimant}  vs  ${c.respondent}", Modifier.padding(top = 4.dp))
        BodyText("${c.caseType.substringAfter("· ")} · ${c.courtroom}")
        if (detail != null) {
            Text(
                detail, color = if (late) CE.Red else CE.Teal, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// A multi-line text box for order text and reasons.
@Composable
fun CourtArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 4
) {
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = minLines,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = CE.Teal,
                unfocusedBorderColor = CE.Border,
                focusedTextColor = CE.Navy,
                unfocusedTextColor = CE.Navy,
                cursorColor = CE.Teal
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}