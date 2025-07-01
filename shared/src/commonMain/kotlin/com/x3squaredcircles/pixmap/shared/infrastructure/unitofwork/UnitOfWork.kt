// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/unitofwork/UnitOfWork.kt

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.*
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.coroutines.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UnitOfWork(
    private val context: IDatabaseContext,
    private val logger: Logger
) : IUnitOfWork, KoinComponent {

    private var inTransaction: Boolean = false

    override val locations: ILocationRepository by inject()

    override val subscriptions: ISubscriptionRepository by inject()

    override val weather: IWeatherRepository by inject()

    override val tips: ITipRepository by inject()

    override val tipTypes: ITipTypeRepository by inject()

    override val settings: ISettingRepository by inject()

    override suspend fun saveChangesAsync(): Int {
        logger.debug("saveChangesAsync called")
        return 1
    }

    override fun getDatabaseContext(): IDatabaseContext {
        return context
    }

    override suspend fun beginTransactionAsync() {
        if (inTransaction) {
            throw IllegalStateException("Transaction already in progress")
        }

        try {
            context.beginTransactionAsync()
            inTransaction = true
            logger.debug("Transaction started")
        } catch (ex: Exception) {
            logger.error("Failed to begin transaction", ex)
            throw ex
        }
    }

    override suspend fun commitAsync() {
        logger.debug("commitAsync called")

        if (inTransaction) {
            context.commitTransactionAsync()
            inTransaction = false
            logger.debug("Transaction committed")
        }
    }

    override suspend fun rollbackAsync() {
        if (!inTransaction) {
            throw IllegalStateException("No transaction in progress")
        }

        try {
            context.rollbackTransactionAsync()
            inTransaction = false
            logger.debug("Transaction rolled back")
        } catch (ex: Exception) {
            logger.error("Failed to rollback transaction", ex)
            throw ex
        }
    }

    private var disposed = false

    fun dispose() {
        if (disposed) return

        if (inTransaction) {
            try {
                // Note: This would need to be handled properly in a real implementation
                // since we can't call suspend functions from dispose
                logger.error("Transaction still in progress during disposal")
            } catch (ex: Exception) {
                logger.error("Error rolling back transaction during disposal", ex)
            }
        }

        disposed = true
    }
}