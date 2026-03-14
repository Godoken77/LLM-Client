package mcpserver.store

import mcpserver.model.Item
import mcpserver.model.User
import java.util.concurrent.ConcurrentHashMap

object UserStore {
    private val users = ConcurrentHashMap<String, User>()

    fun list(): List<User> = users.values.toList()

    fun get(id: String): User? = users[id]

    fun create(user: User): User {
        users[user.id] = user
        return user
    }

    fun update(id: String, name: String?, email: String?): User? {
        val existing = users[id] ?: return null
        val updated = existing.copy(
            name = name ?: existing.name,
            email = email ?: existing.email,
        )
        users[id] = updated
        return updated
    }

    fun delete(id: String): Boolean = users.remove(id) != null

    fun clear() = users.clear()
}

object ItemStore {
    private val items = ConcurrentHashMap<String, Item>()

    fun list(): List<Item> = items.values.toList()

    fun get(id: String): Item? = items[id]

    fun create(item: Item): Item {
        items[item.id] = item
        return item
    }

    fun update(id: String, name: String?, description: String?, price: Double?): Item? {
        val existing = items[id] ?: return null
        val updated = existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description,
            price = price ?: existing.price,
        )
        items[id] = updated
        return updated
    }

    fun delete(id: String): Boolean = items.remove(id) != null

    fun clear() = items.clear()
}
