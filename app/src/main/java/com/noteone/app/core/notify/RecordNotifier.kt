// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noteone.app.R
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.core.domain.TimeRanges
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账成功后的「今天第几笔」提醒。
 *
 * ## 体验对齐
 * 走系统通知渠道 + `IMPORTANCE_HIGH`，落下来就是微信消息那种**横幅 + 提示音**：
 * 不打断当前应用，从顶部滑下，点一下回到本应用。
 *
 * ## 为什么同一天只有一条
 * 微信里同一个会话连发多条消息，通知栏也只留**一条**合并通知。这里照做：
 * 同一天固定用 [NOTIFICATION_ID]，后记的一笔直接覆盖前一条。
 * 否则连记三笔会在通知栏堆三条「今天第 1/2/3 笔」，全是过期信息。
 *
 * ## 序号口径
 * 「今天第几笔」= 该笔 `occurredAt` 所在自然日、**同一账本**内未删除记录的条数
 * （含刚写入的这一笔）。跨账本不累计——序号是相对于当前账本看的。
 */
interface RecordNotifier {

    /** 创建通知渠道（幂等）。Application 启动时调用一次。 */
    fun ensureChannel()

    /** 现在是否真的能发出通知（系统总开关 + Android 13 的运行时权限）。 */
    fun canPost(): Boolean

    /**
     * 记账成功后调用。无权限 / 用户关掉通知时静默返回，**绝不抛异常**。
     *
     * @param occurredAt 该笔记录的业务时间，用来定位自然日
     */
    suspend fun notifySaved(
        bookId: Long,
        cents: Long,
        direction: Int,
        categoryName: String?,
        occurredAt: Long,
    )

    companion object {
        /**
         * 通知权限名。
         *
         * 刻意写成字符串而不是 `Manifest.permission.POST_NOTIFICATIONS`：
         * 后者是 API 33 才有的字段，minSdk 30 下 lint 的 `InlinedApi` 会在**每一处**
         * 引用上报「Field requires API level 33」，哪怕外层已经判过版本。
         * 字符串与字段值完全等价，且天然不受版本检查限制。
         */
        const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}

@Singleton
class AndroidRecordNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : RecordNotifier {

    override fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notify_channel_desc)
            enableVibration(true)
            // 记账不是「社交消息」，不必让桌面图标挂角标
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        // 用 if 包住而不是提前 return：lint 的 InlinedApi 只认这种形式，
        // 否则会把 API 33 才存在的权限常量当成低版本可用。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                RecordNotifier.PERMISSION_POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @SuppressLint("MissingPermission")
    override suspend fun notifySaved(
        bookId: Long,
        cents: Long,
        direction: Int,
        categoryName: String?,
        occurredAt: Long,
    ) {
        if (!canPost()) return
        // 设置页关掉开关后连库都不用查
        if (!settingsRepository.recordNoticeEnabled.first()) return

        val date = TimeRanges.toLocalDate(occurredAt)
        val todayCount = transactions.countActiveInRange(
            bookId = bookId,
            startMs = TimeRanges.startOfDay(date),
            endMs = TimeRanges.endOfDay(date),
        )
        // 刚插入的那条一定在区间里；查不到说明已被并发撤销，没有提醒的意义
        if (todayCount <= 0) return

        val label = context.getString(
            if (direction == Direction.Income) R.string.common_income else R.string.common_expense,
        )
        val amount = "¥" + Money.formatThousands(cents)
        val body = if (categoryName.isNullOrBlank()) {
            context.getString(R.string.notify_body_plain, label, amount)
        } else {
            context.getString(R.string.notify_body_with_category, label, amount, categoryName)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_ledger_line)
            .setContentTitle(context.getString(R.string.notify_title_today, todayCount))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(occurredAt)
            .setShowWhen(true)
            // 与微信消息同类：横幅、可滑动清除、点击回应用
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * 点击通知回到应用。
     *
     * 不用 `Intent(context, MainActivity::class.java)`：`core` 层不该认识 UI 的 Activity，
     * 用启动 Intent 让系统自己找入口 Activity。
     */
    private fun contentIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                setPackage(context.packageName)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** 渠道 id。一经发布不要改，改了等于用户的通知设置被重置。 */
        const val CHANNEL_ID = "record_saved"

        /** 同一天共用这一个 id → 后一笔覆盖前一笔。 */
        private const val NOTIFICATION_ID = 1001
    }
}

/** 把接口绑到唯一的实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {

    @Binds
    abstract fun bindRecordNotifier(impl: AndroidRecordNotifier): RecordNotifier
}
