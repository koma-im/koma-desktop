package link.continuum.desktop.gui

import javafx.application.Application
import javafx.beans.Observable
import javafx.beans.binding.*
import javafx.beans.property.Property
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.stage.Stage
import javafx.stage.Window
import koma.util.given
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.selects.select
import link.continuum.desktop.gui.scene.ScalingPane
import link.continuum.desktop.util.None
import link.continuum.desktop.util.Option
import mu.KotlinLogging
import java.util.concurrent.Callable

private val logger = KotlinLogging.logger {}

val whiteBackGround = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY))

object JFX {
    lateinit var primaryStage: Stage
    lateinit var application: Application
    lateinit var primaryPane: ScalingPane
    val hostServices by lazy { application.hostServices }
}

fun Pane.vbox(op: VBox.()->Unit) {
    val b = VBox()
    b.op()
    this.children.add(b)
}


fun Pane.hbox(spacing: Double? = null, op: HBox.()->Unit={}): HBox {
    val b = HBox()
    if (spacing != null) b.spacing = spacing
    b.op()
    this.children.add(b)
    return b
}

fun Pane.stackpane(op: StackPane.()->Unit={}): StackPane {
    val b = StackPane()
    b.op()
    this.children.add(b)
    return b
}

fun Pane.text(content: String?=null, op: Text.()->Unit={}) {
    this.children.add(Text(content).apply(op))
}

fun Pane.label(content: String?=null, op: Label.()->Unit={}): Label {
    val l = Label(content).apply(op)
    this.children.add(l)
    return l
}

fun ButtonBar.button(content: String?=null, op: Button.()->Unit={}) {
    this.buttons.add(Button(content).apply(op))
}

fun Pane.button(content: String?=null, op: Button.()->Unit={}): Button {
    val b = Button(content).apply(op)
    this.children.add(b)
    return b
}

fun Pane.pane(op: Pane.()->Unit={}): Pane {
    val b = Pane().apply(op)
    this.children.add(b)
    return b
}

fun Pane.add(node: Node) {
    this.children.add(node)
}

fun Control.tooltip(text: String) {
    this.tooltip = Tooltip(text)
}

fun Button.action(Action:  (ActionEvent)->Unit) {
    this.setOnAction(Action)
}

fun MenuBar.menu(text: String, op: Menu.() -> Unit) {
    val m = Menu(text).apply(op)
    this.menus.add(m)
}

fun Menu.item(text: String, op: MenuItem.() -> Unit={}): MenuItem {
    val m = MenuItem(text).apply(op)
    this.items.add(m)
    return m
}

fun ContextMenu.item(text: String, op: MenuItem.() -> Unit={}): MenuItem {
    val m = MenuItem(text).apply(op)
    this.items.add(m)
    return m
}

fun MenuItem.action(action: (ActionEvent)-> Unit) {
    this.setOnAction(action)
}

fun <T : Node> T.disableWhen(predicate: ObservableValue<Boolean>) = apply {
    disableProperty().cleanBind(predicate)
}

fun Node.style(op: StyleBuilder.()->Unit) {
    style = StyleBuilder().apply(op).toString()
}

class StyleBuilder {
    var prefWidth: SizeWithUnit? = null
    var prefHeight: SizeWithUnit? = null
    var minWidth: SizeWithUnit? = null
    var minHeight: SizeWithUnit? = null
    var maxWidth: SizeWithUnit? = null
    var maxHeight: SizeWithUnit? = null
    var fontSize: SizeWithUnit? = null
    var fontFamily: GenericFontFamily? = null
    override fun toString(): String {
        val sb = StringBuilder().apply {
            given(prefWidth) {
                append("-fx-pref-width:")
                append(prefWidth.toString())
                append(';')
            }
            given(prefHeight) {
                append("-fx-pref-height:")
                append(prefHeight.toString())
                append(';')
            }
            given(minWidth) {
                append("-fx-min-width:")
                append(minWidth.toString())
                append(';')
            }
            given(minHeight) {
                append("-fx-min-height:")
                append(minHeight.toString())
                append(';')
            }
            given(maxWidth) {
                append("-fx-max-width:")
                append(maxWidth.toString())
                append(';')
            }
            given(maxHeight) {
                append("-fx-max-height:")
                append(maxHeight.toString())
                append(';')
            }
            given(fontSize) {
                append("-fx-font-size:")
                append(fontSize.toString())
                append(';')
            }
            given(fontFamily) {
                append("-fx-font-family:")
                append(fontFamily.toString())
                append(';')
            }
        }
        return sb.toString()
    }
}

