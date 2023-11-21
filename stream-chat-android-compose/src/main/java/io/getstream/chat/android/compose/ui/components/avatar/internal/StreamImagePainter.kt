package io.getstream.chat.android.compose.ui.components.avatar.internal

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.times
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.NullRequestDataException
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Precision
import coil.size.Scale
import coil.transition.CrossfadeTransition
import coil.transition.TransitionTarget
import com.google.accompanist.drawablepainter.DrawablePainter
import io.getstream.chat.android.compose.ui.components.avatar.internal.StreamAsyncImagePainter.Companion.DefaultTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Return an [AsyncImagePainter] that executes an [ImageRequest] asynchronously and renders the result.
 *
 * ** This is a lower-level API than [AsyncImage] and may not work as expected in all situations. **
 *
 * - [AsyncImagePainter] will not finish loading if [AsyncImagePainter.onDraw] is not called.
 *   This can occur if a composable has an unbounded (i.e. [Constraints.Infinity]) width/height
 *   constraint. For example, to use [AsyncImagePainter] with [LazyRow] or [LazyColumn], you must
 *   set a bounded width or height respectively using `Modifier.width` or `Modifier.height`.
 * - [AsyncImagePainter.state] will not transition to [State.Success] synchronously during the
 *   composition phase. Use [SubcomposeAsyncImage] or set a custom [ImageRequest.Builder.size] value
 *   (e.g. `size(Size.ORIGINAL)`) if you need this.
 *
 * @param model Either an [ImageRequest] or the [ImageRequest.data] value.
 * @param imageLoader The [ImageLoader] that will be used to execute the request.
 * @param placeholder A [Painter] that is displayed while the image is loading.
 * @param error A [Painter] that is displayed when the image request is unsuccessful.
 * @param fallback A [Painter] that is displayed when the request's [ImageRequest.data] is null.
 * @param onLoading Called when the image request begins loading.
 * @param onSuccess Called when the image request completes successfully.
 * @param onError Called when the image request completes unsuccessfully.
 * @param contentScale Used to determine the aspect ratio scaling to be used if the canvas bounds
 *  are a different size from the intrinsic size of the image loaded by [model]. This should be set
 *  to the same value that's passed to [Image].
 * @param filterQuality Sampling algorithm applied to a bitmap when it is scaled and drawn into the
 *  destination.
 */
