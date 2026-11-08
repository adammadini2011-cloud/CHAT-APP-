# تفعيل تسجيل الدخول عبر Google في Infinity Chat

## ما هو جاهز في التطبيق؟
- زر **Continue with Google** موجود.
- تدفق OAuth باستخدام Authorization Code + PKCE موجود.
- التطبيق يستقبل الرجوع عبر: `infinitychat://auth/callback`.
- لا نضع Client Secret داخل تطبيق Android أو داخل GitHub.

## ما الذي ينقص؟
إنشاء OAuth Client في Google Cloud وإضافة بياناته إلى مزوّد Google في Supabase. لا يمكن إكمال تسجيل الدخول الحقيقي قبل هذه الخطوة.

## 1) إنشاء OAuth Client من الهاتف
1. افتح https://console.cloud.google.com/
2. سجّل الدخول بحساب Google.
3. أنشئ مشروعاً جديداً باسم `Infinity Chat` أو اختر مشروعاً موجوداً.
4. افتح Google Auth Platform وأكمل إعداد شاشة الموافقة (Branding / Audience / Data Access حسب ما يظهر).
5. إذا كان التطبيق في وضع Testing، أضف بريد Google الذي ستجرب به ضمن Test users.
6. افتح Clients ثم Create client.
7. اختر **Web application**.
8. في Authorized redirect URIs أضف هذا العنوان كما هو:

   `https://cuoofbmyandlhxwjkyqy.supabase.co/auth/v1/callback`

9. أنشئ العميل وانسخ **Client ID** و**Client Secret**. لا تنشرهما ولا ترسلهما في محادثة عامة.

## 2) إعداد Supabase
1. افتح https://supabase.com/dashboard/project/cuoofbmyandlhxwjkyqy/auth/providers
2. افتح Google وفعّل Enable Sign in with Google.
3. ضع Client ID وClient Secret اللذين أنشأتهما في Google.
4. احفظ التغييرات.
5. افتح Authentication → URL Configuration:
   - أضف إلى Redirect URLs: `infinitychat://auth/callback`
   - لا تستبدل Site URL الحالي إن لم تكن تعرف قيمته.

## 3) الاختبار
1. ثبّت أحدث APK مبني من المشروع.
2. اضغط Continue with Google.
3. اختر حساب Google ووافق على الصلاحيات.
4. يجب أن يعود المتصفح إلى Infinity Chat وتظهر جلسة المستخدم.

## ملاحظات أمان
- لا تضف Client Secret إلى ملفات Java أو XML أو GitHub.
- لا تستخدم Supabase service_role key داخل تطبيق Android.
- ظهور زر Google لا يعني أن تسجيل الدخول يعمل؛ لا نعدّه مكتملاً إلا بعد نجاح اختبار فعلي.
- Facebook يحتاج إعداد تطبيق مستقل وبيانات App ID / App Secret؛ يؤجل إلى ما بعد اختبار Google.
