package com.manalejandro.alejabber.data.repository

import com.manalejandro.alejabber.data.local.dao.RoomDao
import com.manalejandro.alejabber.data.local.entity.toDomain
import com.manalejandro.alejabber.data.local.entity.toEntity
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import com.manalejandro.alejabber.domain.model.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor(
    private val roomDao: RoomDao,
    private val xmppManager: XmppConnectionManager
) {
    private val joinedChats = mutableMapOf<String, MultiUserChat>()

    fun getRooms(accountId: Long): Flow<List<Room>> =
        roomDao.getRoomsByAccount(accountId).map { list -> list.map { it.toDomain() } }

    fun getJoinedRooms(accountId: Long): Flow<List<Room>> =
        roomDao.getJoinedRooms(accountId).map { list -> list.map { it.toDomain() } }

    suspend fun joinRoom(accountId: Long, roomJid: String, nickname: String, password: String = ""): Boolean {
        return try {
            val connection = xmppManager.getConnection(accountId) ?: return false
            val mucManager = MultiUserChatManager.getInstanceFor(connection)
            val jid = JidCreate.entityBareFrom(roomJid)
            val muc = mucManager.getMultiUserChat(jid)
            val resource = Resourcepart.from(nickname)
            if (password.isNotBlank()) {
                muc.join(resource, password)
            } else {
                muc.join(resource)
            }
            joinedChats["${accountId}_${roomJid}"] = muc
            roomDao.updateJoinStatus(accountId, roomJid, true)
            true
        } catch (e: SmackException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun leaveRoom(accountId: Long, roomJid: String) {
        try {
            joinedChats["${accountId}_${roomJid}"]?.leave()
            joinedChats.remove("${accountId}_${roomJid}")
        } catch (e: Exception) { /* ignore */ }
        roomDao.updateJoinStatus(accountId, roomJid, false)
    }

    suspend fun saveRoom(room: Room): Long = roomDao.insertRoom(room.toEntity())

    suspend fun deleteRoom(room: Room) = roomDao.deleteRoom(room.toEntity())

    fun getMuc(accountId: Long, roomJid: String): MultiUserChat? =
        joinedChats["${accountId}_${roomJid}"]

    suspend fun sendRoomMessage(accountId: Long, roomJid: String, body: String): Boolean {
        return try {
            val muc = joinedChats["${accountId}_${roomJid}"] ?: return false
            muc.sendMessage(body)
            true
        } catch (e: Exception) {
            false
        }
    }
}

