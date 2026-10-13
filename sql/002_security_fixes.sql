-- ============================================================================
-- Open Gate — 002_security_fixes.sql
-- ترحيل أمني وتصحيحي. شغّله في Supabase SQL Editor بعد open_gate_schema.sql.
-- آمن لإعادة التشغيل أكثر من مرة (idempotent).
-- ============================================================================

-- ============================================================================
-- 0) دوال الصلاحيات: search_path ثابت + المالك يملك كل الصلاحيات دائماً
-- ============================================================================

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.admin_users au
    where au.user_id = auth.uid() and au.is_active
  );
$$;

create or replace function public.is_owner()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.admin_users au
    join public.roles r on r.id = au.role_id
    where au.user_id = auth.uid() and au.is_active and r.key = 'owner'
  );
$$;

create or replace function public.has_permission(perm_key text)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare
  v_admin_id uuid;
  v_role_id  uuid;
  v_role_key text;
  v_override boolean;
  v_role_has boolean;
begin
  select au.id, au.role_id, r.key into v_admin_id, v_role_id, v_role_key
  from public.admin_users au
  join public.roles r on r.id = au.role_id
  where au.user_id = auth.uid() and au.is_active;

  if v_admin_id is null then return false; end if;
  if v_role_key = 'owner' then return true; end if;

  select ov.is_granted into v_override
  from public.admin_user_permission_overrides ov
  join public.permissions p on p.id = ov.permission_id
  where ov.admin_user_id = v_admin_id and p.key = perm_key;

  if v_override is not null then return v_override; end if;

  select exists (
    select 1 from public.role_permissions rp
    join public.permissions p on p.id = rp.permission_id
    where rp.role_id = v_role_id and p.key = perm_key
  ) into v_role_has;

  return coalesce(v_role_has, false);
end;
$$;

-- نوع الحقل "خيار ثنائي" تستخدمه لوحة الإدارة وشاشة الإعلان لكنه كان غائباً عن enum القاعدة
alter type public.field_type add value if not exists 'choice_binary';

-- ============================================================================
-- 1) صلاحية content.manage + صلاحيات الأدوار الافتراضية
-- ============================================================================

insert into public.permissions (key, name_ar, description_ar) values
  ('content.manage', 'إدارة المحتوى', 'الشريط المتحرك وحسابات التواصل في الرئيسية')
on conflict (key) do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.roles r cross join public.permissions p
where r.key = 'owner'
on conflict do nothing;

-- تُزرع فقط إذا كان الدور بلا أي صلاحيات (لا نكتب فوق تخصيص المالك)
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.roles r join public.permissions p on p.key <> 'admins.manage'
where r.key = 'super_admin'
  and not exists (select 1 from public.role_permissions x where x.role_id = r.id)
on conflict do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.roles r join public.permissions p
  on p.key in ('platforms.manage','categories.manage','auctions.manage','content.manage')
where r.key = 'platform_manager'
  and not exists (select 1 from public.role_permissions x where x.role_id = r.id)
on conflict do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.roles r join public.permissions p
  on p.key in ('listings.approve','listings.remove')
where r.key = 'moderator'
  and not exists (select 1 from public.role_permissions x where x.role_id = r.id)
on conflict do nothing;

-- ============================================================================
-- 2) updated_at تلقائي
-- ============================================================================

create or replace function public.set_updated_at()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();
drop trigger if exists trg_platforms_updated_at on public.platforms;
create trigger trg_platforms_updated_at before update on public.platforms
  for each row execute function public.set_updated_at();
drop trigger if exists trg_listings_updated_at on public.listings;
create trigger trg_listings_updated_at before update on public.listings
  for each row execute function public.set_updated_at();

-- ============================================================================
-- 3) الملفات الشخصية: منع التوثيق الذاتي + إنشاء تلقائي + إخفاء الهاتف/الرخصة
-- ============================================================================

create or replace function public.guard_profile_write()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  -- service_role / SQL Editor / مسؤول يملك users.manage: بلا قيود
  if auth.uid() is null or public.has_permission('users.manage') then
    return new;
  end if;

  -- نوع الحساب مجرد تصنيف يختاره المستخدم؛ الذي يحجبه عن المستخدم هو التوثيق (is_verified) فقط
  if tg_op = 'INSERT' then
    new.is_verified := false;
  else
    new.id := old.id;
    new.is_verified := old.is_verified;
    -- تعديل بيانات النشاط التجاري يُسقط التوثيق حتى تتم مراجعته من جديد
    if new.business_name is distinct from old.business_name
       or new.business_license_no is distinct from old.business_license_no then
      new.is_verified := false;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_guard_profile_write on public.profiles;
create trigger trg_guard_profile_write before insert or update on public.profiles
  for each row execute function public.guard_profile_write();

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_type public.account_type := 'individual';
  v_name text;
  v_raw  text;
begin
  v_raw := new.raw_user_meta_data->>'account_type';
  if v_raw in ('individual', 'real_estate_office', 'car_dealer', 'business') then
    v_type := v_raw::public.account_type;
  end if;
  v_name := nullif(left(btrim(coalesce(new.raw_user_meta_data->>'full_name', '')), 100), '');

  insert into public.profiles (id, full_name, account_type)
  values (new.id, v_name, v_type)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ملفات للمستخدمين الحاليين الذين لا يملكون صفاً
