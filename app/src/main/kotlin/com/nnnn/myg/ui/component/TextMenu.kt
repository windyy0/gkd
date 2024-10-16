package com.nnnn.myg.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nnnn.myg.ui.style.itemPadding
import com.nnnn.myg.util.Option
import com.nnnn.myg.util.allSubObject

@Composable
fun <T> TextMenu(
    modifier: Modifier = Modifier,
    title: String,
    option: Option<T>,
    onOptionChange: ((Option<T>) -> Unit),
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable {
                expanded = true
            }
            .fillMaxWidth().let {
                if (modifier == Modifier) {
                    it.itemPadding()
                } else {
                    it.then(modifier)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                option.allSubObject.forEach { otherOption ->
                    DropdownMenuItem(
                        text = {
                            Text(text = otherOption.label)
                        },
                        onClick = {
                            expanded = false
                            if (otherOption != option) {
                                onOptionChange(otherOption)
                            }
                        },
                    )
                }
            }
        }
    }
}