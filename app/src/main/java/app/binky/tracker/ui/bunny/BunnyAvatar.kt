package app.binky.tracker.ui.bunny

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import coil3.compose.AsyncImage
import java.io.File

/**
 * A bunny's avatar, or the placeholder standing in for one.
 *
 * **Missing media renders as a placeholder, never a crash** (house rule): a restored backup may
 * legitimately lack photos, and a file can be gone from under a perfectly good row. Coil answers a
 * failed load with the `error` painter rather than throwing, so the broken-path case is the same
 * code path as the no-path case.
 */
@Composable
fun BunnyAvatar(
    avatar: File?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val placeholder = rememberVectorPainter(Icons.Filled.Person)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar == null) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.bunny_avatar_placeholder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        } else {
            AsyncImage(
                // A `file://` Uri rather than the File itself: Coil maps Android Uris on every
                // version, and the uuid in the path doubles as the cache key, so replacing an
                // avatar can never show the old one from memory.
                model = remember(avatar) { Uri.fromFile(avatar) },
                contentDescription = stringResource(R.string.bunny_avatar_description, name),
                contentScale = ContentScale.Crop,
                // Both fall back to the same silhouette: `error` covers a path whose file has gone
                // missing, `fallback` a null model. Neither is an error worth shouting about.
                error = placeholder,
                fallback = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
