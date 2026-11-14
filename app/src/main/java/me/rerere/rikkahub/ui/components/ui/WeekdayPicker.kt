package me.rerere.rikkahub.ui.components.ui

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.ui.components.AnimatedAlertDialog
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** 星期的本地名称，例如中文的「星期一」与英文的 Monday */
fun weekdayLabel(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** 多个星期的本地化枚举，例如「星期一和星期三」与「Monday and Wednesday」 */
fun weekdayListLabel(days: List<DayOfWeek>): String =
    ListFormatter.getInstance(Locale.getDefault()).format(days.map { weekdayLabel(it) })

/** 周一至周日的多选弹窗，自动锁定与变价规则共用 */
@Composable
fun WeekdayPickerDialog(
    title: String,
    selected: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onToggle: (DayOfWeek) -> Unit,
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                DayOfWeek.values().forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(day) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = day in selected, onCheckedChange = { onToggle(day) })
                        Text(weekdayLabel(day))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
    )
}
