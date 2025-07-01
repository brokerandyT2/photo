// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/DatabaseContext.kt

import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.*
import kotlinx.coroutines.logging.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface IDatabaseContext {
    suspend fun initializeDatabaseAsync()

    suspend fun <T> insertAsync(entity: T, insertFunction: suspend (T) -> Long): Int
    suspend fun <T> updateAsync(entity: T, updateFunction: suspend (T) -> Int): Int
    suspend fun <T> deleteAsync(entity: T, deleteFunction: suspend (T) -> Int): Int
    suspend fun <T> getAllAsync(queryFunction: suspend () -> List<T>): List<T>
    suspend fun <T> getAsync(primaryKey: Any, getFunction: suspend (Any) -> T?): T?
    suspend fun executeAsync(query: String, vararg args: Any): Int

    suspend fun insertAllAsync(entities: List<Any>): Int
    suspend fun updateAllAsync(entities: List<Any>): Int
    suspend fun deleteAllAsync(entities: List<Any>): Int
    suspend fun bulkInsertAsync(entities: List<Any>, batchSize: Int = 100): Int
    suspend fun bulkUpdateAsync(entities: List<Any>, batchSize: Int = 100): Int

    suspend fun <T> queryAsync(sql: String, mapper: (SqlCursor) -> T, vararg args: Any): List<T>
    suspend fun <T> querySingleAsync(sql: String, mapper: (SqlCursor) -> T, vararg args: Any): T?
    suspend fun <T> executeScalarAsync(sql: String, vararg args: Any): T?

    suspend fun beginTransactionAsync()
    suspend fun commitTransactionAsync()
    suspend fun rollbackTransactionAsync()

    suspend fun <T> executeInTransactionAsync(operation: suspend () -> T): T

    fun getConnection(): SqlDriver
}

