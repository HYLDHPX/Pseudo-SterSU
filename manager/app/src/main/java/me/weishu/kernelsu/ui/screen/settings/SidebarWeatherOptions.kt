package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.icon
import me.weishu.kernelsu.ui.component.bottombar.weatherErrorText
import me.weishu.kernelsu.ui.util.SidebarWeatherClient
import me.weishu.kernelsu.ui.util.SidebarWeatherConfig
import me.weishu.kernelsu.ui.util.SidebarWeatherError
import me.weishu.kernelsu.ui.util.SidebarWeatherException
import me.weishu.kernelsu.ui.util.SidebarWeatherReading
import me.weishu.kernelsu.ui.util.SidebarWeatherRuntime
import me.weishu.kernelsu.ui.util.SidebarWeatherSource
import me.weishu.kernelsu.ui.util.SidebarWidgetConfig
import me.weishu.kernelsu.ui.util.openMeteoUrl

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SidebarWeatherOptions(config: SidebarWidgetConfig, onUpdate: (SidebarWidgetConfig) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentConfig by rememberUpdatedState(config)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    var mode by rememberSaveable(config.weatherApi) {
        mutableStateOf(config.weatherApi.source.takeIf { config.weatherApi.canFetch() })
    }
    var showXiaomiDisclosure by remember { mutableStateOf(false) }
    var showOpenMeteoDisclosure by remember { mutableStateOf(false) }
    var draft by rememberSaveable(config.weatherApi, stateSaver = Saver(
        save = { it: SidebarWeatherConfig -> it.toJson().toString() },
        restore = { SidebarWeatherConfig.fromJson(it) },
    )) { mutableStateOf(config.weatherApi) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<SidebarWeatherException?>(null) }
    var tested by remember { mutableStateOf<SidebarWeatherReading?>(null) }
    var showAuth by rememberSaveable { mutableStateOf(config.weatherApi.headerName.isNotEmpty()) }
    var revealSecret by remember { mutableStateOf(false) }

    fun edit(value: SidebarWeatherConfig) {
        draft = value
        failure = null
        tested = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val sources = listOf(null, SidebarWeatherSource.CustomApi, SidebarWeatherSource.Xiaomi, SidebarWeatherSource.OpenMeteo)
        SidebarDesignChoice(
            title = stringResource(R.string.sidebar_editor_weather_title),
            options = listOf(R.string.sidebar_weather_manual, R.string.sidebar_weather_custom_api,
                R.string.sidebar_weather_xiaomi, R.string.sidebar_weather_open_meteo).map { stringResource(it) },
            selectedIndex = sources.indexOf(mode), enabled = !busy,
            onSelected = { index ->
                mode = sources[index]
                failure = null
                tested = null
                if (mode == null) onUpdate(config.copy(weatherApi = config.weatherApi.copy(enabled = false)))
            },
        )
        if (mode == null) {
            Text(
                stringResource(R.string.sidebar_widget_weather_privacy_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = config.weatherTemperature,
                onValueChange = { onUpdate(config.copy(weatherTemperature = it)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sidebar_widget_weather_temperature)) },
            )
            OutlinedTextField(
                value = config.weatherLabel,
                onValueChange = { onUpdate(config.copy(weatherLabel = it)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sidebar_widget_weather_label)) },
            )
        } else if (mode == SidebarWeatherSource.Xiaomi) {
            Text(
                stringResource(R.string.sidebar_weather_xiaomi_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SidebarWeatherRefreshChoice(draft.refreshMinutes, !busy) { edit(draft.copy(refreshMinutes = it)) }
            val xiaomiEnabled = config.weatherApi.source == SidebarWeatherSource.Xiaomi && config.weatherApi.canFetch()
            Text(
                stringResource(if (xiaomiEnabled) R.string.sidebar_weather_xiaomi_enabled else R.string.sidebar_weather_xiaomi_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showXiaomiDisclosure = true },
            ) { Text(stringResource(if (xiaomiEnabled) R.string.sidebar_weather_xiaomi_apply else R.string.sidebar_weather_xiaomi_enable)) }
            if (showXiaomiDisclosure) AlertDialog(
                onDismissRequest = { showXiaomiDisclosure = false },
                title = { Text(stringResource(R.string.sidebar_weather_xiaomi_disclosure_title)) },
                text = { Text(stringResource(R.string.sidebar_weather_xiaomi_disclosure)) },
                confirmButton = {
                    TextButton(onClick = {
                        val candidate = draft.copy(
                            enabled = true,
                            source = SidebarWeatherSource.Xiaomi,
                            xiaomiAccepted = true,
                        ).normalized()
                        showXiaomiDisclosure = false
                        currentOnUpdate(currentConfig.copy(weatherApi = candidate))
                        scope.launch { SidebarWeatherRuntime.repository(context).refresh(candidate, force = true) }
                    }) { Text(stringResource(R.string.sidebar_weather_xiaomi_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showXiaomiDisclosure = false }) {
                        Text(stringResource(R.string.sidebar_weather_xiaomi_cancel))
                    }
                },
            )
        } else if (mode == SidebarWeatherSource.OpenMeteo) {
            Text(
                stringResource(R.string.sidebar_weather_open_meteo_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.latitude,
                onValueChange = { edit(draft.copy(latitude = it.take(32))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                label = { Text(stringResource(R.string.sidebar_weather_latitude)) },
                isError = failure?.reason == SidebarWeatherError.Coordinates,
            )
            OutlinedTextField(
                value = draft.longitude,
                onValueChange = { edit(draft.copy(longitude = it.take(32))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                label = { Text(stringResource(R.string.sidebar_weather_longitude)) },
                isError = failure?.reason == SidebarWeatherError.Coordinates,
            )
            OutlinedTextField(
                value = draft.locationLabel,
                onValueChange = { edit(draft.copy(locationLabel = it.take(48))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.sidebar_weather_location_label)) },
                supportingText = { Text(stringResource(R.string.sidebar_weather_open_meteo_location_help)) },
            )
            Text(stringResource(R.string.sidebar_weather_open_meteo_unit), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false, true).forEach { fahrenheit ->
                    FilterChip(
                        selected = draft.fahrenheit == fahrenheit, enabled = !busy,
                        onClick = { edit(draft.copy(fahrenheit = fahrenheit)) },
                        label = { Text(if (fahrenheit) "\u00b0F" else "\u00b0C") },
                    )
                }
            }
            SidebarWeatherRefreshChoice(draft.refreshMinutes, !busy) { edit(draft.copy(refreshMinutes = it)) }
            val openMeteoEnabled = config.weatherApi.source == SidebarWeatherSource.OpenMeteo && config.weatherApi.canFetch()
            Text(
                stringResource(if (openMeteoEnabled) R.string.sidebar_weather_open_meteo_enabled else R.string.sidebar_weather_open_meteo_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            failure?.let { Text(weatherErrorText(it.reason, it.statusCode), color = MaterialTheme.colorScheme.error) }
            FilledTonalButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        openMeteoUrl(draft)
                        failure = null
                        showOpenMeteoDisclosure = true
                    } catch (e: SidebarWeatherException) {
                        failure = e
                    }
                },
            ) { Text(stringResource(if (openMeteoEnabled) R.string.sidebar_weather_open_meteo_apply else R.string.sidebar_weather_open_meteo_enable)) }
            if (showOpenMeteoDisclosure) AlertDialog(
                onDismissRequest = { showOpenMeteoDisclosure = false },
                title = { Text(stringResource(R.string.sidebar_weather_open_meteo_disclosure_title)) },
                text = { Text(stringResource(R.string.sidebar_weather_open_meteo_disclosure)) },
                confirmButton = {
                    TextButton(onClick = {
                        val candidate = draft.copy(
                            enabled = true,
                            source = SidebarWeatherSource.OpenMeteo,
                            openMeteoAccepted = true,
                        ).normalized()
                        showOpenMeteoDisclosure = false
                        currentOnUpdate(currentConfig.copy(weatherApi = candidate))
                        scope.launch { SidebarWeatherRuntime.repository(context).refresh(candidate, force = true) }
                    }) { Text(stringResource(R.string.sidebar_weather_open_meteo_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showOpenMeteoDisclosure = false }) {
                        Text(stringResource(R.string.sidebar_weather_xiaomi_cancel))
                    }
                },
            )
        } else {
            Text(
                stringResource(R.string.sidebar_weather_api_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.url,
                onValueChange = { edit(draft.copy(url = it.take(4096))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                label = { Text(stringResource(R.string.sidebar_weather_api_url)) },
                placeholder = { Text("https://example.com/weather?city=...") },
                isError = failure?.reason == SidebarWeatherError.Url,
            )
            OutlinedTextField(
                value = draft.locationLabel,
                onValueChange = { edit(draft.copy(locationLabel = it.take(48))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.sidebar_weather_location_label)) },
                supportingText = { Text(stringResource(R.string.sidebar_weather_location_help)) },
            )
            TextButton(onClick = { showAuth = !showAuth }, enabled = !busy) {
                Text(stringResource(if (showAuth) R.string.sidebar_weather_auth_hide else R.string.sidebar_weather_auth_show))
            }
            if (showAuth) {
                OutlinedTextField(
                    value = draft.headerName,
                    onValueChange = { edit(draft.copy(headerName = it.take(64))) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    label = { Text(stringResource(R.string.sidebar_weather_header_name)) },
                    placeholder = { Text("Authorization / X-API-Key") },
                    isError = failure?.reason == SidebarWeatherError.Header,
                )
                OutlinedTextField(
                    value = draft.headerValue,
                    onValueChange = { edit(draft.copy(headerValue = it.take(4096))) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.sidebar_weather_header_value)) },
                    supportingText = { Text(stringResource(R.string.sidebar_weather_header_help)) },
                    isError = failure?.reason == SidebarWeatherError.Header,
                    trailingIcon = {
                        IconButton(onClick = { revealSecret = !revealSecret }) {
                            Icon(
                                if (revealSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (revealSecret) R.string.sidebar_weather_hide_secret else R.string.sidebar_weather_show_secret),
                            )
                        }
                    },
                )
            }
            Text(stringResource(R.string.sidebar_weather_fields), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.sidebar_weather_fields_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.temperaturePath,
                onValueChange = { edit(draft.copy(temperaturePath = it.take(256))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.sidebar_weather_temperature_path)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                isError = failure?.reason in listOf(SidebarWeatherError.Path, SidebarWeatherError.Temperature),
            )
            OutlinedTextField(
                value = draft.descriptionPath,
                onValueChange = { edit(draft.copy(descriptionPath = it.take(256))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.sidebar_weather_description_path)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
            OutlinedTextField(
                value = draft.locationPath,
                onValueChange = { edit(draft.copy(locationPath = it.take(256))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.sidebar_weather_location_path)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
            Text(stringResource(R.string.sidebar_weather_unit), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false, true).forEach { fahrenheit ->
                    FilterChip(
                        selected = draft.fahrenheit == fahrenheit, enabled = !busy,
                        onClick = { edit(draft.copy(fahrenheit = fahrenheit)) },
                        label = { Text(if (fahrenheit) "\u00b0F" else "\u00b0C") },
                    )
                }
            }
            SidebarWeatherRefreshChoice(draft.refreshMinutes, !busy) { edit(draft.copy(refreshMinutes = it)) }
            Text(
                stringResource(if (config.weatherApi.source == SidebarWeatherSource.CustomApi && config.weatherApi.enabled) R.string.sidebar_weather_saved_hint else R.string.sidebar_weather_unsaved_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            failure?.let { Text(weatherErrorText(it.reason, it.statusCode), color = MaterialTheme.colorScheme.error) }
            tested?.let { reading ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(reading.condition.icon(), contentDescription = null, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(reading.temperatureText() + if (draft.fahrenheit) "\u00b0F" else "\u00b0C", style = MaterialTheme.typography.headlineSmall)
                        if (reading.description.isNotBlank()) Text(reading.description)
                        if (reading.location.isNotBlank()) Text(reading.location, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.sidebar_weather_test_success), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                }
            }
            FilledTonalButton(
                enabled = !busy && draft.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    failure = null
                    tested = null
                    val candidate = draft.copy(enabled = true, source = SidebarWeatherSource.CustomApi)
                    scope.launch {
                        try {
                            val reading = SidebarWeatherClient().fetch(candidate)
                            SidebarWeatherRuntime.repository(context).accept(candidate, reading)
                            currentOnUpdate(currentConfig.copy(weatherApi = candidate.normalized()))
                            tested = reading
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure = e as? SidebarWeatherException ?: SidebarWeatherException(SidebarWeatherError.Network)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(if (busy) R.string.sidebar_weather_testing else R.string.sidebar_weather_test_save)) }
            Text(
                stringResource(R.string.sidebar_weather_api_privacy),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SidebarWeatherRefreshChoice(minutes: Int, enabled: Boolean, onSelected: (Int) -> Unit) {
    val intervals = SidebarWeatherConfig.REFRESH_INTERVALS
    SidebarDesignChoice(
        title = stringResource(R.string.sidebar_weather_interval),
        options = intervals.map { stringResource(R.string.sidebar_weather_minutes, it) },
        selectedIndex = intervals.indexOf(minutes),
        enabled = enabled,
        onSelected = { onSelected(intervals[it]) },
    )
}
