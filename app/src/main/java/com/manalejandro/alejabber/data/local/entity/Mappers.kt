package com.manalejandro.alejabber.data.local.entity

import com.manalejandro.alejabber.domain.model.*

fun AccountEntity.toDomain(status: ConnectionStatus = ConnectionStatus.OFFLINE) = Account(
    id = id, jid = jid, password = password, server = server, port = port,
    useTls = useTls, resource = resource, isEnabled = isEnabled,
    status = status, statusMessage = statusMessage, avatarUrl = avatarUrl
)

fun Account.toEntity() = AccountEntity(
    id = id, jid = jid, password = password, server = server, port = port,
    useTls = useTls, resource = resource, isEnabled = isEnabled,
    statusMessage = statusMessage, avatarUrl = avatarUrl
)

fun ContactEntity.toDomain() = Contact(
    id = id, accountId = accountId, jid = jid, nickname = nickname,
    groups = groups.split(",").filter { it.isNotBlank() },
    presence = try { PresenceStatus.valueOf(presence) } catch (e: Exception) { PresenceStatus.OFFLINE },
    statusMessage = statusMessage, avatarUrl = avatarUrl, isBlocked = isBlocked,
    subscriptionState = try { SubscriptionState.valueOf(subscriptionState) } catch (e: Exception) { SubscriptionState.NONE }
)

fun Contact.toEntity() = ContactEntity(
    id = id, accountId = accountId, jid = jid, nickname = nickname,
    groups = groups.joinToString(","), presence = presence.name,
    statusMessage = statusMessage, avatarUrl = avatarUrl, isBlocked = isBlocked,
    subscriptionState = subscriptionState.name
)

fun MessageEntity.toDomain() = Message(
    id = id, stanzaId = stanzaId, accountId = accountId,
    conversationJid = conversationJid, fromJid = fromJid, toJid = toJid,
    body = body, timestamp = timestamp,
    direction = try { MessageDirection.valueOf(direction) } catch (e: Exception) { MessageDirection.INCOMING },
    status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.PENDING },
    encryptionType = try { EncryptionType.valueOf(encryptionType) } catch (e: Exception) { EncryptionType.NONE },
    mediaType = try { MediaType.valueOf(mediaType) } catch (e: Exception) { MediaType.TEXT },
    mediaUrl = mediaUrl, mediaLocalPath = mediaLocalPath, mediaMimeType = mediaMimeType,
    mediaSize = mediaSize, mediaName = mediaName, audioDurationMs = audioDurationMs,
    isRead = isRead, isEdited = isEdited, isDeleted = isDeleted, replyToId = replyToId
)

fun Message.toEntity() = MessageEntity(
    id = id, stanzaId = stanzaId, accountId = accountId,
    conversationJid = conversationJid, fromJid = fromJid, toJid = toJid,
    body = body, timestamp = timestamp, direction = direction.name, status = status.name,
    encryptionType = encryptionType.name, mediaType = mediaType.name,
    mediaUrl = mediaUrl, mediaLocalPath = mediaLocalPath, mediaMimeType = mediaMimeType,
    mediaSize = mediaSize, mediaName = mediaName, audioDurationMs = audioDurationMs,
    isRead = isRead, isEdited = isEdited, isDeleted = isDeleted, replyToId = replyToId
)

fun RoomEntity.toDomain() = Room(
    id = id, accountId = accountId, jid = jid, nickname = nickname, name = name,
    description = description, topic = topic, password = password,
    isJoined = isJoined, isFavorite = isFavorite, participantCount = participantCount,
    avatarUrl = avatarUrl, unreadCount = unreadCount, lastMessage = lastMessage,
    lastMessageTime = lastMessageTime
)

fun Room.toEntity() = RoomEntity(
    id = id, accountId = accountId, jid = jid, nickname = nickname, name = name,
    description = description, topic = topic, password = password,
    isJoined = isJoined, isFavorite = isFavorite, participantCount = participantCount,
    avatarUrl = avatarUrl, unreadCount = unreadCount, lastMessage = lastMessage,
    lastMessageTime = lastMessageTime
)