insert into public.profiles (id, full_name)
select u.id, nullif(left(btrim(coalesce(u.raw_user_meta_data->>'full_name', '')), 100), '')
from auth.users u
where not exists (select 1 from public.profiles p where p.id = u.id);

-- الهاتف ورقم الرخصة لا يظهران لغير صاحبهما (الباقي عام ليظهر مع الإعلان)
revoke select on public.profiles from anon, authenticated;
grant select (id, full_name, avatar_url, account_type, business_name, is_verified, created_at)
  on public.profiles to anon, authenticated;

drop view if exists public.my_profile;
create view public.my_profile as
  select p.id, p.full_name, p.phone, p.avatar_url, p.account_type,
         p.business_name, p.business_license_no, p.is_verified
  from public.profiles p
  where p.id = auth.uid();
revoke all on public.my_profile from anon, authenticated;
grant select on public.my_profile to authenticated;

-- حدود أطوال
do $$ begin
  alter table public.profiles add constraint profiles_len_check check (
    coalesce(char_length(full_name), 0) <= 100 and coalesce(char_length(phone), 0) <= 32
    and coalesce(char_length(business_name), 0) <= 150 and coalesce(char_length(business_license_no), 0) <= 64
  ) not valid;
exception when duplicate_object then null; end $$;

-- ============================================================================
-- 4) الإعلانات: فرض الحالة والمراجعة من الخادم + اتساق المنصة/التصنيف
-- ============================================================================

create or replace function public.guard_listing_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_platform public.platforms%rowtype;
  v_cat_platform uuid;
  v_reviewer boolean;
  v_publish public.listing_status;
begin
  if auth.uid() is null then return new; end if;   -- service_role / SQL Editor

  v_reviewer := public.has_permission('listings.approve');

  select * into v_platform from public.platforms where id = new.platform_id;
  if not found then raise exception 'المنصة غير موجودة'; end if;
  v_publish := (case when v_platform.requires_moderation then 'pending_review' else 'published' end)::public.listing_status;

  if tg_op = 'INSERT'
     or new.platform_id is distinct from old.platform_id
     or new.category_id is distinct from old.category_id then
    if not v_platform.is_visible and not public.is_admin() then
      raise exception 'المنصة غير متاحة حالياً';
    end if;
    select platform_id into v_cat_platform from public.categories where id = new.category_id;
    if v_cat_platform is distinct from new.platform_id then
      raise exception 'التصنيف لا يتبع المنصة المختارة';
    end if;
  end if;

  if tg_op = 'INSERT' then
    new.owner_id := auth.uid();
    new.views_count := 0;
    new.favorites_count := 0;
    new.rejection_reason := null;
    new.published_at := null;
    new.expires_at := null;
    if new.status in ('published', 'pending_review') then
      new.status := v_publish;
    elsif new.status <> 'draft' then
      raise exception 'حالة الإعلان غير مسموحة عند الإنشاء';
    end if;
    if new.status = 'published' then
      new.published_at := now();
      new.expires_at := now() + make_interval(days => v_platform.default_listing_duration_days);
    end if;
    return new;
  end if;

  -- UPDATE
  if new.owner_id is distinct from old.owner_id then
    raise exception 'لا يمكن نقل ملكية الإعلان';
  end if;

  if v_reviewer then
    if new.status = 'published' and old.status is distinct from 'published' then
      new.published_at := now();
      new.expires_at := now() + make_interval(days => v_platform.default_listing_duration_days);
    end if;
    return new;
  end if;

  -- مالك عادي
  new.views_count := old.views_count;
  new.favorites_count := old.favorites_count;
  new.rejection_reason := old.rejection_reason;
  new.published_at := old.published_at;
  new.expires_at := old.expires_at;

  if new.status is distinct from old.status then
    if new.status = 'archived' then
      null;
    elsif old.status = 'published' and new.status = 'sold' then
      null;
    elsif old.status = 'pending_review' and new.status = 'draft' then
      null;
    elsif old.status = 'draft' and new.status in ('published', 'pending_review') then
      new.status := v_publish;
    else
      raise exception 'تغيير حالة الإعلان غير مسموح';
    end if;
    if new.status = 'published' then
      new.published_at := now();
      new.expires_at := now() + make_interval(days => v_platform.default_listing_duration_days);
    end if;
  elsif v_platform.requires_moderation and old.status = 'published'
        and (new.title is distinct from old.title
             or new.description is distinct from old.description
             or new.attributes is distinct from old.attributes) then
    new.status := 'pending_review';   -- تعديل جوهري بعد الاعتماد يعيد الإعلان للمراجعة
  end if;
  return new;
end;
$$;

drop trigger if exists trg_guard_listing_write on public.listings;
create trigger trg_guard_listing_write before insert or update on public.listings
  for each row execute function public.guard_listing_write();

