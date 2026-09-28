package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.repo.AttachmentOwner
import com.pat.app.holidayplanner.data.repo.AttachmentTarget

/** How far each level of the country, city, spot tree is pushed in. */
private val INDENT_PER_LEVEL = 20.dp

/**
 * Where to move a photo or a link: the country, its cities and their spots, laid out as the tree
 * they are. Whatever it hangs on now is left out, since moving it there would do nothing.
 */
@Composable
fun MoveAttachmentDialog(
    attachment: LinkEntity,
    targets: List<AttachmentTarget>,
    onPick: (AttachmentOwner) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = AttachmentOwner.of(attachment)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (attachment.source.isPhoto) R.string.attachment_move_photo else R.string.attachment_move_link,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                targets.filter { it.owner != current }.forEach { target ->
                    Text(
                        text = target.name,
                        style = if (target.owner.spotId.isEmpty()) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(target.owner) }
                            .padding(start = INDENT_PER_LEVEL * target.depth, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
