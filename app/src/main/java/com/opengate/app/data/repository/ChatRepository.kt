package com.opengate.app.data.repository

import com.opengate.app.data.model.Conversation
import com.opengate.app.data.model.ConversationRow
import com.opengate.app.data.model.Message
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * المحادثات والرسائل. الأمان (من يستطيع إنشاء محادثة/قراءة/إرسال) مفروض في القاعدة عبر RLS وtriggers
 * (sql/002_security_fixes.sql). تحديثات Realtime تتطلب إضافة جدول messages إلى publication (مُنفّذ في نفس الملف).
 */
class ChatRepository(private val client: SupabaseClient) {

    /** يبحث عن محادثة قائمة بين هذين الطرفين على نفس الإعلان، أو ينشئها لو أول مرة */
    suspend fun getOrCreateConversation(listingId: String?, sellerId: String, buyerId: String): String {
        suspend fun findExisting(): String? =
            client.postgrest["conversations"].select {
                filter {
                    eq("buyer_id", buyerId)
                    eq("seller_id", sellerId)
                    if (listingId != null) eq("listing_id", listingId)
                }
            }.decodeList<Conversation>()
                .firstOrNull { it.listing_id == listingId }   // يعالج حالة الإعلان الفارغ دون استثناء عند التكرار
                ?.id

        findExisting()?.let { return it }

        return runCatching {
            client.postgrest["conversations"]
                .insert(Conversation(listing_id = listingId, buyer_id = buyerId, seller_id = sellerId)) { select() }
                .decodeSingle<Conversation>()
                .id
        }.getOrElse { createError ->
            // سباق محتمل: أنشأها طرف/جهاز آخر في نفس اللحظة
            findExisting() ?: throw createError
        } ?: error("تعذّر إنشاء المحادثة")
    }

    /** كل محادثات المستخدم الحالي (كمشترٍ أو بائع) باستعلام واحد، مع اسم الإعلان المرتبط إن وُجد */
    suspend fun getMyConversations(userId: String): List<ConversationRow> =
        client.postgrest["conversations"]
            .select(Columns.raw("*, listings(title)")) {
                filter {
                    or {
                        eq("buyer_id", userId)
                        eq("seller_id", userId)
                    }
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ConversationRow>()

    suspend fun getMessages(conversationId: String): List<Message> =
        client.postgrest["messages"].select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun sendMessage(conversationId: String, senderId: String, content: String) {
        client.postgrest["messages"].insert(
            Message(conversation_id = conversationId, sender_id = senderId, content = content)
        )
    }

    /**
     * يفتح قناة Realtime على جدول الرسائل ويستمع فقط لرسائل هذه المحادثة —
     * كل رسالة جديدة (من أي طرف، بما فيها رسائلي أنا نفسي) تصل فوراً عبر onMessage.
     * يُرجع القناة نفسها ليتم إغلاقها لاحقاً (leaveChannel) عند مغادرة الشاشة.
     */
    fun subscribeToMessages(conversationId: String, scope: CoroutineScope, onMessage: (Message) -> Unit): RealtimeChannel {
        val channel = client.realtime.channel("conversation-$conversationId")
        // التسجيل المتزامن قبل subscribe() ضروري حتى لا يضيع أي حدث
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
        scope.launch {
            inserts
                .map { it.decodeRecord<Message>() }
                .filter { it.conversation_id == conversationId }
                .onEach(onMessage)
                .collect()
        }
        scope.launch { channel.subscribe() }
        return channel
    }

    suspend fun leaveChannel(channel: RealtimeChannel) {
        // removeChannel() يُلغي الاشتراك *ويزيل* القناة من قائمة Realtime.subscriptions معاً —
        // استخدام unsubscribe() وحدها يوقف الاستماع لكن يُبقي القناة مسجّلة، مما يسبب تسرّب ذاكرة بسيط
        // مع تكرار فتح/إغلاق شاشات الدردشة أو المزاد.
        runCatching { client.realtime.removeChannel(channel) }
    }
}