do $$ begin alter table public.listings add constraint listings_price_check
  check (price is null or price >= 0) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.listings add constraint listings_title_len_check
  check (char_length(btrim(title)) between 1 and 200) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.listings add constraint listings_desc_len_check
  check (description is null or char_length(description) <= 5000) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.listings add constraint listings_currency_check
  check (currency in ('YER', 'USD', 'SAR')) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.listings add constraint listings_phone_len_check
  check (contact_phone is null or char_length(contact_phone) <= 32) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.listings add constraint listings_attrs_object_check
  check (jsonb_typeof(attributes) = 'object') not valid;
exception when duplicate_object then null; end $$;

-- ---------- صور الإعلانات ----------
create or replace function public.guard_listing_image_insert()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.listing_images where listing_id = new.listing_id) >= 10 then
    raise exception 'الحد الأقصى لصور الإعلان 10 صور';
  end if;
  return new;
end;
$$;
drop trigger if exists trg_guard_listing_image_insert on public.listing_images;
create trigger trg_guard_listing_image_insert before insert on public.listing_images
  for each row execute function public.guard_listing_image_insert();

do $$ begin alter table public.listing_images add constraint listing_images_url_check
  check (url ~ '^https://' and char_length(url) <= 2048) not valid;
exception when duplicate_object then null; end $$;

-- ============================================================================
-- 5) المزادات والمزايدات: التحقق داخل القاعدة (كان مفقوداً: guard_bid_insert)
-- ============================================================================

create or replace function public.guard_auction_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_is_auction_platform boolean;
begin
  if auth.uid() is null then return new; end if;

  select p.is_auction_platform into v_is_auction_platform
  from public.listings l join public.platforms p on p.id = l.platform_id
  where l.id = new.listing_id;
  if v_is_auction_platform is null then raise exception 'سلعة المزاد غير موجودة'; end if;

  if not public.has_permission('auctions.manage') then
    if not v_is_auction_platform then raise exception 'الإعلان لا يتبع منصة المزادات'; end if;
    if exists (select 1 from public.auctions a where a.listing_id = new.listing_id) then
      raise exception 'يوجد مزاد لهذا الإعلان مسبقاً';
    end if;
    if new.starting_price is null or new.starting_price <= 0 then raise exception 'سعر البداية غير صالح'; end if;
    if new.min_increment is null or new.min_increment <= 0 then raise exception 'قيمة الزيادة غير صالحة'; end if;
    if new.ends_at <= new.starts_at or new.ends_at <= now() then raise exception 'وقت انتهاء المزاد غير صالح'; end if;
    if new.ends_at - new.starts_at > interval '30 days' then raise exception 'مدة المزاد لا تتجاوز 30 يوماً'; end if;
    new.current_price := new.starting_price;
    new.status := (case when new.starts_at <= now() then 'live' else 'scheduled' end)::public.auction_status;
  end if;
  return new;
end;
$$;
drop trigger if exists trg_guard_auction_insert on public.auctions;
create trigger trg_guard_auction_insert before insert on public.auctions
  for each row execute function public.guard_auction_insert();

create or replace function public.guard_bid_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  a public.auctions%rowtype;
  v_owner uuid;
  v_min numeric;
begin
  select * into a from public.auctions where id = new.auction_id for update;
  if not found then raise exception 'المزاد غير موجود'; end if;

  select owner_id into v_owner from public.listings where id = a.listing_id;
  if v_owner = new.bidder_id then raise exception 'لا يمكنك المزايدة على مزادك الخاص'; end if;
  if a.status in ('ended', 'cancelled') then raise exception 'المزايدة مغلقة: المزاد منتهٍ أو ملغى'; end if;
  if now() < a.starts_at then raise exception 'المزايدة لم تبدأ بعد'; end if;
  if now() >= a.ends_at then raise exception 'المزايدة مغلقة: انتهى وقت المزاد'; end if;

  v_min := coalesce(a.current_price, a.starting_price) + a.min_increment;
  if new.amount < v_min then
    raise exception 'المزايدة يجب أن تكون % على الأقل', v_min;
  end if;

  update public.auctions set current_price = new.amount, status = 'live' where id = a.id;
  return new;
end;
$$;
drop trigger if exists trg_guard_bid_insert on public.bids;
create trigger trg_guard_bid_insert before insert on public.bids
  for each row execute function public.guard_bid_insert();

create or replace function public.refresh_auction_statuses()
returns void language sql security definer set search_path = public as $$
  update public.auctions set status = 'ended'
    where status in ('live', 'scheduled') and ends_at <= now();
  update public.auctions set status = 'live'
    where status = 'scheduled' and starts_at <= now() and ends_at > now();
$$;
revoke all on function public.refresh_auction_statuses() from public;
grant execute on function public.refresh_auction_statuses() to anon, authenticated;

