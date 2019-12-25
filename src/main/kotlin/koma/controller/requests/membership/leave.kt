package koma.controller.requests.membership

import koma.HttpFailure
import koma.gui.view.window.auth.uilaunch
import koma.koma_app.AppStore
import koma.koma_app.appState
import koma.util.failureOrThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.continuum.desktop.Room
import link.continuum.desktop.gui.JFX
import link.continuum.desktop.gui.UiDispatcher
import mu.KotlinLogging
import org.controlsfx.control.Notifications

private val logger = KotlinLogging.logger {}

@ExperimentalCoroutinesApi
fun leaveRoom(mxroom: Room, appData: AppStore = appState.store) {
    val roomId = mxroom.id
    logger.debug { "Leaving $roomId" }
    val api = appState.apiClient
    api ?: return
    GlobalScope.launch {
        val roomname = mxroom.displayName()
        val result = api.leavingRoom(roomId)
        when {
            result.isSuccess -> {
                logger.debug { "Left $roomname successfully" }
                withContext(UiDispatcher) {
                    appData.joinedRoom.removeById(roomId)
                }
            }
            result.isFailure -> {
                val ex = result.failureOrThrow()
                uilaunch {
                    Notifications.create()
                            .title("Had error leaving room $roomname")
                            .text("$ex")
                            .owner(JFX.primaryStage)
                            .showWarning()
                    if ((ex is HttpFailure && ex.http_code == 404)) {
                        logger.debug { "leaving room although there is exception $ex" }
                        appData.joinedRoom.removeById(roomId)
                    }
                }
            }
        }
    }
}
