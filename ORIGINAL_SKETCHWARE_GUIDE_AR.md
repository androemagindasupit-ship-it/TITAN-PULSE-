# TITAN PULSE — تحويل إلى تطبيق Android / Sketchware Pro

## ما الذي تم إصلاحه في نسخة HTML

1. مشكلة شاشة البداية التي كانت تظهر كخلفية رمادية/زر تشغيل للفيديو: تمت إضافة شاشة fallback حقيقية فيها عنوان TITAN PULSE والنص وحالة التحميل، مع جعل الفيديو اختيارياً وغير قادر على إخفاء النص عند فشل التشغيل.
2. تحسين ملاءمة الهاتف: تصغير شريط العنوان، منع تداخل الأزرار، تحسين الشريط السفلي، وتكبير حقول الإدخال المناسبة للمس.
3. حفظ مكان المستخدم: آخر قسم مفتوح، مساحة العمل الحالية، ومواقع التمرير تُحفظ محلياً وتُستعاد عند إعادة فتح التطبيق.
4. حفظ المشروع بشكل أقوى: يتم الآن تحديث `aiStudioProject` أيضاً، مع مرآة إلى IndexedDB عندما تكون متاحة، إضافة إلى localStorage القديم.
5. الإشعارات: الصفحة تستدعي جسر Android باسم `AndroidBridge` عند وجوده. في المتصفح يبقى fallback إلى Web Notification / Toast.
6. أضيف جسر Java لـ Sketchware Pro يحفظ الحالة والمشروع ومساحات العمل ويُصدر إشعارات Android حقيقية.

## ملفات الحزمة

- `TITAN_PULSE_FIXED.html` — النسخة المصححة والمهيأة للـ WebView.
- `TitanPulseBridge.java` — كلاس Java للجسر الأصلي.

## إعداد Sketchware Pro

اختَر أحدث إصدار مستقر متاح من Sketchware Pro. وقت إعداد هذه الحزمة، المستودع الرسمي يعرض `v7.0.0` كـ Latest / Stable.

### 1) WebView

أضف WebView واحداً يغطي الشاشة، ثم في حدث `onCreate` / التهيئة أضف الإعدادات التالية:

```java
android.webkit.WebSettings s = webview1.getSettings();
s.setJavaScriptEnabled(true);
s.setDomStorageEnabled(true);
s.setDatabaseEnabled(true);
s.setAllowFileAccess(true);
s.setAllowContentAccess(true);
s.setMediaPlaybackRequiresUserGesture(false);
s.setLoadWithOverviewMode(true);
s.setUseWideViewPort(true);

webview1.setBackgroundColor(android.graphics.Color.TRANSPARENT);
webview1.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
webview1.setWebChromeClient(new android.webkit.WebChromeClient());
webview1.addJavascriptInterface(new com.titanpulse.app.TitanPulseBridge(this), "AndroidBridge");
webview1.loadUrl("file:///android_asset/TITAN_PULSE_FIXED.html");
```

### 2) ملف HTML

ضع `TITAN_PULSE_FIXED.html` داخل مجلد assets الخاص بالمشروع.

### 3) كلاس الجسر

أضف `TitanPulseBridge.java` كـ Java Class باسم الحزمة المطابق لمشروعك، ثم غيّر أول سطر:

```java
package com.titanpulse.app;
```

إلى package مشروعك الحقيقي إن كان مختلفاً.

### 4) إذن الإشعارات

لأجهزة Android 13+ أضف إذن:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

وعند فتح التطبيق يستدعي الجسر طلب الإذن من النظام.

### 5) النتيجة

- عند نجاح طلب البناء، الواجهة تستدعي `AndroidBridge.notify(...)` فيظهر إشعار Android حقيقي عندما يكون الإذن متاحاً.
- إذا لم يوجد الجسر (مثلاً فتحت HTML في متصفح عادي)، يبقى نظام Web Notification / Toast يعمل كبديل.
- عند إغلاق التطبيق وإعادة فتحه، تُستعاد الجلسة من localStorage + IndexedDB، كما يمكن للجسر الأصلي حفظ نسخة في SharedPreferences.

## مهم جداً بخصوص "أخرج من التطبيق ويكمل الطلب"

النسخة الأصلية تنفذ خط إنتاج الذكاء الاصطناعي داخل JavaScript في WebView. إذا تم إنهاء عملية Android بالكامل، لا يمكن ضمان استمرار هذا التنفيذ من JavaScript وحده. الجسر الموجود هنا يحل الإشعارات والحفظ، أما الاستمرار الحقيقي للبناء بعد قتل العملية فيحتاج نقل عملية البناء نفسها إلى Worker/Foreground Service أو إلى Backend.

لا أعتبر ذلك "منجزاً بالكامل" داخل ملف HTML وحده، لذلك لم أضع ادعاءً مضللاً في المشروع.

## اختبار سريع قبل إخراج APK

1. ثبّت HTML داخل WebView.
2. افتح التطبيق وتحقق أن شاشة البداية لا تعرض المربع الرمادي/زر التشغيل.
3. انتقل إلى "إنشاء" ثم اكتب أي نص.
4. أغلق التطبيق وافتحه: يجب أن تعود لنفس القسم ومساحة العمل، ويُستعاد المشروع المحفوظ.
5. اسمح بالإشعارات، ثم نفّذ بناءاً كاملاً؛ يفترض أن يصل الإشعار عند اكتماله.