-- إنشاء مزاد ذرّي (إعلان + مزاد في معاملة واحدة) تحت صلاحيات المستخدم (RLS مطبّقة)
create or replace function public.create_auction(
  p_title text, p_starting_price numeric, p_min_increment numeric, p_duration_hours int,
  p_city text default null, p_phone text default null, p_currency text default 'YER'
) returns uuid
language plpgsql security invoker set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_platform uuid; v_category uuid; v_listing uuid; v_auction uuid;
begin
  if v_uid is null then raise exception 'يجب تسجيل الدخول لبدء مزاد'; end if;
  if p_title is null or char_length(btrim(p_title)) not between 3 and 200 then raise exception 'عنوان المزاد غير صالح'; end if;
  if p_starting_price is null or p_starting_price <= 0 then raise exception 'سعر البداية غير صالح'; end if;
  if p_min_increment is null or p_min_increment <= 0 then raise exception 'قيمة الزيادة غير صالحة'; end if;
  if p_duration_hours is null or p_duration_hours not between 1 and 720 then raise exception 'مدة المزاد يجب أن تكون بين ساعة و30 يوماً'; end if;
  if p_currency not in ('YER', 'USD', 'SAR') then raise exception 'عملة غير مدعومة'; end if;

  select id into v_platform from public.platforms
   where is_auction_platform and is_visible order by sort_order limit 1;
  if v_platform is null then raise exception 'منصة المزادات غير متاحة حالياً'; end if;

  select id into v_category from public.categories
   where platform_id = v_platform and is_visible order by sort_order, created_at limit 1;
  if v_category is null then raise exception 'لا يوجد تصنيف متاح للمزادات'; end if;

  insert into public.listings (owner_id, platform_id, category_id, title, price, currency, city, contact_phone, status)
  values (v_uid, v_platform, v_category, btrim(p_title), p_starting_price, p_currency,
          nullif(btrim(coalesce(p_city, '')), ''), nullif(btrim(coalesce(p_phone, '')), ''), 'published')
  returning id into v_listing;

  insert into public.auctions (listing_id, starting_price, min_increment, current_price, starts_at, ends_at, status)
  values (v_listing, p_starting_price, p_min_increment, p_starting_price, now(),
          now() + make_interval(hours => p_duration_hours), 'live')
  returning id into v_auction;

  return v_auction;
end;
$$;
revoke all on function public.create_auction(text, numeric, numeric, int, text, text, text) from public, anon;
grant execute on function public.create_auction(text, numeric, numeric, int, text, text, text) to authenticated;

-- ============================================================================
-- 6) الدردشة: منع انتحال البائع/المشتري وتعديل رسائل الغير
-- ============================================================================

drop policy if exists "conversations_participants_only" on public.conversations;
drop policy if exists "conversations_select_participants" on public.conversations;
drop policy if exists "conversations_insert_buyer" on public.conversations;

create policy "conversations_select_participants" on public.conversations for select
  using (buyer_id = auth.uid() or seller_id = auth.uid());
create policy "conversations_insert_buyer" on public.conversations for insert
  with check (buyer_id = auth.uid() and seller_id <> auth.uid());

create or replace function public.guard_conversation_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_owner uuid;
begin
  if auth.uid() is null then return new; end if;
  if new.listing_id is not null then
    select owner_id into v_owner from public.listings where id = new.listing_id;
    if v_owner is null then raise exception 'الإعلان غير موجود'; end if;
    if v_owner is distinct from new.seller_id then raise exception 'البائع لا يطابق صاحب الإعلان'; end if;
  end if;
  return new;
end;
$$;
drop trigger if exists trg_guard_conversation_insert on public.conversations;
create trigger trg_guard_conversation_insert before insert on public.conversations
  for each row execute function public.guard_conversation_insert();

do $$ begin
  create unique index if not exists conversations_buyer_seller_nolisting
    on public.conversations (buyer_id, seller_id) where listing_id is null;
exception when others then
  raise notice 'تعذّر إنشاء الفهرس الفريد للمحادثات (توجد تكرارات قديمة): %', sqlerrm;
end $$;

drop policy if exists "messages_update_mark_read_by_recipient" on public.messages;
create policy "messages_update_mark_read_by_recipient" on public.messages for update
  using (
    sender_id <> auth.uid() and exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id and (c.buyer_id = auth.uid() or c.seller_id = auth.uid()))
  )
  with check (
    sender_id <> auth.uid() and exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id and (c.buyer_id = auth.uid() or c.seller_id = auth.uid()))
  );

create or replace function public.guard_message_update()
returns trigger language plpgsql set search_path = public as $$
begin
  if auth.uid() is null then return new; end if;
  if new.conversation_id is distinct from old.conversation_id
     or new.sender_id is distinct from old.sender_id
     or new.content is distinct from old.content
     or new.attachment_url is distinct from old.attachment_url
     or new.created_at is distinct from old.created_at then
    raise exception 'لا يمكن تعديل محتوى الرسائل';
  end if;
  return new;
end;
$$;
drop trigger if exists trg_guard_message_update on public.messages;
create trigger trg_guard_message_update before update on public.messages
  for each row execute function public.guard_message_update();

do $$ begin alter table public.messages add constraint messages_content_check
  check (coalesce(char_length(content), 0) <= 4000
         and (btrim(coalesce(content, '')) <> '' or attachment_url is not null)) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.messages add constraint messages_attachment_check
  check (attachment_url is null or attachment_url ~ '^https://') not valid;
exception when duplicate_object then null; end $$;

