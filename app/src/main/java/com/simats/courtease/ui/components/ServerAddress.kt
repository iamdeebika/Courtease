package com.simats.courtease.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simats.courtease.ui.theme.CE
import com.simats.courtease.ui.viewmodel.ServerAddressViewModel

// A small line under the sign-in form: shows the server address and lets you change and test it.
@Composable
fun ServerAddressButton(vm: ServerAddressViewModel = viewModel()) {
    Text(
        "Server: ${vm.current.removePrefix("http://").removePrefix("https://").trimEnd('/')}  ·  change",
        color = CE.Muted, fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable { vm.show() }
    )

    if (vm.open) {
        AlertDialog(
            onDismissRequest = { vm.close() },
            title = { Text("Server address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BodyText("Leave the built-in address to use the online server from any network. Or type the PC's address on the same Wi-Fi, for example 192.168.43.25:8000.")
                    CourtField("Address", vm.text, { vm.onText(it) })
                    if (vm.note.isNotEmpty()) {
                        Text(
                            vm.note, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (vm.noteIsGood) CE.Teal else CE.Red
                        )
                    }
                    Text(
                        "Use built-in address",
                        color = CE.Teal, fontSize = 14.sp,
                        modifier = Modifier.clickable { vm.useDefault() }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.save() }) { Text("Save", color = CE.Teal) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.test() }) { Text(if (vm.busy) "Testing..." else "Test") }
                    TextButton(onClick = { vm.close() }) { Text("Close") }
                }
            }
        )
    }
}