@Composable
public fun rememberStream2AsyncImagePainter(
    model: Any?,
    imageLoader: ImageLoader,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((StreamAsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((StreamAsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((StreamAsyncImagePainter.State.Error) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
): StreamAsyncImagePainter = rememberStream2AsyncImagePainter(
    model = model,
    imageLoader = imageLoader,
    transform = transformOf(placeholder, error, fallback),
    onState = onStateOf(onLoading, onSuccess, onError),
    contentScale = contentScale,
    filterQuality = filterQuality,
)

/**
 * Return an [AsyncImagePainter] that executes an [ImageRequest] asynchronously and renders the result.
 *
 * ** This is a lower-level API than [AsyncImage] and may not work as expected in all situations. **
 *
 * - [AsyncImagePainter] will not finish loading if [AsyncImagePainter.onDraw] is not called.
 *   This can occur if a composable has an unbounded (i.e. [Constraints.Infinity]) width/height
 *   constraint. For example, to use [AsyncImagePainter] with [LazyRow] or [LazyColumn], you must
 *   set a bounded width or height respectively using `Modifier.width` or `Modifier.height`.
 * - [AsyncImagePainter.state] will not transition to [State.Success] synchronously during the
 *   composition phase. Use [SubcomposeAsyncImage] or set a custom [ImageRequest.Builder.size] value
 *   (e.g. `size(Size.ORIGINAL)`) if you need this.
 *
 * @param model Either an [ImageRequest] or the [ImageRequest.data] value.
 * @param imageLoader The [ImageLoader] that will be used to execute the request.
 * @param transform A callback to transform a new [State] before it's applied to the
 *  [AsyncImagePainter]. Typically this is used to overwrite the state's [Painter].
 * @param onState Called when the state of this painter changes.
 * @param contentScale Used to determine the aspect ratio scaling to be used if the canvas bounds
 *  are a different size from the intrinsic size of the image loaded by [model]. This should be set
 *  to the same value that's passed to [Image].
 * @param filterQuality Sampling algorithm applied to a bitmap when it is scaled and drawn into the
 *  destination.
 */
@Composable
public fun rememberStream2AsyncImagePainter(
    model: Any?,
    imageLoader: ImageLoader,
    transform: (StreamAsyncImagePainter.State) -> StreamAsyncImagePainter.State = DefaultTransform,
    onState: ((StreamAsyncImagePainter.State) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
): StreamAsyncImagePainter {
    val request = requestOf(model)
    validateRequest(request)

    val painter = remember { StreamAsyncImagePainter(request, imageLoader) }
    painter.transform = transform
    painter.onState = onState
    painter.contentScale = contentScale
    painter.filterQuality = filterQuality
    painter.isPreview = LocalInspectionMode.current
    painter.imageLoader = imageLoader
    painter.request = request // Update request last so all other properties are up to date.
    painter.onRemembered() // Invoke this manually so `painter.state` is set to `Loading` immediately.
    return painter
}

/**
 * A [Painter] that that executes an [ImageRequest] asynchronously and renders the result.
 */
@Stable
public class StreamAsyncImagePainter internal constructor(
    public var request: ImageRequest,
    public var imageLoader: ImageLoader
) : Painter(), RememberObserver {

    private var rememberScope: CoroutineScope? = null
    private val drawSize = MutableStateFlow(Size.Zero)

    private var painter: Painter? by mutableStateOf(null)
    private var alpha: Float by mutableStateOf(DefaultAlpha)
    private var colorFilter: ColorFilter? by mutableStateOf(null)

    // These fields allow access to the current valueÏT
    // instead of the value in the current composition.
    private var _state: State = State.Empty
        set(value) {
            field = value
            state = value
        }
    private var _painter: Painter? = null
        set(value) {
            field = value
            painter = value
        }

    internal var transform = DefaultTransform
    internal var onState: ((State) -> Unit)? = null
    internal var contentScale = ContentScale.Fit
    internal var filterQuality = DrawScope.DefaultFilterQuality
    internal var isPreview = false

    /** The current [AsyncImagePainter.State]. */
    internal var state: State by mutableStateOf(State.Empty)
        private set

    override val intrinsicSize: Size
        get() = painter?.intrinsicSize ?: Size.Unspecified

    override fun DrawScope.onDraw() {
        // Update the draw scope's current size.
        drawSize.value = size

        // Draw the current painter.
        painter?.apply { draw(size, alpha, colorFilter) }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onRemembered() {
        // Short circuit if we're already remembered.
        if (rememberScope != null) return

        // Create a new scope to observe state and execute requests while we're remembered.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        rememberScope = scope

        // Manually notify the child painter that we're remembered.
        (_painter as? RememberObserver)?.onRemembered()

        // If we're in inspection mode skip the image request and set the state to loading.
        if (isPreview) {
            val request = request.newBuilder().defaults(imageLoader.defaults).build()
            updateState(State.Loading(request.placeholder?.toPainter()))
            return
        }

        // Observe the current request and execute any emissions.
        scope.launch {
            val state = imageLoader.execute(updateRequest(request)).toState()
            updateState(state)
        }
    }

    override fun onForgotten() {
        clear()
        (_painter as? RememberObserver)?.onForgotten()
    }

    override fun onAbandoned() {
        clear()
        (_painter as? RememberObserver)?.onAbandoned()
    }

    private fun clear() {
        rememberScope?.cancel()
        rememberScope = null
    }

    /** Update the [request] to work with [AsyncImagePainter]. */
    private fun updateRequest(request: ImageRequest): ImageRequest {
        return request.newBuilder()
            .target(onStart = { placeholder ->
                updateState(State.Loading(placeholder?.toPainter()))
            })
            .apply {
                if (request.defined.sizeResolver == null) {
                    // If no other size resolver is set, suspend until the canvas size is positive.
                    size { drawSize.mapNotNull { it.toSizeOrNull() }.first() }
                }
                if (request.defined.scale == null) {
                    // If no other scale resolver is set, use the content scale.
                    scale(contentScale.toScale())
                }
                if (request.defined.precision != Precision.EXACT) {
                    // AsyncImagePainter scales the image to fit the canvas size at draw time.
                    precision(Precision.INEXACT)
                }
            }
            .build()
    }

    private fun updateState(input: State) {
        val previous = _state
        val current = transform(input)
        _state = current
        _painter = maybeNewCrossfadePainter(previous, current) ?: current.painter

        // Manually forget and remember the old/new painters if we're already remembered.
        if (rememberScope != null && previous.painter !== current.painter) {
            (previous.painter as? RememberObserver)?.onForgotten()
            (current.painter as? RememberObserver)?.onRemembered()
        }

        // Notify the state listener.
        onState?.invoke(current)
    }

    /** Create and return a [CrossfadePainter] if requested. */
    private fun maybeNewCrossfadePainter(previous: State, current: State): CrossfadePainter? {
        // We can only invoke the transition factory if the state is success or error.
        val result = when (current) {
            is State.Success -> current.result
            is State.Error -> current.result
            else -> return null
        }

        // Invoke the transition factory and wrap the painter in a `CrossfadePainter` if it returns
        // a `CrossfadeTransformation`.
        val transition = result.request.transitionFactory.create(FakeTransitionTarget, result)
        if (transition is CrossfadeTransition) {
            return CrossfadePainter(
                start = previous.painter.takeIf { previous is State.Loading },
                end = current.painter,
                contentScale = contentScale,
                durationMillis = transition.durationMillis,
                fadeStart = result !is SuccessResult || !result.isPlaceholderCached,
                preferExactIntrinsicSize = transition.preferExactIntrinsicSize
            )
        } else {
            return null
        }
    }

    private fun ImageResult.toState() = when (this) {
        is SuccessResult -> State.Success(drawable.toPainter(), this)
        is ErrorResult -> State.Error(drawable?.toPainter(), this)
    }

    /** Convert this [Drawable] into a [Painter] using Compose primitives if possible. */
    private fun Drawable.toPainter() = when (this) {
        is BitmapDrawable -> BitmapPainter(bitmap.asImageBitmap(), filterQuality = filterQuality)
        else -> DrawablePainter(mutate())
    }

    /**
     * The current state of the [AsyncImagePainter].
     */
    public sealed class State {

        /** The current painter being drawn by [AsyncImagePainter]. */
        public abstract val painter: Painter?

        /** The request has not been started. */
        public object Empty : State() {
            override val painter: Painter? get() = null
        }

        /** The request is in-progress. */
        public data class Loading(
            override val painter: Painter?,
        ) : State()

        /** The request was successful. */
        public data class Success(
            override val painter: Painter,
            val result: SuccessResult,
        ) : State()

        /** The request failed due to [ErrorResult.throwable]. */
        public data class Error(
            override val painter: Painter?,
            val result: ErrorResult,
        ) : State()
    }

    internal companion object {
        /**
         * A state transform that does not modify the state.
         */
        val DefaultTransform: (State) -> State = { it }
    }
}

/**
 * A [Painter] that crossfades from [start] to [end].
 *
 * NOTE: The animation can only be executed once as the [start] drawable is dereferenced at
 * the end of the transition.
 */
@Stable
internal class CrossfadePainter(
    private var start: Painter?,
    private val end: Painter?,
    private val contentScale: ContentScale,
    private val durationMillis: Int,
    private val fadeStart: Boolean,
    private val preferExactIntrinsicSize: Boolean,
) : Painter() {

    private var invalidateTick by mutableStateOf(0)
    private var startTimeMillis = -1L
    private var isDone = false

    private var maxAlpha: Float by mutableStateOf(DefaultAlpha)
    private var colorFilter: ColorFilter? by mutableStateOf(null)

    override val intrinsicSize get() = computeIntrinsicSize()

    override fun DrawScope.onDraw() {
        if (isDone) {
            drawPainter(end, maxAlpha)
            return
        }

        // Initialize startTimeMillis the first time we're drawn.
        val uptimeMillis = SystemClock.uptimeMillis()
        if (startTimeMillis == -1L) {
            startTimeMillis = uptimeMillis
        }

        val percent = (uptimeMillis - startTimeMillis) / durationMillis.toFloat()
        val endAlpha = percent.coerceIn(0f, 1f) * maxAlpha
        val startAlpha = if (fadeStart) maxAlpha - endAlpha else maxAlpha
        isDone = percent >= 1f

        drawPainter(start, startAlpha)
        drawPainter(end, endAlpha)

        if (isDone) {
            start = null
        } else {
            // Increment this value to force the painter to be redrawn.
            invalidateTick++
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.maxAlpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    private fun computeIntrinsicSize(): Size {
        val startSize = start?.intrinsicSize ?: Size.Zero
        val endSize = end?.intrinsicSize ?: Size.Zero

        val isStartSpecified = startSize.isSpecified
        val isEndSpecified = endSize.isSpecified
        if (isStartSpecified && isEndSpecified) {
            return Size(
                width = maxOf(startSize.width, endSize.width),
                height = maxOf(startSize.height, endSize.height),
            )
        }
        if (preferExactIntrinsicSize) {
            if (isStartSpecified) return startSize
            if (isEndSpecified) return endSize
        }
        return Size.Unspecified
    }

    private fun DrawScope.drawPainter(painter: Painter?, alpha: Float) {
        if (painter == null || alpha <= 0) return

        with(painter) {
            val size = size
            val drawSize = computeDrawSize(intrinsicSize, size)

            if (size.isUnspecified || size.isEmpty()) {
                draw(drawSize, alpha, colorFilter)
            } else {
                inset(
                    horizontal = (size.width - drawSize.width) / 2,
                    vertical = (size.height - drawSize.height) / 2
                ) {
                    draw(drawSize, alpha, colorFilter)
                }
            }
        }
    }

    private fun computeDrawSize(srcSize: Size, dstSize: Size): Size {
        if (srcSize.isUnspecified || srcSize.isEmpty()) return dstSize
        if (dstSize.isUnspecified || dstSize.isEmpty()) return dstSize
        return srcSize * contentScale.computeScaleFactor(srcSize, dstSize)
    }
}

@Stable
internal fun ContentScale.toScale() = when (this) {
    ContentScale.Fit, ContentScale.Inside -> Scale.FIT
    else -> Scale.FILL
}

private fun validateRequest(request: ImageRequest) {
    when (request.data) {
        is ImageRequest.Builder -> unsupportedData(
            name = "ImageRequest.Builder",
            description = "Did you forget to call ImageRequest.Builder.build()?"
        )
        is ImageBitmap -> unsupportedData("ImageBitmap")
        is ImageVector -> unsupportedData("ImageVector")
        is Painter -> unsupportedData("Painter")
    }
    require(request.target == null) { "request.target must be null." }
}

private fun unsupportedData(
    name: String,
    description: String = "If you wish to display this $name, use androidx.compose.foundation.Image."
): Nothing = throw IllegalArgumentException("Unsupported type: $name. $description")

private val Size.isPositive get() = width >= 0.5 && height >= 0.5

private fun Size.toSizeOrNull() = when {
    isUnspecified -> coil.size.Size.ORIGINAL
    isPositive -> coil.size.Size(
        width = if (width.isFinite()) Dimension(width.roundToInt()) else Dimension.Undefined,
        height = if (height.isFinite()) Dimension(height.roundToInt()) else Dimension.Undefined
    )
    else -> null
}

private val FakeTransitionTarget = object : TransitionTarget {
    override val view get() = throw UnsupportedOperationException()
    override val drawable: Drawable? get() = null
}

@Composable
@ReadOnlyComposable
internal fun requestOf(model: Any?): ImageRequest {
    if (model is ImageRequest) {
        return model
    } else {
        return ImageRequest.Builder(LocalContext.current).data(model).build()
    }
}

@Stable
internal fun transformOf(
    placeholder: Painter?,
    error: Painter?,
    fallback: Painter?,
): (StreamAsyncImagePainter.State) -> StreamAsyncImagePainter.State {
    return if (placeholder != null || error != null || fallback != null) {
        { state ->
            when (state) {
                is StreamAsyncImagePainter.State.Loading -> {
                    if (placeholder != null) state.copy(painter = placeholder) else state
                }
                is StreamAsyncImagePainter.State.Error -> if (state.result.throwable is NullRequestDataException) {
                    if (fallback != null) state.copy(painter = fallback) else state
                } else {
                    if (error != null) state.copy(painter = error) else state
                }
                else -> state
            }
        }
    } else {
        StreamAsyncImagePainter.DefaultTransform
    }
}

@Stable
internal fun onStateOf(
    onLoading: ((StreamAsyncImagePainter.State.Loading) -> Unit)?,
    onSuccess: ((StreamAsyncImagePainter.State.Success) -> Unit)?,
    onError: ((StreamAsyncImagePainter.State.Error) -> Unit)?,
): ((StreamAsyncImagePainter.State) -> Unit)? {
    return if (onLoading != null || onSuccess != null || onError != null) {
        { state ->
            when (state) {
                is StreamAsyncImagePainter.State.Loading -> onLoading?.invoke(state)
                is StreamAsyncImagePainter.State.Success -> onSuccess?.invoke(state)
                is StreamAsyncImagePainter.State.Error -> onError?.invoke(state)
                is StreamAsyncImagePainter.State.Empty -> {}
            }
        }
    } else {
        null
    }
}