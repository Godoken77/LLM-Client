package mcpserver

import mcpserver.model.Item
import mcpserver.model.User
import mcpserver.store.ItemStore
import mcpserver.store.UserStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTest {

    @BeforeTest
    fun setUp() {
        UserStore.clear()
        ItemStore.clear()
    }

    @Test
    fun `create and get user`() {
        val user = UserStore.create(User(name = "Alice", email = "alice@example.com"))
        val found = UserStore.get(user.id)
        assertNotNull(found)
        assertEquals("Alice", found.name)
        assertEquals("alice@example.com", found.email)
    }

    @Test
    fun `update user partial fields`() {
        val user = UserStore.create(User(name = "Bob", email = "bob@example.com"))
        val updated = UserStore.update(user.id, name = "Robert", email = null)
        assertNotNull(updated)
        assertEquals("Robert", updated.name)
        assertEquals("bob@example.com", updated.email)
    }

    @Test
    fun `delete user`() {
        val user = UserStore.create(User(name = "Carol", email = "carol@example.com"))
        val deleted = UserStore.delete(user.id)
        assertTrue(deleted)
        assertNull(UserStore.get(user.id))
    }

    @Test
    fun `update nonexistent user returns null`() {
        val result = UserStore.update("no-such-id", name = "X", email = null)
        assertNull(result)
    }

    @Test
    fun `create and get item`() {
        val item = ItemStore.create(Item(name = "Widget", description = "A small widget", price = 9.99))
        val found = ItemStore.get(item.id)
        assertNotNull(found)
        assertEquals("Widget", found.name)
        assertEquals(9.99, found.price)
    }

    @Test
    fun `update item partial fields`() {
        val item = ItemStore.create(Item(name = "Gadget", description = "Old desc", price = 5.0))
        val updated = ItemStore.update(item.id, name = null, description = "New desc", price = 7.5)
        assertNotNull(updated)
        assertEquals("Gadget", updated.name)
        assertEquals("New desc", updated.description)
        assertEquals(7.5, updated.price)
    }

    @Test
    fun `delete item`() {
        val item = ItemStore.create(Item(name = "Thingamajig", description = "desc", price = 1.0))
        val deleted = ItemStore.delete(item.id)
        assertTrue(deleted)
        assertNull(ItemStore.get(item.id))
    }

    @Test
    fun `list returns all created entries`() {
        UserStore.create(User(name = "D", email = "d@d.com"))
        UserStore.create(User(name = "E", email = "e@e.com"))
        assertEquals(2, UserStore.list().size)
    }
}