-- ============================================================================
-- 7) التقييمات: تقييم واحد لكل مستخدم + منع تقييم إعلانك + عدم تغيير الهدف
-- ============================================================================

do $$ begin
  create unique index if not exists reviews_one_per_listing
    on public.reviews (reviewer_id, listing_id) where listing_id is not null;
  create unique index if not exists reviews_one_per_seller
    on public.reviews (reviewer_id, reviewee_id) where listing_id is null and reviewee_id is not null;
exception when others then
  raise notice 'تعذّر إنشاء فهارس التقييمات (توجد تكرارات قديمة): %', sqlerrm;
end $$;

create or replace function public.guard_review_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_owner uuid;
begin
  if auth.uid() is null then return new; end if;
  if tg_op = 'UPDATE' then
    if new.reviewer_id is distinct from old.reviewer_id
       or new.reviewee_id is distinct from old.reviewee_id
       or new.listing_id is distinct from old.listing_id then
      raise exception 'لا يمكن تغيير هدف التقييم';
    end if;
    return new;
  end if;
  if new.listing_id is not null then
    select owner_id into v_owner from public.listings where id = new.listing_id;
    if v_owner is null then raise exception 'الإعلان غير موجود'; end if;
    if v_owner = new.reviewer_id then raise exception 'لا يمكنك تقييم إعلانك الخاص'; end if;
    if new.reviewee_id is not null and new.reviewee_id <> v_owner then
      raise exception 'المقيَّم لا يطابق صاحب الإعلان';
    end if;
  end if;
  return new;
end;
$$;
drop trigger if exists trg_guard_review_write on public.reviews;
create trigger trg_guard_review_write before insert or update on public.reviews
  for each row execute function public.guard_review_write();

do $$ begin alter table public.reviews add constraint reviews_comment_len_check
  check (comment is null or char_length(comment) <= 1000) not valid;
exception when duplicate_object then null; end $$;

do $$ begin
  alter view public.seller_rating_summary set (security_invoker = true);
  alter view public.listing_rating_summary set (security_invoker = true);
exception when others then raise notice 'security_invoker غير مدعوم: %', sqlerrm; end $$;

-- ============================================================================
-- 8) المسؤولون: الإدارة العليا للمالك فقط (مطابقة لما تعرضه الواجهة)
-- ============================================================================

drop policy if exists "roles_write_admins_manage_only" on public.roles;
drop policy if exists "roles_update_admins_manage_only" on public.roles;
drop policy if exists "roles_insert_owner_only" on public.roles;
drop policy if exists "roles_update_owner_only" on public.roles;
create policy "roles_insert_owner_only" on public.roles for insert
  with check (public.is_owner() and key <> 'owner');
create policy "roles_update_owner_only" on public.roles for update
  using (public.is_owner() and is_system_role = false)
  with check (public.is_owner() and is_system_role = false and key <> 'owner');

drop policy if exists "role_permissions_write_admins_manage_only" on public.role_permissions;
drop policy if exists "role_permissions_write_owner_only" on public.role_permissions;
create policy "role_permissions_write_owner_only" on public.role_permissions for all
  using (public.is_owner()) with check (public.is_owner());

drop policy if exists "admin_users_select_self_or_manager" on public.admin_users;
drop policy if exists "admin_users_write_manager_only" on public.admin_users;
drop policy if exists "admin_users_update_manager_only" on public.admin_users;
drop policy if exists "admin_users_delete_manager_only_not_owner" on public.admin_users;
drop policy if exists "admin_users_select_self_or_owner" on public.admin_users;
drop policy if exists "admin_users_insert_owner_only" on public.admin_users;
drop policy if exists "admin_users_update_owner_only" on public.admin_users;
drop policy if exists "admin_users_delete_owner_only" on public.admin_users;
create policy "admin_users_select_self_or_owner" on public.admin_users for select
  using (user_id = auth.uid() or public.is_owner());
create policy "admin_users_insert_owner_only" on public.admin_users for insert
  with check (public.is_owner());
create policy "admin_users_update_owner_only" on public.admin_users for update
  using (public.is_owner()) with check (public.is_owner());
create policy "admin_users_delete_owner_only" on public.admin_users for delete
  using (public.is_owner());

create or replace function public.guard_admin_users()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_owner_role uuid;
  v_old_role_key text;
begin
  if auth.uid() is null then
    return coalesce(new, old);   -- service_role / SQL Editor
  end if;

  select id into v_owner_role from public.roles where key = 'owner';

  if tg_op in ('UPDATE', 'DELETE') then
    select key into v_old_role_key from public.roles where id = old.role_id;
    if v_old_role_key = 'owner' then
      raise exception 'لا يمكن تعديل أو حذف حساب المالك من التطبيق';
    end if;
    if old.user_id = auth.uid() then
      raise exception 'لا يمكنك تعديل صلاحياتك بنفسك';
    end if;
  end if;

  if tg_op in ('INSERT', 'UPDATE') then
    if new.role_id = v_owner_role then
      raise exception 'لا يمكن منح دور المالك من التطبيق';
    end if;
    if new.user_id = auth.uid() then
      raise exception 'لا يمكنك تعيين نفسك مشرفاً';
    end if;
  end if;

  if tg_op = 'UPDATE' and new.user_id is distinct from old.user_id then
    raise exception 'لا يمكن تغيير حساب المشرف';
  end if;

  if tg_op = 'INSERT' then new.granted_by := auth.uid(); end if;

  insert into public.audit_logs (admin_user_id, action, target_table, target_id, details)
  values (
    (select id from public.admin_users where user_id = auth.uid()),
    'admin_users.' || lower(tg_op), 'admin_users',
    coalesce(new.id, old.id),
    jsonb_build_object('user_id', coalesce(new.user_id, old.user_id),
                       'role_id', coalesce(new.role_id, old.role_id))
  );
  return coalesce(new, old);
