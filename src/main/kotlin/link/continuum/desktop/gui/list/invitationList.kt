package link.continuum.desktop.gui.list

import javafx.geometry.Pos
import javafx.geometry.VPos
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import koma.gui.element.icon.placeholder.generator.hashStringColorDark
import koma.matrix.room.naming.RoomId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import link.continuum.desktop.events.InviteData
import link.continuum.desktop.gui.icon.avatar.UrlAvatar
import link.continuum.desktop.gui.icon.avatar.downloadImageResized
import link.continuum.desktop.util.http.mapMxc
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import tornadofx.*

private val logger = KotlinLogging.logger {}

@ExperimentalCoroutinesApi
class InvitationsView(
        private val client: OkHttpClient,
        private val scaling: Double = 1.0
) {
    val list = VBox(3.0)
    @Deprecated("find the right way to avoid dupliicates when a full sync is performed")
    private val added = mutableSetOf<RoomId>()
    private val spareCells = mutableListOf<InvitationCell>()

    init {
        list.isFocusTraversable = false
    }

    fun add(invite: InviteData, server: HttpUrl) {
        if (added.contains(invite.id)) {
            logger.warn { "ignoring duplicate invite $invite" }
            return
        } else {
            added.add(invite.id)
        }
        val c = if (spareCells.isNotEmpty()) {
            spareCells.removeAt(0)
        } else{
            InvitationCell(scaling, client)
        }
        c.update(invite,
                server = server)
        list.add(c.cell)
    }

    @ExperimentalCoroutinesApi
    private inner class InvitationCell(
            private val scaling: Double = 1.0,
            client: OkHttpClient
    ) {
        private val inviterAvatarSize = scaling * 12.0
        private val roomAvatarSize = scaling * 32.0

        private val inviter = Label()
        private val inviterAvatar = UrlAvatar(client, inviterAvatarSize)
        private val roomAvatar = UrlAvatar(client, roomAvatarSize)
        private val roomLabel = Text()
        private var roomId: RoomId? = null


        val cell = HBox(3.0*scaling).apply {
            minWidth = 1.0
            prefWidth = 1.0
            add(roomAvatar.root)
            vbox {
                textflow {
                    add(inviterAvatar.root)
                    text(" ")
                    add(inviter)
                    text(" invited you to room ")
                    add(roomLabel)
                }
                hbox(2.0*scaling) {
                    alignment = Pos.CENTER_RIGHT
                    hgrow = Priority.ALWAYS
                    button("Join").action {
                        logger.debug { "Accepting invitation to $roomId" }
                        remove()
                    }
                    button("Ignore").action {
                        logger.debug { "Ignoring invitation to $roomId" }
                        remove()
                    }
                }
            }
        }

        fun update(
                invitation: InviteData,
                server: HttpUrl
        ) {
            roomId = invitation.id
            inviter.text = invitation.inviterName

            inviterAvatar.updateName(
                    invitation.inviterName?:"   ",
                    hashStringColorDark(invitation.inviterId?.str?:""))
            inviterAvatar.updateUrl(
                    invitation.inviterAvatar?.let { mapMxc(it, server) }
            )

            roomLabel.text = invitation.roomDisplayName

            roomAvatar.updateName(invitation.roomDisplayName?:"   ",
                    hashStringColorDark(invitation.id.str))
            roomAvatar.updateUrl(
                    invitation.roomAvatar?.let { mapMxc(it, server) }
            )
        }

        fun remove() {
            list.children.remove(this.cell)
            spareCells.add(this)
        }
    }
}