val Number.em: SizeWithUnit get() = SizeWithUnit.Em(this)

@Suppress("Unused")
enum class GenericFontFamily {
    serif, // (e.g., Times)
    sansSerif, // (e.g., Helvetica)
    cursive, // (e.g., Zapf-Chancery)
    fantasy,// (e.g., Western)
    monospace; // (e.g., Courier)

    override fun toString() =
            when(this) {
                sansSerif -> "sans-serif"
                else -> this.name
            }
}
sealed class SizeWithUnit {
    class Em(val value: Number): SizeWithUnit() {
        override fun toString() = "${value}em"
    }
}

fun <T : Node> T.enableWhen(predicate: BooleanBinding) = apply {
    disableProperty().cleanBind(predicate.not())
}

fun <T : Node> T.visibleWhen(predicate: ObservableValue<Boolean>) = apply {
    visibleProperty().cleanBind(predicate)
    managedProperty().cleanBind(predicate)
}

fun <T : Node> T.removeWhen(predicate: ObservableValue<Boolean>) = apply {
    val remove = booleanBinding(predicate) { predicate.value.not() }
    visibleProperty().cleanBind(remove)
    managedProperty().cleanBind(remove)
}

inline fun <T : Node> T.removeWhen(predicate: () ->ObservableValue<Boolean>) = removeWhen(predicate())

fun <T> Property<T>.cleanBind(observable: ObservableValue<T>) {
    unbind()
    bind(observable)
}
fun <T : Observable> doubleBinding(receiver: T, op: T.() -> Double): DoubleBinding
        = Bindings.createDoubleBinding(Callable { receiver.op() }, receiver)
fun <T : Observable> stringBinding(receiver: T, op: T.() -> String): StringBinding
        = Bindings.createStringBinding(Callable { receiver.op() }, receiver)
fun <T : Observable> stringBindingNullable(receiver: T, op: T.() -> String?): StringBinding
        = Bindings.createStringBinding(Callable { receiver.op() }, receiver)
fun <T : Observable> booleanBinding(receiver: T, op: T.() -> Boolean): BooleanBinding
        = Bindings.createBooleanBinding(Callable { receiver.op() }, receiver)

fun <T : Observable, R> objectBinding(receiver: T, op: T.() -> R): ObjectBinding<R>
        = Bindings.createObjectBinding(Callable { receiver.op() }, receiver)

fun clipboardPutString(text: String){
    Clipboard.getSystemClipboard().setContent(
            ClipboardContent().apply {
                putString(text)
            })
}

fun<T: Node> T.tooltip(text: String) {
    val t = Tooltip(text)
    Tooltip.install(this, t)
}

fun<T: Node> T.showIf(show: Boolean) {
    this.isManaged = show
    this.isVisible = show
}

object UiConstants {
    val insets5 = Insets(5.0)
}
val UiDispatcher = Dispatchers.JavaFx

fun uialert(type: Alert.AlertType,
            header: String,
            content: String? = null,
            vararg buttons: ButtonType,
            owner: Window? = null,
            title: String? = null,
            actionFn: Alert.(ButtonType) -> Unit = {}) {
    GlobalScope.launch(UiDispatcher) {
        val alert = Alert(type, content ?: "", *buttons)
        title?.let { alert.title = it }
        alert.headerText = header
        owner?.also { alert.initOwner(it) }
        val buttonClicked = alert.showAndWait()
        if (buttonClicked.isPresent) {
            alert.actionFn(buttonClicked.get())
        }
    }
}

