package com.opengate.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * فئة مساعدة للتكامل مع خدمات متجر Google Play الرسمية (Google Play Services)
 * - تقييم التطبيق من داخل التطبيق (In-App Review API) وفق سياسات قوقل بدون أي إجبار
 * - فحص التحديثات الفورية (In-App Update API)
 * - فتح صفحة التطبيق الرسمية في متجر Google Play
 */
object GooglePlayServicesHelper {

    /**
     * طلب تقييم التطبيق رسمياً عبر نافذة Google Play In-App Review
     * متوافق 100% مع سياسات Google Play
     */
    fun launchInAppReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // اكتمل تدفق التقييم بنجاح
                }
            } else {
                // إذا لم تتوفر خدمات قوقل بلي على الجهاز، يتم فتح صفحة المتجر مباشرة
                openPlayStorePage(activity)
            }
        }
    }

    /**
     * فتح صفحة التطبيق في متجر Google Play
     */
    fun openPlayStorePage(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * فحص توفر تحديثات جديدة عبر Google Play In-App Update API
     */
    fun checkForAppUpdate(activity: Activity, onUpdateChecked: (Boolean) -> Unit = {}) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.FLEXIBLE,
                        activity,
                        9901
                    )
                }
                onUpdateChecked(true)
            } else {
                onUpdateChecked(false)
            }
        }.addOnFailureListener {
            onUpdateChecked(false)
        }
    }
}
