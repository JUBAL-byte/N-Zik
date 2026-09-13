package app.n_zik.android.components.musicbrainz.insights

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import kotlin.math.roundToInt

/**
 * Card container for an Insights section.
 */
@Composable
fun InfoCard(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(
            containerColor = colorPalette().background2,
            contentColor = colorPalette().text
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colorPalette().textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = typography().xs.semiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Five-star rating display for the Insights screen.
 */
@Composable
fun RatingBar(
    rating: Float?,
    votes: Int?,
    modifier: Modifier = Modifier
) {
    if (rating == null) {
        Text(
            text = stringResource(R.string.mb_no_rating),
            style = typography().xxs,
            color = colorPalette().textSecondary,
            modifier = modifier
        )
        return
    }

    val normalizedRating = (rating / 5f).coerceIn(0f, 1f)
    val stars = (normalizedRating * 5).roundToInt()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(14.dp)
                )
            }
            repeat(5 - stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = colorPalette().textDisabled,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = String.format("%.1f", rating),
                style = typography().xs.semiBold
            )
        }
        if (votes != null && votes > 0) {
            Text(
                text = stringResource(R.string.mb_rating_votes_mb, votes),
                style = typography().xxs,
                color = colorPalette().textSecondary
            )
        }
    }
}

