package com.manalejandro.alejabber.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.manalejandro.alejabber.AleJabberApp
import com.manalejandro.alejabber.MainActivity
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import com.manalejandro.alejabber.data.repository.AccountRepository
import com.manalejandro.alejabber.data.repository.ContactRepository
import com.manalejandro.alejabber.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class XmppForegroundService : Service() {

    @Inject lateinit var xmppManager: XmppConnectionManager
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var contactRepository: ContactRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(AleJabberApp.NOTIFICATION_ID_SERVICE, buildForegroundNotification())
        listenForIncomingMessages()
        listenForPresenceUpdates()
        listenForSubscriptionRequests()
        connectAllAccounts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        xmppManager.disconnectAll()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectAllAccounts() {
        serviceScope.launch {
            val accounts = accountRepository.getAllAccounts().first()
            accounts.filter { it.isEnabled }.forEach { account ->
                accountRepository.connectAccount(account)
            }
        }
    }

    private fun listenForIncomingMessages() {
        serviceScope.launch {
            xmppManager.incomingMessages.collect { incoming ->
                val id = messageRepository.saveIncomingMessage(
                    accountId = incoming.accountId,
                    from = incoming.from,
                    body = incoming.body
                )
                showMessageNotification(incoming.from, incoming.body)
            }
        }
    }

    private fun listenForPresenceUpdates() {
        serviceScope.launch {
            xmppManager.presenceUpdates.collect { update ->
                // update.status is already a PresenceStatus — persist it to DB
                // so the roster shows the correct state even after restarting the app.
                contactRepository.updatePresence(
                    update.accountId, update.jid, update.status, update.statusMessage
                )
            }
        }
    }

    private fun listenForSubscriptionRequests() {
        serviceScope.launch {
            xmppManager.subscriptionRequests.collect { req ->
                showSubscriptionNotification(req.accountId, req.fromJid)
            }
        }
    }

    private fun showSubscriptionNotification(accountId: Long, fromJid: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("subscription_account_id", accountId)
            putExtra("subscription_from_jid", fromJid)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, fromJid.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AleJabberApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Contact request")
            .setContentText("$fromJid wants to add you as a contact")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify("sub_${fromJid}".hashCode(), notification)
    }

    private fun showMessageNotification(from: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AleJabberApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(from)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(from.hashCode(), notification)
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AleJabberApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_service_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

