// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/BaseViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.interfaces.IEventBus
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ErrorDisplayEventArgs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.lang.ref.WeakReference

/**
 * Base view model class with optimized performance patterns and memory management
 */
abstract class BaseViewModel(
    private val eventBus: IEventBus? = null,
    private val errorDisplayService: IErrorDisplayService? = null
) {

    internal val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Performance optimized thread-safe boolean flags using atomic
    private val _isBusy = AtomicBoolean(false)
    private val _isError = AtomicBoolean(false)
    private val _hasActiveErrors = AtomicBoolean(false)
    private val _isDisposed = AtomicBoolean(false)

    // Performance optimized string management
    private val _errorMessage = MutableStateFlow("")

    // Command tracking with weak references to prevent memory leaks
    private var lastCommandRef: WeakReference<suspend () -> Unit>? = null
    private var _lastCommandParameter: Any? = null

    // Error subscription management
    private val errorSubscriptionLock = Any()
    private var errorDisplayHandler: ((ErrorDisplayEventArgs) -> Unit)? = null

    // Flow-based properties for reactive UI
    val isBusy: StateFlow<Boolean> = flowOf(_isBusy.get())
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isError: StateFlow<Boolean> = flowOf(_isError.get())
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    val hasActiveErrors: StateFlow<Boolean> = flowOf(_hasActiveErrors.get())
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Cached command for retry capability
    val lastCommand: (suspend () -> Unit)?
        get() = lastCommandRef?.get()

    val lastCommandParameter: Any?
        get() = _lastCommandParameter

    // Event for system errors (MediatR failures)
    private val _errorOccurred = MutableSharedFlow<OperationErrorEvent>()
    val errorOccurred: SharedFlow<OperationErrorEvent> = _errorOccurred.asSharedFlow()

    init {
        subscribeToErrorDisplayService()
    }

    /**
     * Sets the busy state with atomic operation
     */
    protected fun setBusy(busy: Boolean) {
        _isBusy.set(busy)
    }

    /**
     * Gets the current busy state
     */
    protected fun isBusy(): Boolean = _isBusy.get()

    /**
     * Optimized command tracking with weak references
     */
    protected fun trackCommand(command: suspend () -> Unit, parameter: Any? = null) {
        if (_isDisposed.get()) return

        lastCommandRef = WeakReference(command)
        _lastCommandParameter = parameter
    }

    /**
     * Executes a command and tracks it for retry capability
     */
    suspend fun executeAndTrack(command: suspend () -> Unit, parameter: Any? = null) {
        if (_isDisposed.get()) return

        trackCommand(command, parameter)
        command()
    }

    /**
     * Optimized retry with null checks and weak reference handling
     */
    suspend fun retryLastCommand() {
        if (_isDisposed.get()) return

        lastCommandRef?.get()?.let { command ->
            command()
        }
    }

    /**
     * Optimized system error handling with event emission
     */
    fun onSystemError(message: String) {
        if (_isDisposed.get()) return

        viewModelScope.launch {
            val errorEvent = OperationErrorEvent(message)
            _errorOccurred.emit(errorEvent)
        }
    }

    /**
     * Inline validation error setter for performance
     */
    protected fun setValidationError(message: String) {
        _errorMessage.value = message
        _isError.set(true)
    }

    /**
     * Optimized error clearing with batch updates
     */
    protected fun clearErrors() {
        val wasError = _isError.get()
        val hadActiveErrors = _hasActiveErrors.get()
        val hadErrorMessage = _errorMessage.value.isNotEmpty()

        if (wasError || hadActiveErrors || hadErrorMessage) {
            _isError.set(false)
            _errorMessage.value = ""
            _hasActiveErrors.set(false)
        }
    }

    /**
     * Weak event subscription to prevent memory leaks
     */
    private fun subscribeToErrorDisplayService() {
        errorDisplayService?.let { service ->
            synchronized(errorSubscriptionLock) {
                if (errorDisplayHandler == null) {
                    errorDisplayHandler = ::onErrorsReady
                    service.subscribeToErrors(errorDisplayHandler!!)
                }
            }
        }
    }

    private fun unsubscribeFromErrorDisplayService() {
        errorDisplayService?.let { service ->
            synchronized(errorSubscriptionLock) {
                errorDisplayHandler?.let { handler ->
                    service.unsubscribeFromErrors(handler)
                    errorDisplayHandler = null
                }
            }
        }
    }

    /**
     * Optimized error handling with coroutine-safe operations
     */
    private fun onErrorsReady(eventArgs: ErrorDisplayEventArgs) {
        if (_isDisposed.get()) return

        _hasActiveErrors.set(true)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    onSystemError(eventArgs.displayMessage)
                }
            } finally {
                _hasActiveErrors.set(false)
            }
        }
    }

    /**
     * Execute a suspending operation safely with error handling
     */
    protected suspend fun executeSafely(
        operation: suspend () -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            setBusy(true)
            clearErrors()
            operation()
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            onError?.invoke(e) ?: onSystemError("Operation failed: ${e.message}")
        } finally {
            setBusy(false)
        }
    }

    /**
     * Dispose pattern implementation with proper cleanup
     */
    open fun dispose() {
        if (_isDisposed.getAndSet(true)) return

        // Unsubscribe from error display service
        unsubscribeFromErrorDisplayService()

        // Clear weak references
        lastCommandRef = null
        _lastCommandParameter = null

        // Cancel coroutine scope
        viewModelScope.cancel()
    }
}

/**
 * Object pool for OperationErrorEvent to reduce allocations
 */
internal object OperationErrorEventPool {
    private val pool = ConcurrentLinkedQueue<OperationErrorEvent>()
    private val poolCount = AtomicInteger(0)
    private const val MAX_POOL_SIZE = 10

    fun get(message: String): OperationErrorEvent {
        return pool.poll()?.apply {
            updateMessage(message)
            poolCount.decrementAndGet()
        } ?: OperationErrorEvent(message)
    }

    fun release(event: OperationErrorEvent) {
        if (poolCount.get() < MAX_POOL_SIZE) {
            pool.offer(event)
            poolCount.incrementAndGet()
        }
    }
}

/**
 * Optimized OperationErrorEvent with reusable message
 */
data class OperationErrorEvent(
    private var _message: String
) {
    val message: String get() = _message

    // Internal method for pool reuse
    internal fun updateMessage(message: String) {
        this._message = message
    }
}