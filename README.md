# Open Gate — مشروع أندرويد (Kotlin + Jetpack Compose + Supabase)

## خطوات التشغيل

1. **إنشاء مشروع Supabase** ثم في **SQL Editor** نفّذ بالترتيب:
   1. `sql/open_gate_schema.sql` (الجداول الأساسية)
   2. `sql/002_security_fixes.sql` (إصلاحات أمنية وكائنات ناقصة: triggers، دوال، views، سياسات Storage، Realtime، حذف الحساب…). آمن لإعادة التشغيل.

2. **ربط التطبيق بمشروعك**: انسخ `local.properties.example` إلى `local.properties` وضع فيه:
   ```
   SUPABASE_URL=https://YOUR_PROJECT.supabase.co
   SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
   ```
   (المفتاح العام مصمم ليكون ظاهراً في التطبيق؛ الحماية الفعلية عبر RLS. لا تضع أبداً `service_role` في التطبيق.)

3. **حساب المالك**: سجّل مستخدماً من التطبيق (ينشئ الـ trigger ملفه الشخصي تلقائياً)، ثم في SQL Editor:
   ```sql
   insert into admin_users (user_id, role_id)
   select '<AUTH_USER_UUID>', id from roles where key = 'owner';
   ```
   دور المالك لا يُمنح ولا يُعدَّل من التطبيق (يُمنع بـ trigger)؛ فقط من SQL Editor.

4. **إعدادات Auth في لوحة Supabase** (Authentication): فعّل تأكيد البريد، ارفع الحد الأدنى لكلمة المرور إلى 8+، وفعّل حماية كلمات المرور المسرّبة إن توفرت خطتك، وفعّل CAPTCHA إن أردت منع التسجيل الآلي.

5. **افتح المشروع** في Android Studio (دعه يولّد Gradle wrapper ويزامن)، ثم شغّل.

6. **الإصدار النهائي**: أنشئ keystore خاصاً بك وعرّف `RELEASE_STORE_FILE/…` في `local.properties`، واختبر `assembleRelease` (التصغير مفعّل: راجع `proguard-rules.pro`). ارفع `targetSdk/compileSdk` حسب متطلبات Google Play الحالية (يتطلب تحديث AGP/Compose).

## هيكل المشروع
- `sql/open_gate_schema.sql` — قاعدة البيانات الكاملة (منصات، تصنيفات، حقول ديناميكية، إعلانات، مزادات، دردشة، صلاحيات، **تقييمات**).
- `di/SupabaseProvider.kt` — عميل Supabase الموحّد (Auth, Postgrest, Realtime, Storage).
- `data/model/Models.kt` — نماذج مطابقة لجداول القاعدة، بما فيها `Review` و`RatingSummary`.
- `data/repository/` — `PlatformRepository`، `ListingRepository`، **`ReviewRepository`** (التقييمات).
- `ui/components/ReviewComponents.kt` — نجوم تقييم قابلة لإعادة الاستخدام + قسم مراجعات كامل.
- `ui/screens/home/` — الشاشة الرئيسية: قائمة جانبية منزلقة + شريط متحرك + شبكة منصات ديناميكية.
- `ui/screens/splash/` — شاشة ترحيب فاخرة.
- `ui/navigation/AppNavHost.kt` — التنقل؛ شاشات الإعلانات/الإنشاء/الإعدادات موضوعة كنقاط بداية جاهزة للتوسيع بنفس نمط HomeScreen.

## ما تم بناؤه (كل شيء متصل بقاعدة البيانات)
- تسجيل دخول/إنشاء حساب/خروج، وجلسة محفوظة.
- الرئيسية: قائمة جانبية، شريط متحرك (من جدول `featured_sponsors`)، شبكة منصات ديناميكية، حسابات التواصل (من `social_links`، مخفية حتى يفعّلها المالك).
- إنشاء إعلان بنموذج ديناميكي + صور إلزامية، عرض الإعلانات (صور متقلبة)، التفاصيل، التقييمات.
- الدردشة الفورية (Realtime)، المزاد المباشر (سعر وعدّاد ومزايدات حية، والتحقق من المزايدة داخل القاعدة).
- الإعدادات: ثيم (داكن/فاتح/حسب الجهاز)، عدد أعمدة العرض، مفتاح الإشعارات (تفضيل محفوظ فقط، لا يوجد Push بعد).
- لوحة المالك (تظهر فقط لمن هو مسؤول فعلياً في القاعدة): المنصات، المسؤولون بالبريد الإلكتروني، التصنيفات والحقول، حسابات التواصل، الشريط المتحرك.

## ما لم يُبنَ بعد / ملاحظات
- الإشعارات الفعلية (Push عبر FCM)، الملف الشخصي العام، البحث المتقدم، أيقونة نهائية.
- منصة المزادات تتطلب مراجعة (requires_moderation) في البذور: يظهر المزاد للجميع بعد اعتماده، والوقت يجري من لحظة الإنشاء. عطّل المراجعة لهذه المنصة إن أردت نشراً فورياً.
- صور حساب محذوف تبقى في Storage (الحذف المباشر من SQL محجوب في Supabase)؛ نظّفها دورياً بـ Edge Function أو يدوياً.
- حالة المزادات تُحدَّث عبر `refresh_auction_statuses()` (يستدعيها التطبيق عند عرض القائمة)؛ يمكن جدولتها بـ pg_cron أيضاً.
- راجع `CHANGES.md` لقائمة التصحيحات.
