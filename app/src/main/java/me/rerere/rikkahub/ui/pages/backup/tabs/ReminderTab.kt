package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.RoundedColumn
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackupReminderConfig
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.SaltIconRow
import me.rerere.rikkahub.ui.components.ui.SaltSwitchRow
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

private val ReminderIntervals = listOf(1, 3, 7, 14, 30)

@Composable
fun ReminderTab(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.backupReminderConfig

    fun updateConfig(update: BackupReminderConfig) {
        vm.updateSettings { it.copy(backupReminderConfig = update) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 横向内距交给 Salt 的 RoundedColumn（自带 16dp），这里不再补
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundedColumn {
            SaltSwitchRow(
                checked = config.enabled,
                onCheckedChange = { updateConfig(config.copy(enabled = it)) },
                headline = stringResource(R.string.backup_page_reminder_enable),
            )

            if (config.enabled) {
                ItemContainer {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.backup_page_reminder_interval),
                                modifier = Modifier.weight(1f),
                            )
                            // 当前天数的取值小标签，保留原来的 pill 观感
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.backup_page_reminder_interval_days,
                                        config.intervalDays,
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    // 等宽数字，切换天数时标签宽度不跳
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // 五个按钮并排，按钮里只放数字，带单位的完整取值显示在标题行右侧
                        ConnectedChoiceButtons(
                            options = ReminderIntervals,
                            selected = config.intervalDays,
                            onSelect = { updateConfig(config.copy(intervalDays = it)) },
                        ) { days, selected ->
                            ConnectedChoiceLabel(text = days.toString(), selected = selected)
                        }
                    }
                }

                SaltIconRow(
                    icon = HugeIcons.Clock01,
                    headline = if (config.lastBackupTime == 0L) {
                        stringResource(R.string.backup_page_reminder_no_record)
                    } else {
                        stringResource(
                            R.string.backup_page_reminder_last_time,
                            Instant.ofEpochMilli(config.lastBackupTime).toLocalDateTime()
                        )
                    },
                )
            }
        }
    }
}