end;
$$;
drop trigger if exists trg_guard_admin_users on public.admin_users;
create trigger trg_guard_admin_users before insert or update or delete on public.admin_users
  for each row execute function public.guard_admin_users();

drop policy if exists "overrides_select_self_or_manager" on public.admin_user_permission_overrides;
drop policy if exists "overrides_write_manager_only" on public.admin_user_permission_overrides;
drop policy if exists "overrides_select_self_or_owner" on public.admin_user_permission_overrides;
drop policy if exists "overrides_write_owner_only" on public.admin_user_permission_overrides;
create policy "overrides_select_self_or_owner" on public.admin_user_permission_overrides for select
  using (
    exists (select 1 from public.admin_users au where au.id = admin_user_id and au.user_id = auth.uid())
    or public.is_owner()
  );
create policy "overrides_write_owner_only" on public.admin_user_permission_overrides for all
  using (public.is_owner()) with check (public.is_owner());

drop policy if exists "audit_logs_insert_any_admin" on public.audit_logs;
drop policy if exists "audit_logs_insert_own_admin" on public.audit_logs;
create policy "audit_logs_insert_own_admin" on public.audit_logs for insert
  with check (
    public.is_admin()
    and admin_user_id in (select id from public.admin_users where user_id = auth.uid())
  );

-- ---------- واجهات وRPC تستخدمها لوحة الإدارة ----------
drop view if exists public.my_admin_status;
create view public.my_admin_status as
  select au.id as admin_user_id, au.user_id, r.key as role_key, r.name_ar as role_name, au.is_active
  from public.admin_users au join public.roles r on r.id = au.role_id
  where au.user_id = auth.uid() and au.is_active;
revoke all on public.my_admin_status from anon, authenticated;
grant select on public.my_admin_status to authenticated;

drop view if exists public.my_permissions;
create view public.my_permissions as
  select p.key from public.permissions p where public.has_permission(p.key);
revoke all on public.my_permissions from anon, authenticated;
grant select on public.my_permissions to authenticated;

create or replace function public.find_user_id_by_email(p_email text)
returns uuid language plpgsql stable security definer set search_path = public, auth as $$
declare v_id uuid;
begin
  if not public.is_owner() then raise exception 'غير مصرّح'; end if;
  select id into v_id from auth.users where lower(email) = lower(btrim(p_email)) limit 1;
  return v_id;
end;
$$;
revoke all on function public.find_user_id_by_email(text) from public, anon;
grant execute on function public.find_user_id_by_email(text) to authenticated;

create or replace function public.get_admin_permissions(p_admin_user_id uuid)
returns setof text language plpgsql stable security definer set search_path = public as $$
declare v_role uuid; v_role_key text;
begin
  if not public.is_owner() then raise exception 'غير مصرّح'; end if;
  select au.role_id, r.key into v_role, v_role_key
  from public.admin_users au join public.roles r on r.id = au.role_id
  where au.id = p_admin_user_id;
  if v_role is null then return; end if;
  if v_role_key = 'owner' then
    return query select p.key from public.permissions p;
    return;
  end if;
  return query
    select p.key from public.permissions p
    where coalesce(
      (select ov.is_granted from public.admin_user_permission_overrides ov
        where ov.admin_user_id = p_admin_user_id and ov.permission_id = p.id),
      exists (select 1 from public.role_permissions rp
               where rp.role_id = v_role and rp.permission_id = p.id));
end;
$$;
revoke all on function public.get_admin_permissions(uuid) from public, anon;
grant execute on function public.get_admin_permissions(uuid) to authenticated;

create or replace function public.set_admin_permissions(p_admin_user_id uuid, p_granted text[])
returns void language plpgsql security definer set search_path = public as $$
declare v_granted text[] := coalesce(p_granted, '{}');
begin
  if not public.is_owner() then raise exception 'غير مصرّح'; end if;
  if not exists (select 1 from public.admin_users where id = p_admin_user_id) then
    raise exception 'المشرف غير موجود';
  end if;
  if exists (select 1 from public.admin_users au join public.roles r on r.id = au.role_id
             where au.id = p_admin_user_id and r.key = 'owner') then
    raise exception 'لا يمكن تعديل صلاحيات المالك';
  end if;

  delete from public.admin_user_permission_overrides where admin_user_id = p_admin_user_id;

  insert into public.admin_user_permission_overrides (admin_user_id, permission_id, is_granted)
  select p_admin_user_id, p.id, (p.key = any (v_granted))
  from public.permissions p
  where (p.key = any (v_granted)) is distinct from exists (
    select 1 from public.role_permissions rp
    join public.admin_users au on au.role_id = rp.role_id
    where au.id = p_admin_user_id and rp.permission_id = p.id
  );
