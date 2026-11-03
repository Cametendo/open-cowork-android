package io.github.cametendo.opencowork.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.cametendo.opencowork.OpenCoworkApp
import kotlinx.coroutines.launch

/** Handles Allow / Allow all / Deny tapped on an approval notification. */
class ApprovalReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_SESSION = "session_id"
        const val EXTRA_TOOL_CALL = "tool_call_id"
        const val EXTRA_APPROVED = "approved"
        const val EXTRA_ALWAYS = "always"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getStringExtra(EXTRA_SESSION) ?: return
        val toolCall = intent.getStringExtra(EXTRA_TOOL_CALL) ?: return
        val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)
        val always = intent.getBooleanExtra(EXTRA_ALWAYS, false)
        val container = (context.applicationContext as OpenCoworkApp).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.cowork.approve(session, toolCall, approved, always)
            } catch (e: Exception) {
                Toast.makeText(context.applicationContext, e.message ?: "Couldn't send your answer", Toast.LENGTH_LONG).show()
            } finally {
                pending.finish()
            }
        }
    }
}
