package app.amoxi.loopycode.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.amoxi.loopycode.Brain
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val brain: Brain by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        setContent {
            LoopyTheme(brain.prefs) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { Root(brain) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        brain.checkFiles()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /** Something shared from another app: a new chat with it in the composer. */
    private fun take(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        lifecycleScope.launch {
            val pictures = listOfNotNull(uri?.let { brain.importPicture(it) })
            brain.openSession(null)
            brain.screens.clear()
            brain.shared = text to pictures
        }
    }
}

@Composable
private fun Root(brain: Brain) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val top = brain.screens.lastOrNull()
    BackHandler(top != null) { brain.screens.removeAt(brain.screens.lastIndex) }
    BackHandler(top == null && drawer.isOpen) { scope.launch { drawer.close() } }
    AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
        val back = { if (brain.screens.isNotEmpty()) brain.screens.removeAt(brain.screens.lastIndex) }
        when {
            screen == null -> ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = { Drawer(brain) { scope.launch { drawer.close() } } },
            ) {
                ChatScreen(brain, openDrawer = { scope.launch { drawer.open() } })
            }
            screen == "providers" -> ProvidersScreen(brain, back)
            screen.startsWith("provider:") -> ProviderScreen(brain, screen.removePrefix("provider:"), back)
            screen == "config" -> ConfigScreen(brain, back)
            screen == "settings" -> SettingsScreen(brain, back)
            screen == "limits" -> LimitsScreen(brain, back)
            screen == "tools" -> ToolsScreen(brain, back)
            screen == "linux" -> LinuxScreen(brain, back)
            screen == "local" -> LocalScreen(brain, back)
        }
    }
}