end;
$$;
revoke all on function public.set_admin_permissions(uuid, text[]) from public, anon;
grant execute on function public.set_admin_permissions(uuid, text[]) to authenticated;


-- ============================================================================
-- 8-ب) إدارة المنصات (إنشاء/إخفاء/إظهار/حذف) لمالك المشروع فقط
-- ============================================================================
drop policy if exists "platforms_write_admin_only" on public.platforms;
drop policy if exists "platforms_update_admin_only" on public.platforms;
drop policy if exists "platforms_delete_admin_only" on public.platforms;
drop policy if exists "platforms_insert_owner_only" on public.platforms;
drop policy if exists "platforms_update_owner_only" on public.platforms;
drop policy if exists "platforms_delete_owner_only" on public.platforms;
create policy "platforms_insert_owner_only" on public.platforms for insert with check (public.is_owner());
create policy "platforms_update_owner_only" on public.platforms for update
  using (public.is_owner()) with check (public.is_owner());
create policy "platforms_delete_owner_only" on public.platforms for delete using (public.is_owner());

-- ============================================================================
-- 9) المحتوى: social_links و featured_sponsors (كانا مفقودين من المخطط)
-- ============================================================================

create table if not exists public.social_links (
  key text primary key,
  label_ar text not null,
  value text not null default '',
  is_visible boolean not null default false,
  sort_order int not null default 0,
  updated_at timestamptz not null default now()
);
create table if not exists public.featured_sponsors (
  id uuid primary key default gen_random_uuid(),
  text_ar text not null,
  is_visible boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

insert into public.social_links (key, label_ar, sort_order) values
  ('whatsapp', 'واتساب', 1), ('telegram', 'تيليجرام', 2), ('facebook', 'فيسبوك', 3),
  ('instagram', 'إنستغرام', 4), ('x', 'إكس (تويتر)', 5), ('tiktok', 'تيك توك', 6),
  ('youtube', 'يوتيوب', 7), ('website', 'الموقع الإلكتروني', 8)
on conflict (key) do nothing;

do $$ begin alter table public.social_links add constraint social_links_value_len check (char_length(value) <= 300) not valid;
exception when duplicate_object then null; end $$;
do $$ begin alter table public.featured_sponsors add constraint featured_sponsors_text_len check (char_length(text_ar) between 1 and 200) not valid;
exception when duplicate_object then null; end $$;

alter table public.social_links enable row level security;
alter table public.featured_sponsors enable row level security;

drop policy if exists "social_links_select" on public.social_links;
drop policy if exists "social_links_insert" on public.social_links;
drop policy if exists "social_links_update" on public.social_links;
drop policy if exists "social_links_delete" on public.social_links;
create policy "social_links_select" on public.social_links for select
  using (is_visible = true or public.has_permission('content.manage'));
create policy "social_links_insert" on public.social_links for insert with check (public.has_permission('content.manage'));
create policy "social_links_update" on public.social_links for update
  using (public.has_permission('content.manage')) with check (public.has_permission('content.manage'));
create policy "social_links_delete" on public.social_links for delete using (public.has_permission('content.manage'));

drop policy if exists "featured_sponsors_select" on public.featured_sponsors;
drop policy if exists "featured_sponsors_insert" on public.featured_sponsors;
drop policy if exists "featured_sponsors_update" on public.featured_sponsors;
drop policy if exists "featured_sponsors_delete" on public.featured_sponsors;
create policy "featured_sponsors_select" on public.featured_sponsors for select
  using (is_visible = true or public.has_permission('content.manage'));
create policy "featured_sponsors_insert" on public.featured_sponsors for insert with check (public.has_permission('content.manage'));
create policy "featured_sponsors_update" on public.featured_sponsors for update
  using (public.has_permission('content.manage')) with check (public.has_permission('content.manage'));
create policy "featured_sponsors_delete" on public.featured_sponsors for delete using (public.has_permission('content.manage'));

-- ============================================================================
-- 10) تصنيفات إضافية يستخدمها التطبيق (بدل التصنيفات الوهمية داخل الكود)
-- ============================================================================

insert into public.categories (platform_id, key, name_ar, sort_order)
select p.id, c.key, c.name_ar, c.sort_order
from public.platforms p
join (values
  ('real_estate', 'buildings', 'عمائر وأبراج سكنية وتجارية', 60),
  ('real_estate', 'resorts',   'استراحات وشاليهات',          70),
  ('real_estate', 'farms',     'مزارع وأحواش',               80),
  ('real_estate', 'other',     'أخرى',                       99),
  ('cars', 'pickups', 'بيك آب / شاص / هيلوكس', 50),
  ('cars', 'buses',   'باصات وفانات ونقل ركاب', 60),
  ('cars', 'sports',  'سيارات رياضية / كوبيه',  70),
  ('cars', 'economy', 'سيارات اقتصادية / هاتشباك', 80),
  ('cars', 'salvage', 'سيارات مصدومة وتشليح',   90),
  ('cars', 'other',   'أخرى',                   99),
  ('auctions', 'general', 'مزادات عامة', 1)
) as c(platform_key, key, name_ar, sort_order) on c.platform_key = p.key
on conflict (platform_id, key) do nothing;

