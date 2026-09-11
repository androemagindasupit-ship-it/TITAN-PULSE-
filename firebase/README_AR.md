# Firebase setup

المشروع يستخدم Firebase Auth + Firestore. ملف `firebase_config.json` يحتوي معرفات عميلة وليست أسراراً، لكنه يجب أن يشير إلى مشروع يملكه صاحب التطبيق.

فعّل:
- Authentication → Email/Password
- Cloud Firestore
- طبّق `firestore.rules`

لا تضع مفاتيح AI أو كلمات المرور داخل Firebase config أو Git. مفاتيح AI تحفظ في Android Keystore داخل التطبيق.
