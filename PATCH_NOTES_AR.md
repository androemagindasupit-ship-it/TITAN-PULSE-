# TITAN PULSE — Final Patch Notes

تم تطبيق إصلاحات النسخة الحالية على المصدر نفسه:

1. ترقية Room إلى schema v7.
2. تحويل Projects وJobs إلى مفاتيح مركبة مرتبطة بالحساب `ownerUserId/userId` لمنع التصادم بين الحسابات.
3. تحويل Settings وSync Metadata إلى مفاتيح مركبة مرتبطة بالمالك.
4. إضافة migration انتقالية 6→7 تعيد بناء الجداول بدون destructive downgrade.
5. إصلاح مرجع DAO المفقود `deleteProject(owner,id)`.
6. جعل Worker يستقبل `ownerUserId` ويتحقق من الحساب قبل استرجاع المهمة، مع unique work مرتبط بالمالك.
7. إيقاف وإعادة جدولة المهام النشطة عند نقل بيانات الجهاز إلى Firebase UID.
8. إيقاف المهام النشطة عند تسجيل الخروج حتى لا يستمر تنفيذ AI باسم الحساب السابق.
9. إضافة معالجة `onStopped()` لإعادة المهمة المتوقفة للنظام إلى `RETRYING` وحفظ الحالة.
10. إزالة Firebase `Tasks.await()` من عمليات المصادقة التي تستدعيها JavaScript؛ أصبحت login/register/reset/verification/delete مبنية على Firebase Tasks غير المتزامنة.
11. الحفاظ على عمليات مزامنة Worker/IO خارج UI thread.
12. توسيع تحقق WebView المحلي للسماح بـ localhost عبر http/https فقط، مع استمرار منع التنقل الخارجي غير المصرح به داخل WebView.
13. توحيد `www/index.html` مع نسخة Android assets.
14. إضافة فحوصات static إضافية لعزل الحسابات وواجهات DAO والـBridge.

## تحقق هذه النسخة

- Web/JavaScript checks: PASS
- Production static checks: PASS
- Android XML parsing: PASS
- JSON parsing: PASS
- Room 6→7 SQL migration simulation: PASS
- Web asset synchronization: PASS
- ZIP integrity: PASS

لم يتم تزوير نتيجة `assembleRelease` أو `bundleRelease`: البناء الفعلي يحتاج Android SDK وGradle distribution متاحين في بيئة البناء.
