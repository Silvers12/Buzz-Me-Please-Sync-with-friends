package fr.buzzme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.buzzme.core.Codes
import fr.buzzme.core.Transport
import fr.buzzme.net.lan.DiscoveredRoom
import fr.buzzme.net.lan.NsdBrowser
import fr.buzzme.ui.components.PrimaryAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.components.StageBackground
import fr.buzzme.ui.components.StagePanel
import fr.buzzme.ui.theme.Stage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan

@Composable
fun JoinScreen(
    transport: Transport,
    onBack: () -> Unit,
    onJoin: (code: String, address: String?) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current

    // En Wi-Fi local, les salons ouverts s'annoncent en mDNS : inutile de taper le code.
    val rooms: List<DiscoveredRoom> by remember(transport) {
        if (transport == Transport.LOCAL) {
            NsdBrowser.discover(context).scan(emptyList<DiscoveredRoom>()) { acc, room ->
                if (acc.any { it.code == room.code }) acc else acc + room
            }
        } else {
            flowOf(emptyList<DiscoveredRoom>())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Stage.TextSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Rejoindre un salon",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Stage.TextPrimary,
                )
            }

            Spacer(Modifier.height(20.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Code du salon")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = Codes.normalize(it) },
                    singleLine = true,
                    placeholder = {
                        // Même alignement que la saisie, sinon le code semble sauter au premier
                        // caractère tapé.
                        Text(
                            text = "ABCDE",
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go,
                    ),
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 8.sp,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Stage.Gold,
                        unfocusedBorderColor = Stage.Line,
                        focusedTextColor = Stage.GoldSoft,
                        unfocusedTextColor = Stage.GoldSoft,
                        cursorColor = Stage.Gold,
                        focusedContainerColor = Stage.Night,
                        unfocusedContainerColor = Stage.Night,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryAction(
                    text = "Rejoindre",
                    icon = Icons.AutoMirrored.Filled.Login,
                    enabled = Codes.isValid(code),
                    onClick = { onJoin(code, rooms.firstOrNull { it.code == code }?.address) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            if (transport == Transport.LOCAL) {
                StagePanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Salons détectés sur le Wi-Fi", modifier = Modifier.weight(1f))
                        if (rooms.isEmpty()) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Stage.Violet,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (rooms.isEmpty()) {
                        Text(
                            "Recherche en cours… Assurez-vous que l'hôte a créé le salon et que " +
                                "tous les téléphones sont sur le même réseau Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Stage.TextMuted,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rooms.forEach { room ->
                                DiscoveredRoomRow(room = room, onClick = { onJoin(room.code, room.address) })
                            }
                        }
                    }
                }
            } else {
                StagePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("Mode en ligne")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Le salon est hébergé sur votre projet Firebase. Saisissez le code que " +
                            "l'animateur vous a communiqué.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Stage.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DiscoveredRoomRow(room: DiscoveredRoom, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Stage.PanelHigh.copy(alpha = 0.7f), shape)
            .border(1.dp, Stage.Violet.copy(alpha = 0.4f), shape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Router, contentDescription = null, tint = Stage.VioletSoft)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.code,
                style = MaterialTheme.typography.titleLarge,
                color = Stage.GoldSoft,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (room.hostName.isBlank()) room.address else "Chez ${room.hostName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )
        }
        Text("REJOINDRE", style = MaterialTheme.typography.labelMedium, color = Stage.VioletSoft)
    }
}
