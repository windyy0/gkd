package com.nnnn.myg.debug

import android.app.Service
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.nnnn.myg.app
import com.nnnn.myg.appScope
import com.nnnn.myg.data.AppInfo
import com.nnnn.myg.data.DeviceInfo
import com.nnnn.myg.data.GkdAction
import com.nnnn.myg.data.RawSubscription
import com.nnnn.myg.data.RpcError
import com.nnnn.myg.data.SubsItem
import com.nnnn.myg.data.deleteSubscription
import com.nnnn.myg.data.selfAppInfo
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.debug.SnapshotExt.captureSnapshot
import com.nnnn.myg.notif.httpNotif
import com.nnnn.myg.notif.notifyService
import com.nnnn.myg.permission.notificationState
import com.nnnn.myg.service.A11yService
import com.nnnn.myg.util.LOCAL_HTTP_SUBS_ID
import com.nnnn.myg.util.SERVER_SCRIPT_URL
import com.nnnn.myg.util.getIpAddressInLocalNetwork
import com.nnnn.myg.util.keepNullJson
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.map
import com.nnnn.myg.util.storeFlow
import com.nnnn.myg.util.subsItemsFlow
import com.nnnn.myg.util.toast
import com.nnnn.myg.util.updateSubscription
import java.io.File


class HttpService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var server: CIOApplicationEngine? = null
    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        localNetworkIpsFlow.value = getIpAddressInLocalNetwork()
        val httpServerPortFlow = storeFlow.map(scope) { s -> s.httpServerPort }
        scope.launchTry(Dispatchers.IO) {
            httpServerPortFlow.collect { port ->
                server?.stop()
                server = try {
                    createServer(port).apply { start() }
                } catch (e: Exception) {
                    LogUtils.d("HTTP服务启动失败", e)
                    null
                }
                if (server == null) {
                    toast("HTTP服务启动失败,您可以尝试切换端口后重新启动")
                    stopSelf()
                    return@collect
                }
                httpNotif.copy(text = "HTTP服务-$port").notifyService(this@HttpService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        localNetworkIpsFlow.value = emptyList()

        scope.launchTry(Dispatchers.IO) {
            server?.stop()
            if (storeFlow.value.autoClearMemorySubs) {
                deleteSubscription(LOCAL_HTTP_SUBS_ID)
            }
            delay(3000)
            scope.cancel()
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        val localNetworkIpsFlow = MutableStateFlow(emptyList<String>())
        fun stop() {
            app.stopService(Intent(app, HttpService::class.java))
        }

        fun start() {
            if (!notificationState.checkOrToast()) return
            app.startForegroundService(Intent(app, HttpService::class.java))
        }
    }

    override fun onBind(intent: Intent?) = null
}

@Serializable
data class RpcOk(
    val message: String? = null,
)

@Serializable
data class ReqId(
    val id: Long,
)

@Serializable
data class ServerInfo(
    val device: DeviceInfo = DeviceInfo.instance,
    val gkdAppInfo: AppInfo = selfAppInfo
)

fun clearHttpSubs() {
    // 如果 app 被直接在任务列表划掉, HTTP订阅会没有清除, 所以在后续的第一次启动时清除
    if (HttpService.isRunning.value) return
    appScope.launchTry(Dispatchers.IO) {
        delay(1000)
        if (storeFlow.value.autoClearMemorySubs) {
            deleteSubscription(LOCAL_HTTP_SUBS_ID)
        }
    }
}

private val httpSubsItem by lazy {
    SubsItem(
        id = LOCAL_HTTP_SUBS_ID,
        order = -1,
        enableUpdate = false,
    )
}

private fun createServer(port: Int): CIOApplicationEngine {
    return embeddedServer(CIO, port) {
        install(KtorCorsPlugin)
        install(KtorErrorPlugin)
        install(ContentNegotiation) { json(keepNullJson) }
        routing {
            get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$SERVER_SCRIPT_URL'></script>" } }
            route("/api") {
                // Deprecated
                get("/device") { call.respond(DeviceInfo.instance) }

                post("/getServerInfo") { call.respond(ServerInfo()) }

                // Deprecated
                get("/snapshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    val fp = File(SnapshotExt.getSnapshotPath(id))
                    if (!fp.exists()) {
                        throw RpcError("对应快照不存在")
                    }
                    call.respondFile(fp)
                }
                post("/getSnapshot") {
                    val data = call.receive<ReqId>()
                    val fp = File(SnapshotExt.getSnapshotPath(data.id))
                    if (!fp.exists()) {
                        throw RpcError("对应快照不存在")
                    }
                    call.respond(fp)
                }

                // Deprecated
                get("/screenshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    val fp = File(SnapshotExt.getScreenshotPath(id))
                    if (!fp.exists()) {
                        throw RpcError("对应截图不存在")
                    }
                    call.respondFile(fp)
                }
                post("/getScreenshot") {
                    val data = call.receive<ReqId>()
                    val fp = File(SnapshotExt.getScreenshotPath(data.id))
                    if (!fp.exists()) {
                        throw RpcError("对应截图不存在")
                    }
                    call.respondFile(fp)
                }

                // Deprecated
                get("/captureSnapshot") {
                    call.respond(captureSnapshot())
                }
                post("/captureSnapshot") {
                    call.respond(captureSnapshot())
                }

                // Deprecated
                get("/snapshots") {
                    call.respond(DbSet.snapshotDao.query().first())
                }
                post("/getSnapshots") {
                    call.respond(DbSet.snapshotDao.query().first())
                }

                post("/updateSubscription") {
                    val subscription =
                        RawSubscription.parse(call.receiveText(), json5 = false)
                            .copy(
                                id = LOCAL_HTTP_SUBS_ID,
                                name = "内存订阅",
                                version = 0,
                                author = "@myg-kit/inspect"
                            )
                    updateSubscription(subscription)
                    DbSet.subsItemDao.insert((subsItemsFlow.value.find { s -> s.id == httpSubsItem.id }
                        ?: httpSubsItem).copy(mtime = System.currentTimeMillis()))
                    call.respond(RpcOk())
                }
                post("/execSelector") {
                    if (!A11yService.isRunning.value) {
                        throw RpcError("无障碍没有运行")
                    }
                    val gkdAction = call.receive<GkdAction>()
                    call.respond(A11yService.execAction(gkdAction))
                }
            }
        }
    }
}