insert into public.category_field_schemas (category_id, field_key, label_ar, field_type, is_required, options, sort_order)
select c.id, f.field_key, f.label_ar, f.field_type::public.field_type, f.is_required, f.options::jsonb, f.sort_order
from public.categories c
join public.platforms p on p.id = c.platform_id and p.key = 'cars'
cross join (values
  ('brand', 'الماركة', 'text', true, null, 1),
  ('model', 'الموديل', 'text', true, null, 2),
  ('year', 'سنة الصنع', 'number', true, null, 3),
  ('mileage_km', 'الممشى (كم)', 'number', true, null, 4),
  ('transmission', 'ناقل الحركة', 'select', false, '["أوتوماتيك","عادي"]', 5),
  ('fuel_type', 'نوع الوقود', 'select', false, '["بنزين","ديزل","هجين","كهربائي"]', 6),
  ('color', 'اللون', 'text', false, null, 7)
) as f(field_key, label_ar, field_type, is_required, options, sort_order)
where c.key in ('pickups', 'buses', 'sports', 'economy', 'salvage')
on conflict (category_id, field_key) do nothing;

insert into public.category_field_schemas (category_id, field_key, label_ar, field_type, is_required, options, sort_order)
select c.id, 'area_sqm', 'المساحة (م²)', 'number'::public.field_type, true, null::jsonb, 1
from public.categories c
join public.platforms p on p.id = c.platform_id and p.key = 'real_estate'
where c.key in ('buildings', 'resorts', 'farms')
on conflict (category_id, field_key) do nothing;

-- ============================================================================
-- 11) Storage: bucket الصور + سياسات (كانت غائبة فكان الرفع يفشل)
-- ============================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('listing-images', 'listing-images', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = true, file_size_limit = 5242880,
      allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

-- المسار المسموح: listings/<listing_id>/<file> ويملك المستخدم ذلك الإعلان
create or replace function public.owns_listing_image_path(p_name text)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce(
    split_part(p_name, '/', 1) = 'listings'
    and split_part(p_name, '/', 4) = ''
    and split_part(p_name, '/', 3) <> ''
    and exists (
      select 1 from public.listings l
      where l.id::text = split_part(p_name, '/', 2) and l.owner_id = auth.uid()
    ), false);
$$;

drop policy if exists "listing_images_insert_owner" on storage.objects;
drop policy if exists "listing_images_select_owner" on storage.objects;
drop policy if exists "listing_images_delete_owner" on storage.objects;
create policy "listing_images_insert_owner" on storage.objects for insert to authenticated
  with check (bucket_id = 'listing-images' and public.owns_listing_image_path(name));
create policy "listing_images_select_owner" on storage.objects for select to authenticated
  using (bucket_id = 'listing-images' and public.owns_listing_image_path(name));
create policy "listing_images_delete_owner" on storage.objects for delete to authenticated
  using (bucket_id = 'listing-images' and public.owns_listing_image_path(name));

-- ============================================================================
-- 12) Realtime: الجداول لم تكن ضمن publication فلم تصل أي تحديثات لحظية
-- ============================================================================

do $$
declare t text;
begin
  foreach t in array array['messages', 'bids', 'auctions'] loop
    if not exists (select 1 from pg_publication_tables
                   where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
exception when undefined_object then
  raise notice 'publication supabase_realtime غير موجود';
end $$;

-- ============================================================================
-- 13) حذف الحساب فعلياً (متطلب Google Play) — كان التطبيق يحذف صف profiles فقط وبصمت يفشل
-- ============================================================================

create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = public, auth as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'يجب تسجيل الدخول'; end if;

  if exists (select 1 from public.admin_users au join public.roles r on r.id = au.role_id
             where au.user_id = v_uid and r.key = 'owner') then
    raise exception 'لا يمكن حذف حساب مالك المشروع من التطبيق';
  end if;

  if exists (select 1 from public.auctions a join public.bids b on b.auction_id = a.id
             where b.bidder_id = v_uid and a.status in ('live', 'scheduled')
               and a.ends_at > now() and b.amount = a.current_price) then
    raise exception 'لا يمكن حذف الحساب وأنت صاحب أعلى مزايدة في مزاد جارٍ';
  end if;

  delete from public.messages
   where sender_id = v_uid
      or conversation_id in (select id from public.conversations where buyer_id = v_uid or seller_id = v_uid);
  delete from public.conversations where buyer_id = v_uid or seller_id = v_uid;
  delete from public.bids where bidder_id = v_uid;
  delete from auth.users where id = v_uid;   -- يحذف profiles والإعلانات والمفضلة والتقييمات تسلسلياً
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- ============================================================================
-- 14) صلاحيات الجداول: الزائر (anon) قراءة فقط
-- ============================================================================

revoke insert, update, delete, truncate on all tables in schema public from anon;

-- نهاية الملف
