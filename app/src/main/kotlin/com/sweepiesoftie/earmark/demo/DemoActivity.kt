package com.sweepiesoftie.earmark.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.ui.Tone
import com.sweepiesoftie.earmark.ui.theme.EarmarkTheme
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif

/** The way back for demo notifications: a small chat with Rahul, or Aisha's payment request. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_CHAT
        setContent {
            EarmarkTheme {
                if (screen == SCREEN_PAY) {
                    val paid by AppGraph.demo.aishaPaid.collectAsState()
                    PayScreen(paid = paid, onPay = { DemoNotifier.payAisha(this) })
                } else {
                    val lines by AppGraph.demo.rahul.collectAsState()
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ChatScreen(lines = lines, onBack = ::finish, onSend = { DemoNotifier.reply(this, it) })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getStringExtra(EXTRA_SCREEN) != SCREEN_PAY) DemoNotifier.chatOpened(this)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CHAT = "chat"
        const val SCREEN_PAY = "pay"
    }
}

/** The demo chat's own colours, so it reads as another app in light and dark. */
private class ChatColors(val background: Color, val received: Color, val sent: Color, val online: Color)

private val LightChat = ChatColors(Color(0xFFF1EDE4), Color.White, Color(0xFFD9F5C8), Color(0xFF2E7D32))
private val DarkChat = ChatColors(Color(0xFF0F0E0A), Color(0xFF26221A), Color(0xFF1F3B29), Color(0xFF7CC47F))

@Composable
private fun ChatScreen(lines: List<ChatLine>, onBack: () -> Unit, onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }
    val chat = if (Tone.Dark) DarkChat else LightChat
    Column(Modifier.fillMaxSize().background(chat.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Tone.Paper).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.nav_back), onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", fontSize = 22.sp, color = Tone.Ink) }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE8734A)), contentAlignment = Alignment.Center) {
                Text("R", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text("Rahul", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.demo_online), style = MaterialTheme.typography.bodyMedium, color = chat.online)
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(lines) { line ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (line.fromUser) chat.sent else chat.received)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.demo_message_hint)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = chat.received, unfocusedContainerColor = chat.received),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.demo_send), enabled = draft.isNotBlank()) {
                        onSend(draft.trim())
                        draft = ""
                    },
                contentAlignment = Alignment.Center,
            ) { Text("➤", color = Color.White, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun PayScreen(paid: Boolean, onPay: () -> Unit) {
    val blue = Color(0xFF3253E6)
    Column(
        Modifier.fillMaxSize().background(blue).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (paid) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text("✓", color = blue, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.heightIn(min = 24.dp))
            Text(stringResource(R.string.demo_paid), color = Color.White, fontFamily = InstrumentSerif, fontSize = 34.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        } else {
            Text(stringResource(R.string.demo_pay_title), color = Color.White, fontFamily = InstrumentSerif, fontSize = 34.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.demo_pay_note), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.heightIn(min = 32.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = onPay),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.demo_pay_button), color = blue, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