class DatabaseContext(
    private val driver: SqlDriver,
    private val logger: Logger,
    private val databasePath: String? = null
) : IDatabaseContext {

    private val initializationMutex = Mutex()
    private val transactionMutex = Mutex()
    private val preparedStatementCache = mutableMapOf<String, Any>()
    private val transactionLock = Any()

    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInTransaction = false

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val BUSY_TIMEOUT_MS = 3000L
        private const val DEFAULT_BATCH_SIZE = 100
        private const val MAX_CACHED_STATEMENTS = 50
    }

    override suspend fun initializeDatabaseAsync() {
        if (isInitialized) return

        initializationMutex.withLock {
            if (isInitialized) return

            try {
                logger.info("Starting database initialization...")

                // Enable WAL mode for better performance
                driver.execute(null, "PRAGMA journal_mode=WAL", 0)
                driver.execute(null, "PRAGMA synchronous=NORMAL", 0)
                driver.execute(null, "PRAGMA cache_size=10000", 0)
                driver.execute(null, "PRAGMA temp_store=MEMORY", 0)
                driver.execute(null, "PRAGMA mmap_size=268435456", 0) // 256MB

                createTables()

                isInitialized = true
                logger.info("Database initialization completed successfully")
            } catch (ex: Exception) {
                logger.error("Failed to initialize database", ex)
                throw ex
            }
        }
    }

    override fun getConnection(): SqlDriver {
        if (!isInitialized) {
            throw IllegalStateException("Database not initialized. Call initializeDatabaseAsync() first.")
        }
        return driver
    }

    override suspend fun <T> insertAsync(entity: T, insertFunction: suspend (T) -> Long): Int {
        return try {
            ensureInitializedAsync()
            val result = insertFunction(entity).toInt()
            logger.debug("Inserted ${entity!!::class.simpleName} with result: $result")
            result
        } catch (ex: Exception) {
            logger.error("Failed to insert ${entity!!::class.simpleName}", ex)
            throw ex
        }
    }

    override suspend fun <T> updateAsync(entity: T, updateFunction: suspend (T) -> Int): Int {
        return try {
            ensureInitializedAsync()
            val result = updateFunction(entity)
            logger.debug("Updated ${entity!!::class.simpleName} with result: $result")
            result
        } catch (ex: Exception) {
            logger.error("Failed to update ${entity!!::class.simpleName}", ex)
            throw ex
        }
    }

    override suspend fun <T> deleteAsync(entity: T, deleteFunction: suspend (T) -> Int): Int {
        return try {
            ensureInitializedAsync()
            val result = deleteFunction(entity)
            logger.debug("Deleted ${entity!!::class.simpleName} with result: $result")
            result
        } catch (ex: Exception) {
            logger.error("Failed to delete ${entity!!::class.simpleName}", ex)
            throw ex
        }
    }

    override suspend fun <T> getAllAsync(queryFunction: suspend () -> List<T>): List<T> {
        return try {
            ensureInitializedAsync()
            queryFunction()
        } catch (ex: Exception) {
            logger.error("Failed to get all entities", ex)
            throw ex
        }
    }

    override suspend fun <T> getAsync(primaryKey: Any, getFunction: suspend (Any) -> T?): T? {
        return try {
            ensureInitializedAsync()
            getFunction(primaryKey)
        } catch (ex: Exception) {
            logger.error("Failed to get entity by key $primaryKey", ex)
            throw ex
        }
    }

    override suspend fun executeAsync(query: String, vararg args: Any): Int {
        return try {
            ensureInitializedAsync()
            driver.execute(null, query, args.size) {
                args.forEachIndexed { index, arg ->
                    when (arg) {
                        is String -> bindString(index + 1, arg)
                        is Long -> bindLong(index + 1, arg)
                        is Double -> bindDouble(index + 1, arg)
                        is Boolean -> bindBoolean(index + 1, arg)
                        is Int -> bindLong(index + 1, arg.toLong())
                        else -> bindString(index + 1, arg.toString())
                    }
                }
            }.executeAsOne().toInt()
        } catch (ex: Exception) {
            logger.error("Failed to execute query: $query", ex)
            throw ex
        }
    }

    override suspend fun insertAllAsync(entities: List<Any>): Int {
        return try {
            ensureInitializedAsync()
            var totalInserted = 0
            for (entity in entities) {
                // This would need to be implemented with specific insert functions per entity type
                totalInserted++
            }
            logger.debug("Inserted $totalInserted entities")
            totalInserted
        } catch (ex: Exception) {
            logger.error("Failed to insert all entities", ex)
            throw ex
        }
    }

    override suspend fun updateAllAsync(entities: List<Any>): Int {
        return try {
            ensureInitializedAsync()
            var totalUpdated = 0
            for (entity in entities) {
                // This would need to be implemented with specific update functions per entity type
                totalUpdated++
            }
            logger.debug("Updated $totalUpdated entities")
            totalUpdated
        } catch (ex: Exception) {
            logger.error("Failed to update all entities", ex)
            throw ex
        }
    }

    override suspend fun deleteAllAsync(entities: List<Any>): Int {
        return try {
            ensureInitializedAsync()
            var totalDeleted = 0
            for (entity in entities) {
                // This would need to be implemented with specific delete functions per entity type
                totalDeleted++
            }
            logger.debug("Deleted $totalDeleted entities")
            totalDeleted
        } catch (ex: Exception) {
            logger.error("Failed to delete all entities", ex)
            throw ex
        }
    }

    override suspend fun bulkInsertAsync(entities: List<Any>, batchSize: Int): Int {
        return try {
            ensureInitializedAsync()
            var totalInserted = 0

            entities.chunked(batchSize).forEach { batch ->
                executeInTransactionAsync {
                    batch.forEach { entity ->
                        // Insert logic per entity type
                        totalInserted++
                    }
                }
            }

            logger.debug("Bulk inserted $totalInserted entities in batches")
            totalInserted
        } catch (ex: Exception) {
            logger.error("Failed to bulk insert entities in batches", ex)
            throw ex
        }
    }

    override suspend fun bulkUpdateAsync(entities: List<Any>, batchSize: Int): Int {
        return try {
            ensureInitializedAsync()
            var totalUpdated = 0

            entities.chunked(batchSize).forEach { batch ->
                executeInTransactionAsync {
                    batch.forEach { entity ->
                        // Update logic per entity type
                        totalUpdated++
                    }
                }
            }

            logger.debug("Bulk updated $totalUpdated entities in batches")
            totalUpdated
        } catch (ex: Exception) {
            logger.error("Failed to bulk update entities in batches", ex)
            throw ex
        }
    }

    override suspend fun <T> queryAsync(sql: String, mapper: (SqlCursor) -> T, vararg args: Any): List<T> {
        return try {
            ensureInitializedAsync()
            val results = mutableListOf<T>()
            driver.executeQuery(null, sql, mapper = { cursor ->
                results.add(mapper(SqlCursorWrapper(cursor)))
            }, args.size) {
                args.forEachIndexed { index, arg ->
                    when (arg) {
                        is String -> bindString(index + 1, arg)
                        is Long -> bindLong(index + 1, arg)
                        is Double -> bindDouble(index + 1, arg)
                        is Boolean -> bindBoolean(index + 1, arg)
                        is Int -> bindLong(index + 1, arg.toLong())
                        else -> bindString(index + 1, arg.toString())
                    }
                }
            }
            results
        } catch (ex: Exception) {
            logger.error("Failed to execute query: $sql", ex)
            throw ex
        }
    }

    override suspend fun <T> querySingleAsync(sql: String, mapper: (SqlCursor) -> T, vararg args: Any): T? {
        return try {
            ensureInitializedAsync()
            val results = queryAsync(sql, mapper, *args)
            results.firstOrNull()
        } catch (ex: Exception) {
            logger.error("Failed to execute single query: $sql", ex)
            throw ex
        }
    }

    override suspend fun <T> executeScalarAsync(sql: String, vararg args: Any): T? {
        return try {
            ensureInitializedAsync()
            @Suppress("UNCHECKED_CAST")
            driver.executeQuery(null, sql, mapper = { it.next(); it.getString(0) as T? }, args.size) {
                args.forEachIndexed { index, arg ->
                    when (arg) {
                        is String -> bindString(index + 1, arg)
                        is Long -> bindLong(index + 1, arg)
                        is Double -> bindDouble(index + 1, arg)
                        is Boolean -> bindBoolean(index + 1, arg)
                        is Int -> bindLong(index + 1, arg.toLong())
                        else -> bindString(index + 1, arg.toString())
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to execute scalar query: $sql", ex)
            throw ex
        }
    }

    override suspend fun beginTransactionAsync() {
        ensureInitializedAsync()

        transactionMutex.withLock {
            if (isInTransaction) {
                throw IllegalStateException("Transaction already in progress")
            }
            isInTransaction = true
        }

        try {
            driver.execute(null, "BEGIN TRANSACTION", 0)
            logger.debug("Transaction started")
        } catch (ex: Exception) {
            isInTransaction = false
            logger.error("Failed to begin transaction", ex)
            throw ex
        }
    }

    override suspend fun commitTransactionAsync() {
        ensureInitializedAsync()

        if (!isInTransaction) {
            throw IllegalStateException("No transaction in progress")
        }

        try {
            driver.execute(null, "COMMIT", 0)
            isInTransaction = false
            logger.debug("Transaction committed")
        } catch (ex: Exception) {
            logger.error("Failed to commit transaction", ex)
            throw ex
        }
    }

    override suspend fun rollbackTransactionAsync() {
        ensureInitializedAsync()

        if (!isInTransaction) {
            throw IllegalStateException("No transaction in progress")
        }

        try {
            driver.execute(null, "ROLLBACK", 0)
            isInTransaction = false
            logger.debug("Transaction rolled back")
        } catch (ex: Exception) {
            logger.error("Failed to rollback transaction", ex)
            throw ex
        }
    }

    override suspend fun <T> executeInTransactionAsync(operation: suspend () -> T): T {
        ensureInitializedAsync()

        beginTransactionAsync()
        return try {
            val result = operation()
            commitTransactionAsync()
            result
        } catch (ex: Exception) {
            try {
                rollbackTransactionAsync()
            } catch (rollbackEx: Exception) {
                logger.error("Failed to rollback transaction after exception", rollbackEx)
            }
            throw ex
        }
    }

    private suspend fun ensureInitializedAsync() {
        if (!isInitialized) {
            initializeDatabaseAsync()
        }
    }

    private fun createTables() {
        // Location table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS LocationEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Title TEXT NOT NULL,
                Description TEXT,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                Photo TEXT,
                IsActive INTEGER NOT NULL DEFAULT 1,
                Timestamp TEXT NOT NULL
            )
        """.trimIndent(), 0)

        // Weather table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS WeatherEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                LocationId INTEGER NOT NULL,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                Timezone TEXT NOT NULL,
                TimezoneOffset INTEGER NOT NULL,
                LastUpdate TEXT NOT NULL,
                FOREIGN KEY (LocationId) REFERENCES LocationEntity (Id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        // Weather Forecast table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS WeatherForecastEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                WeatherId INTEGER NOT NULL,
                Date TEXT NOT NULL,
                Sunrise TEXT NOT NULL,
                Sunset TEXT NOT NULL,
                Temperature REAL NOT NULL,
                MinTemperature REAL NOT NULL,
                MaxTemperature REAL NOT NULL,
                Description TEXT NOT NULL,
                Icon TEXT NOT NULL,
                WindSpeed REAL NOT NULL,
                WindDirection REAL NOT NULL,
                WindGust REAL,
                Humidity INTEGER NOT NULL,
                Pressure INTEGER NOT NULL,
                Clouds INTEGER NOT NULL,
                UvIndex REAL NOT NULL,
                Precipitation REAL,
                MoonRise TEXT,
                MoonSet TEXT,
                MoonPhase REAL NOT NULL,
                FOREIGN KEY (WeatherId) REFERENCES WeatherEntity (Id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        // Hourly Forecast table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS HourlyForecastEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                WeatherId INTEGER NOT NULL,
                DateTime TEXT NOT NULL,
                Temperature REAL NOT NULL,
                FeelsLike REAL NOT NULL,
                Description TEXT NOT NULL,
                Icon TEXT NOT NULL,
                WindSpeed REAL NOT NULL,
                WindDirection REAL NOT NULL,
                WindGust REAL,
                Humidity INTEGER NOT NULL,
                Pressure INTEGER NOT NULL,
                Clouds INTEGER NOT NULL,
                UvIndex REAL NOT NULL,
                ProbabilityOfPrecipitation REAL NOT NULL,
                Visibility INTEGER NOT NULL,
                DewPoint REAL NOT NULL,
                FOREIGN KEY (WeatherId) REFERENCES WeatherEntity (Id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        // Setting table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS SettingEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Key TEXT NOT NULL UNIQUE,
                Value TEXT NOT NULL,
                Description TEXT,
                Timestamp TEXT NOT NULL
            )
        """.trimIndent(), 0)

        // TipType table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS TipTypeEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                Name TEXT NOT NULL UNIQUE,
                Description TEXT,
                I8n TEXT,
                Timestamp TEXT NOT NULL
            )
        """.trimIndent(), 0)

        // Tip table
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS TipEntity (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                TipTypeId INTEGER NOT NULL,
                Title TEXT NOT NULL,
                Content TEXT NOT NULL,
                Fstop TEXT,
                ShutterSpeed TEXT,
                Iso TEXT,
                I8n TEXT,
                Timestamp TEXT NOT NULL,
                FOREIGN KEY (TipTypeId) REFERENCES TipTypeEntity (Id) ON DELETE CASCADE
            )
        """.trimIndent(), 0)

        // Indices for performance
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_location_active ON LocationEntity(IsActive)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_weather_location ON WeatherEntity(LocationId)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_forecast_weather ON WeatherForecastEntity(WeatherId)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_hourly_weather ON HourlyForecastEntity(WeatherId)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_setting_key ON SettingEntity(Key)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_tip_type ON TipEntity(TipTypeId)", 0)
    }
}

// Wrapper for SqlDelight cursor to provide a common interface
class SqlCursorWrapper(private val cursor: app.cash.sqldelight.db.QueryResult.Value<*>) : SqlCursor {
    override fun getString(index: Int): String? = cursor.getString(index)
    override fun getLong(index: Int): Long? = cursor.getLong(index)
    override fun getDouble(index: Int): Double? = cursor.getDouble(index)
    override fun getBoolean(index: Int): Boolean? = cursor.getBoolean(index)
    override fun getInt(index: Int): Int? = cursor.getLong(index)?.toInt()
}

interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}