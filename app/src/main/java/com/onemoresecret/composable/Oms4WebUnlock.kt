package com.onemoresecret.composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.onemoresecret.R

@Composable
fun Oms4webUnlockContent(
    message: String?,
    onUnlock: () -> Unit
) {
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        // Correctly initialize the reference within the ConstraintLayout scope
        val btnUnlock = createRef()

        Button(
            onClick = onUnlock,
            enabled = message != null, // Replaces binding.btnUnlock.isEnabled
            modifier = Modifier.constrainAs(btnUnlock) {
                // Constraints mimicking fragment_oms4web_unlock.xml
                bottom.linkTo(parent.bottom, margin = 32.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) {
            // RowScope starts here
            Icon(
                painter = painterResource(id = R.drawable.baseline_key_24),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.unlock))
        }
    }
}