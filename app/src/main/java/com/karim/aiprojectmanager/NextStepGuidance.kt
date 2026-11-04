package com.karim.aiprojectmanager

/**
 * Offline, read-only guidance derived only from the selected project's saved data.
 * A suggestion is not independent verification of task progress.
 */
internal fun nextStepGuidance(project: ProjectItem): String {
    if (project.blueprint.isBlank()) return "ابدأ بكتابة Blueprint للمشروع."
    if (project.roadmap.isBlank()) return "حدد مراحل العمل في Roadmap."
    if (project.tasks.isEmpty()) return "أضف أول مهمة قابلة للتنفيذ."
    if (project.tasks.any { it.status == "IN_PROGRESS" }) return "راجع المهمة قيد التنفيذ وحدد ما اكتمل منها."
    if (project.tasks.any { it.status == "IMPLEMENTED_UNVERIFIED" }) return "راجع أدلة واختبارات المهام المنفذة قبل اعتمادها."
    if (project.tasks.any { it.status != "VERIFIED" }) return "اختر مهمة لم تبدأ بعد وحدد خطوتها العملية."
    return "راجع أدلة تحقق المهام وحدد ما إذا كان المشروع يحتاج أعمالًا إضافية."
}
