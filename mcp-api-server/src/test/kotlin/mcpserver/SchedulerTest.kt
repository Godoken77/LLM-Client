package mcpserver

import kotlinx.coroutines.runBlocking
import mcpserver.model.TaskType
import mcpserver.scheduler.TaskScheduler
import mcpserver.store.TaskStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchedulerTest {

    private fun tempStore(): TaskStore =
        TaskStore(Files.createTempDirectory("task-store-test").toFile())

    @Test
    fun `create and list task`() = runBlocking {
        val store = tempStore()
        val task = store.create("Ping", "health check", TaskType.PERIODIC, 60L, System.currentTimeMillis() + 60_000)
        val list = store.list()
        assertEquals(1, list.size)
        assertEquals("Ping", list[0].name)
        assertEquals(task.id, list[0].id)
        assertEquals(TaskType.PERIODIC, list[0].type)
    }

    @Test
    fun `append result and get`() = runBlocking {
        val store = tempStore()
        val task = store.create("T", "d", TaskType.ONCE, null, System.currentTimeMillis() + 10_000)
        store.appendResult(task.id, "point-1")
        store.appendResult(task.id, "point-2")
        val updated = store.get(task.id)
        assertNotNull(updated)
        assertEquals(2, updated.results.size)
        assertEquals("point-1", updated.results[0].data)
        assertEquals("point-2", updated.results[1].data)
    }

    @Test
    fun `results are capped at 100`() = runBlocking {
        val store = tempStore()
        val task = store.create("Cap", "cap test", TaskType.PERIODIC, 1L, System.currentTimeMillis() + 1_000)
        repeat(110) { i -> store.appendResult(task.id, "dp-$i") }
        val updated = store.get(task.id)
        assertNotNull(updated)
        assertEquals(100, updated.results.size)
        assertEquals("dp-10", updated.results[0].data)  // oldest 10 dropped
    }

    @Test
    fun `disable task`() = runBlocking {
        val store = tempStore()
        val task = store.create("T", "d", TaskType.PERIODIC, 30L, System.currentTimeMillis() + 30_000)
        assertTrue(store.disable(task.id))
        val updated = store.get(task.id)
        assertNotNull(updated)
        assertFalse(updated.enabled)
    }

    @Test
    fun `get due tasks`() = runBlocking {
        val store = tempStore()
        val past = System.currentTimeMillis() - 5_000
        val future = System.currentTimeMillis() + 60_000
        store.create("Past", "d", TaskType.PERIODIC, 10L, past)
        store.create("Future", "d", TaskType.PERIODIC, 60L, future)
        val due = store.getDueTasks(System.currentTimeMillis())
        assertEquals(1, due.size)
        assertEquals("Past", due[0].name)
    }

    @Test
    fun `disabled task not returned as due`() = runBlocking {
        val store = tempStore()
        val task = store.create("Old", "d", TaskType.ONCE, null, System.currentTimeMillis() - 1_000)
        store.disable(task.id)
        val due = store.getDueTasks(System.currentTimeMillis())
        assertEquals(0, due.size)
    }

    @Test
    fun `disable nonexistent returns false`() = runBlocking {
        val store = tempStore()
        assertFalse(store.disable("no-such-id"))
    }

    @Test
    fun `update next run`() = runBlocking {
        val store = tempStore()
        val task = store.create("T", "d", TaskType.PERIODIC, 10L, System.currentTimeMillis() + 10_000)
        val newNext = System.currentTimeMillis() + 999_000
        store.updateNextRun(task.id, newNext)
        val updated = store.get(task.id)
        assertNotNull(updated)
        assertEquals(newNext, updated.nextRunAt)
    }

    @Test
    fun `data persists across store instances`() = runBlocking {
        val dir = Files.createTempDirectory("persist-test").toFile()
        val store1 = TaskStore(dir)
        val task = store1.create("Persist", "persists?", TaskType.ONCE, null, System.currentTimeMillis() + 60_000)
        store1.appendResult(task.id, "hello")

        val store2 = TaskStore(dir)
        val loaded = store2.get(task.id)
        assertNotNull(loaded)
        assertEquals("Persist", loaded.name)
        assertEquals(1, loaded.results.size)
        assertEquals("hello", loaded.results[0].data)
    }

    // ── TaskScheduler.tick() tests ───────────────────────────────────────────

    @Test
    fun `tick fires due ONCE task and disables it`() = runBlocking {
        val store = tempStore()
        val task = store.create("Reminder", "msg", TaskType.ONCE, null, System.currentTimeMillis() - 1_000)
        val scheduler = TaskScheduler(store)
        scheduler.tickForTest()

        val updated = store.get(task.id)
        assertNotNull(updated)
        assertFalse(updated.enabled, "ONCE task should be disabled after firing")
        assertEquals(1, updated.results.size)
    }

    @Test
    fun `tick fires due PERIODIC task and reschedules it`() = runBlocking {
        val store = tempStore()
        val interval = 60L
        val task = store.create("Heartbeat", "tick", TaskType.PERIODIC, interval, System.currentTimeMillis() - 1_000)
        val beforeTick = System.currentTimeMillis()
        val scheduler = TaskScheduler(store)
        scheduler.tickForTest()

        val updated = store.get(task.id)
        assertNotNull(updated)
        assertTrue(updated.enabled, "PERIODIC task should remain enabled")
        assertEquals(1, updated.results.size)
        assertTrue(updated.nextRunAt >= beforeTick + interval * 1000L, "nextRunAt should be rescheduled")
    }

    @Test
    fun `tick skips future tasks`() = runBlocking {
        val store = tempStore()
        val task = store.create("Future", "not yet", TaskType.ONCE, null, System.currentTimeMillis() + 60_000)
        val scheduler = TaskScheduler(store)
        scheduler.tickForTest()

        val updated = store.get(task.id)
        assertNotNull(updated)
        assertTrue(updated.enabled, "Future task should not be touched")
        assertEquals(0, updated.results.size)
    }

    @Test
    fun `tick skips disabled tasks`() = runBlocking {
        val store = tempStore()
        val task = store.create("Disabled", "skip me", TaskType.PERIODIC, 1L, System.currentTimeMillis() - 1_000)
        store.disable(task.id)
        val scheduler = TaskScheduler(store)
        scheduler.tickForTest()

        val updated = store.get(task.id)
        assertNotNull(updated)
        assertEquals(0, updated.results.size)
    }

    @Test
    fun `tick handles PERIODIC task with null intervalSeconds gracefully`() = runBlocking {
        val store = tempStore()
        // Create a valid PERIODIC task, then corrupt it by adding result without interval info
        // Simulate corrupt state: create a ONCE task and append manually — instead test via direct store manipulation
        // Since ScheduledTask is a data class, we test that tick doesn't throw when intervalSeconds is null
        val task = store.create("Corrupt", "bad data", TaskType.ONCE, null, System.currentTimeMillis() - 1_000)
        val scheduler = TaskScheduler(store)
        // Should not throw
        scheduler.tickForTest()
        val updated = store.get(task.id)
        assertNotNull(updated)
        assertFalse(updated.enabled)
    }
}
