package com.family.health

import android.app.Application
import android.content.Context
import com.family.health.data.db.AppDatabase
import com.family.health.data.repo.Repository
import com.family.health.data.session.SessionStore
import com.family.health.data.sync.SyncEngine
import com.family.health.notif.NotificationHelper
import com.family.health.notif.ReminderScheduler
import com.family.health.syncclient.HttpSyncClient

/** 依赖容器（手工装配，规模可控，不引 DI 框架） */
class AppContainer(context: Context) {
    val session = SessionStore(context)
    val db = AppDatabase.get(context)
    val client = HttpSyncClient()
    val engine = SyncEngine(db, session, client)
    val repo = Repository(db, session, engine, client)
    val reminderScheduler = ReminderScheduler(context)

    init {
        NotificationHelper.ensureChannel(context)
        reminderScheduler.rescheduleAll() // 冷启动重排本机提醒
        engine.kickPull() // 冷启动：重放 outbox + 增量下拉
    }
}

class FamilyHealthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
