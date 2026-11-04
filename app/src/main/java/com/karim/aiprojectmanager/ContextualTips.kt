package com.karim.aiprojectmanager

/**
 * Static, offline help text for existing screens.
 * Informational only: never reads or changes projects, drafts, or task status.
 */
internal fun contextualTip(section: String): String = when (section) {
    "new_project" -> "أدخل اسمًا واضحًا للمشروع ووصفًا مختصرًا له. بعد الإنشاء، يمكنك كتابة الخطة في Blueprint."
    "blueprint" -> "إرشاد: سجّل هدف المشروع ومتطلباته والقيود الأساسية هنا. احفظ التغييرات قبل المغادرة."
    "roadmap" -> "إرشاد: قسّم العمل إلى مراحل صغيرة وحدد نتيجة واضحة لكل مرحلة."
    "tasks" -> "إرشاد: أضف مهام قابلة للتنفيذ. الحالة VERIFIED تُسجَّل يدويًا ولا تعني تحققًا آليًا."
    else -> ""
}