/**
 * convert a channel of unordered updates with timestamps
 * to one that provides the latest value
 */
@ExperimentalCoroutinesApi
class UpdateConflater<T> {
    private val inputs = Channel<Pair<Long, T>>()
    private val updates = ConflatedBroadcastChannel<T>()

    suspend fun update(time: Long, value: T) {
        inputs.send(time to value)
    }
    fun subscribe(): ReceiveChannel<T> {
        return updates.openSubscription()
    }

    init {
        GlobalScope.launch { processUpdates() }
    }
    private fun CoroutineScope.processUpdates() {
        var latest = 0L
        var lastValue: T? = null
        launch {
            for ((t, v) in inputs) {
                if (t >= latest) {
                    if (v != lastValue) {
                        logger.trace { "value $v updated at $t, newer than $latest" }
                        latest = t
                        lastValue = v
                        updates.send(v)
                    }
                } else {
                    logger.info { "value $v comes at $t, older than $latest" }
                }
            }
        }
    }
}

fun<T, U, C: SendChannel<U>> CoroutineScope.switchGetDeferred(
        input: ReceiveChannel<T>,
        getDeferred: (T)->Deferred<U>,
        output: C
) {
    launch {
        var current = input.receive()
        while (isActive) {
            val next = select<T?> {
                input.onReceiveOrNull { update ->
                    update
                }
                getDeferred(current).onAwait {
                    output.send(it)
                    input.receiveOrNull()
                }
            }
            if (next == null) {
                logger.debug { "no more input after $current" }
                break
            } else {
                current = next
            }
        }
        logger.debug { "closing switchMapDeferred" }
        input.cancel()
        output.close()
    }
}


fun<T: Any, U: Any, C: SendChannel<Option<U>>> CoroutineScope.switchGetDeferredOption(
        input: ReceiveChannel<Option<T>>,
        getDeferred: (T)->Deferred<Option<U>>,
        output: C
) {
    launch {
        var current = input.receive()
        while (isActive) {
            val next = select<Option<T>?> {
                input.onReceiveOrNull { it }
                current.map { getDeferred(it) }.map {
                    it.onAwait {
                        output.send(it)
                        input.receiveOrNull()
                    }
                }
            }
            if (next == null) {
                logger.debug { "no more input after $current" }
                break
            } else {
                current = next
                if (current.isEmpty)
                    output.send(None())
            }
        }
        logger.debug { "closing switchMapDeferred" }
        input.cancel()
        output.close()
    }
}

/**
 * switch channel by key
 */
@ExperimentalCoroutinesApi
fun <T, U> CoroutineScope.switchUpdates(
        input: ReceiveChannel<T>,
        getUpdates: (T)->ReceiveChannel<U>
): ReceiveChannel<U> {
    val output = Channel<U>(Channel.CONFLATED)
    launch {
        var key = input.receive()
        var updates = getUpdates(key)
        var latest: U? = null
        loop@ while (isActive) {
            val state = select<UpdateState<T>> {
                input.onReceiveOrNull { k ->
                    k?.let {
                        logger.trace { "switching to updates for $k" }
                        UpdateState.Switch<T>(it)
                    } ?: UpdateState.Close()
                }
                updates.onReceive {
                    if (it!= latest) {
                        latest = it
                        logger.trace { "got update for $key: $it" }
                        output.send(it)
                    }
                    UpdateState.Continue()
                }
            }
            when (state) {
                is UpdateState.Switch<T> -> {
                    updates.cancel()
                    key = state.key
                    updates = getUpdates(key)
                }
                is UpdateState.Close -> {
                    logger.debug { "no more updates after $key" }
                    break@loop
                }
                is UpdateState.Continue -> {
                }
            }
        }
        logger.debug { "canceling subscription" }
        input.cancel()
        output.close()
    }
    return output
}

sealed class UpdateState<T> {
    class Switch<T>(val key: T): UpdateState<T>()
    class Close<T>: UpdateState<T>()
    class Continue<T>: UpdateState<T>()
}
