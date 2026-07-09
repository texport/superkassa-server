package io.github.texport.superkassa.jvm.storage.impl.core

import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockAdapter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueLockTest {

    @Test
    fun testMultiNodeLeaseLockBehavior() {
        val dbFile = Files.createTempFile("queue-lock-test", ".db").toFile()
        val storageConfig = StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}")
        val storageBootstrap = DefaultStorageBootstrap()
        storageBootstrap.migrate(storageConfig)

        val storage = StorageAdapter(storageBootstrap, storageConfig)
        val lockPort1 = StorageBackedLeaseLockAdapter(storage)
        val lockPort2 = StorageBackedLeaseLockAdapter(storage)

        val cashboxId = "cashbox-123"
        val now = System.currentTimeMillis()

        // 1. Node 1 acquires the lock for 10 seconds.
        val acquired1 = lockPort1.tryAcquire(cashboxId, "node-1", now + 10000, now)
        assertTrue(acquired1, "Node 1 should successfully acquire the lock")

        // 2. Node 2 tries to acquire the lock immediately -> should fail.
        val acquired2 = lockPort2.tryAcquire(cashboxId, "node-2", now + 10000, now)
        assertFalse(acquired2, "Node 2 should fail to acquire lock while lease is active")

        // 3. Node 2 tries to acquire the lock after lease expires (simulated by setting now = now + 11000).
        val acquired3 = lockPort2.tryAcquire(cashboxId, "node-2", now + 20000, now + 11000)
        assertTrue(acquired3, "Node 2 should acquire lock after lease has expired")

        // 4. Node 1 tries to renew after Node 2 took the lock -> should fail.
        val renewed1 = lockPort1.renew(cashboxId, "node-1", now + 30000, now + 12000)
        assertFalse(renewed1, "Node 1 should fail to renew a lock owned by Node 2")

        // 5. Node 2 renews the lock -> should succeed.
        val renewed2 = lockPort2.renew(cashboxId, "node-2", now + 30000, now + 12000)
        assertTrue(renewed2, "Node 2 should successfully renew its own active lock")

        // 6. Node 2 releases the lock -> should succeed.
        val released2 = lockPort2.release(cashboxId, "node-2")
        assertTrue(released2, "Node 2 should successfully release the lock")

        // 7. Node 1 can now acquire it again -> should succeed.
        val acquired4 = lockPort1.tryAcquire(cashboxId, "node-1", now + 40000, now + 13000)
        assertTrue(acquired4, "Node 1 should successfully acquire the released lock")
    }
}
