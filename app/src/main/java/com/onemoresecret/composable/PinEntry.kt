package com.onemoresecret.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemoresecret.R

@Composable
fun PinEntry(
    onDismissRequest: () -> Unit,
    onUnlock: (String) -> Boolean,
    onDelete: (String) -> String = { it.dropLast(1) }
) {
    var enteredPin by remember { mutableStateOf("") }

    // Scramble the digits 0-9 once per dialog launch
    val scrambledDigits = remember {
        (0..9).shuffled().map { it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {}, // Logic handled by the unlock button in the grid
        title = {
            Text(
                text = stringResource(R.string.please_enter_your_pin),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pin Display (Masked)
                Text(
                    text = "•".repeat(enteredPin.length).ifEmpty { " " },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Keypad Grid
                Box(modifier = Modifier.width(220.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        // First 9 randomized digits
                        items(scrambledDigits.take(9)) { digit ->
                            PinButton(digit) { enteredPin += digit }
                        }

                        // Last digit
                        item {
                            PinButton(scrambledDigits.last()) { enteredPin += scrambledDigits.last() }
                        }

                        // Delete Button
                        item {
                            IconButton(
                                onClick = { if (enteredPin.isNotEmpty()) enteredPin = onDelete(enteredPin) },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                            }
                        }

                        // Unlock Button
                        item {
                            IconButton(
                                onClick = { if(!onUnlock(enteredPin)) enteredPin = "" },
                                enabled = enteredPin.isNotEmpty(),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Unlock")
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PinButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}