package com.opendarts.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendarts.billing.Plan
import com.opendarts.billing.buttonText
import com.opendarts.billing.yearlySaving

/** One button per plan, best value first. [emphasise] fills the yearly button; otherwise every button is an outline. */
@Composable
fun PlanButtons(plans: List<Plan>, emphasise: Boolean, onChoose: (Plan) -> Unit) {
    if (plans.isEmpty()) {
        PrimaryButton("Subscribe (not available yet)", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        return
    }
    val saving = yearlySaving(plans)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plans.sortedByDescending { it.yearly }.forEachIndexed { index, plan ->
            val text = plan.buttonText(saving)
            if (emphasise && index == 0) PrimaryButton(text, onClick = { onChoose(plan) }, modifier = Modifier.fillMaxWidth())
            else OutlineButton(text, { onChoose(plan) }, Modifier.fillMaxWidth(), height = 48.dp)
        }
    }
}
