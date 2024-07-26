/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.microsurvey.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.compose.list.RadioButtonListItem
import org.mozilla.fenix.theme.FirefoxTheme

private val shape = RoundedCornerShape(8.dp)

/**
 * The microsurvey content UI to hold question and answer data.
 *
 * @param question The survey question text.
 * @param answers The survey answer text options available for the question.
 * @param icon The survey icon, this will represent the feature the survey is for.
 * @param backgroundColor The view background color.
 * @param selectedAnswer The current selected answer. Will be null until user selects an option.
 * @param onSelectionChange An event that updates the [selectedAnswer].
 */
@Composable
fun MicrosurveyContent(
    question: String,
    answers: List<String>,
    @DrawableRes icon: Int = R.drawable.ic_print,
    backgroundColor: Color = FirefoxTheme.colors.layer2,
    selectedAnswer: String? = null,
    onSelectionChange: (String) -> Unit,
) {
    Card(
        border = BorderStroke(1.dp, FirefoxTheme.colors.borderPrimary),
        elevation = 0.dp,
        shape = shape,
        backgroundColor = backgroundColor,
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.wrapContentHeight()) {
            Header(icon, question)

            answers.forEach {
                RadioButtonListItem(
                    label = it,
                    selected = selectedAnswer == it,
                    onClick = {
                        onSelectionChange.invoke(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(icon: Int, question: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = stringResource(id = R.string.microsurvey_feature_icon_content_description),
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = question,
            color = FirefoxTheme.colors.textPrimary,
            style = FirefoxTheme.typography.headline7,
        )
    }
}

/**
 * Preview for [MicrosurveyContent].
 */
@PreviewScreenSizes
@LightDarkPreview
@Composable
fun MicrosurveyContentPreview() {
    FirefoxTheme {
        MicrosurveyContent(
            question = "How satisfied are you with printing in Firefox?",
            icon = R.drawable.ic_print,
            answers = listOf(
                stringResource(id = R.string.likert_scale_option_very_satisfied),
                stringResource(id = R.string.likert_scale_option_satisfied),
                stringResource(id = R.string.likert_scale_option_neutral),
                stringResource(id = R.string.likert_scale_option_dissatisfied),
                stringResource(id = R.string.likert_scale_option_very_dissatisfied),
            ),
            onSelectionChange = {},
        )
    }
}
