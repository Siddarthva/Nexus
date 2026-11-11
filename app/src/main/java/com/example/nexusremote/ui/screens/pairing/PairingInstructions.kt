package com.example.nexusremote.ui.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.nexusremote.R
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.SectionCard

/** Numbered TV setup steps, with a Haier / other Google TV switch. */
@Composable
fun PairingInstructions(brand: TvBrand, onBrandChange: (TvBrand) -> Unit, enabled: Boolean) {
    val steps = when (brand) {
        // UNVERIFIED-ON-HARDWARE: Haier menu labels are based on standard Google TV menus,
        // not checked on a physical Haier set.
        TvBrand.HAIER -> listOf(
            R.string.pair_step_haier_1,
            R.string.pair_step_haier_2,
            R.string.pair_step_haier_3,
            R.string.pair_step_haier_4,
        )
        TvBrand.OTHER -> listOf(
            R.string.pair_step_general_1,
            R.string.pair_step_general_2,
            R.string.pair_step_general_3,
            R.string.pair_step_general_4,
        )
    } + listOf(R.string.pair_step_enter, R.string.pair_step_connect_port)

    SectionCard(title = stringResource(R.string.pair_steps_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TvBrand.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = brand == option,
                    onClick = { onBrandChange(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TvBrand.entries.size),
                    label = {
                        Text(
                            stringResource(
                                if (option == TvBrand.HAIER) R.string.pair_brand_haier else R.string.pair_brand_other,
                            ),
                        )
                    },
                )
            }
        }
        steps.forEachIndexed { index, res -> NumberedStep(number = index + 1, text = stringResource(res)) }
        BodyText(stringResource(R.string.pair_same_network))
        Text(
            text = stringResource(R.string.pair_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
