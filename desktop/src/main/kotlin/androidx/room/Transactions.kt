package androidx.room

/**
 * room-ktx's withTransaction for Room's desktop build: one write transaction on the writer
 * connection. DAO calls inside the block run on that same connection, as on Android.
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
