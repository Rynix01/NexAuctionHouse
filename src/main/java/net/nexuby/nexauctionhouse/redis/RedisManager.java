package net.nexuby.nexauctionhouse.redis;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class RedisManager {

    private final NexAuctionHouse plugin;
    private JedisPool pool;
    private final Map<String, SubscriptionThread> activeSubscriptions = new ConcurrentHashMap<>();
    private String channelPrefix;

    public RedisManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        ConfigManager config = plugin.getConfigManager();
        this.channelPrefix = config.getRedisChannelPrefix();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getRedisMaxPoolSize());
        poolConfig.setMaxIdle(config.getRedisMaxPoolSize());
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        String host = config.getRedisHost();
        int port = config.getRedisPort();
        int timeout = config.getRedisTimeout();
        String password = config.getRedisPassword();
        int database = config.getRedisDatabase();

        try {
            if (password != null && !password.isEmpty()) {
                this.pool = new JedisPool(poolConfig, host, port, timeout, password, database);
            } else {
                this.pool = new JedisPool(poolConfig, host, port, timeout, null, database);
            }

            // Test the connection
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            plugin.getLogger().info("Redis connection established. (" + host + ":" + port + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to Redis at " + host + ":" + port, e);
            return false;
        }
    }

    /**
     * Publishes a message to a Redis channel (prefixed).
     */
    public void publish(String channel, String message) {
        String fullChannel = channelPrefix + ":" + channel;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(fullChannel, message);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish to Redis channel: " + fullChannel, e);
            }
        });
    }

    /**
     * Subscribes to a Redis channel (prefixed) with the given message handler.
     * The handler receives (channel, message) on a dedicated thread.
     */
    public void subscribe(String channel, BiConsumer<String, String> handler) {
        String fullChannel = channelPrefix + ":" + channel;

        JedisPubSub pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String message) {
                try {
                    handler.accept(ch, message);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error processing Redis message on " + ch, e);
                }
            }
        };

        SubscriptionThread thread = new SubscriptionThread(fullChannel, pubSub);
        thread.setDaemon(true);
        thread.setName("NexAH-Redis-Sub-" + channel);
        thread.start();

        activeSubscriptions.put(channel, thread);
    }

    /**
     * Attempts to acquire a distributed lock using Redis SET NX with expiration.
     * Returns true if the lock was acquired.
     */
    public boolean tryLock(String key, int ttlSeconds) {
        String fullKey = channelPrefix + ":lock:" + key;
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.set(fullKey, plugin.getConfigManager().getServerId(),
                    SetParams.setParams().nx().ex(ttlSeconds));
            return "OK".equals(result);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to acquire Redis lock: " + fullKey, e);
            return false;
        }
    }

    /**
     * Releases a distributed lock.
     */
    public void releaseLock(String key) {
        String fullKey = channelPrefix + ":lock:" + key;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(fullKey);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to release Redis lock: " + fullKey, e);
        }
    }

    public boolean isConnected() {
        if (pool == null || pool.isClosed()) return false;
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void disconnect() {
        // Unsubscribe and stop all subscription threads
        for (Map.Entry<String, SubscriptionThread> entry : activeSubscriptions.entrySet()) {
            entry.getValue().shutdown();
        }
        activeSubscriptions.clear();

        if (pool != null && !pool.isClosed()) {
            pool.close();
            plugin.getLogger().info("Redis connection closed.");
        }
    }

    /**
     * Dedicated thread for a Redis subscription. Handles reconnection on failure.
     */
    private class SubscriptionThread extends Thread {
        private final String channel;
        private final JedisPubSub pubSub;
        private volatile boolean running = true;

        SubscriptionThread(String channel, JedisPubSub pubSub) {
            this.channel = channel;
            this.pubSub = pubSub;
        }

        @Override
        public void run() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = pool.getResource()) {
                    jedis.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (running) {
                        plugin.getLogger().log(Level.WARNING, "Redis subscription lost for " + channel + ", reconnecting in 5s...", e);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        void shutdown() {
            running = false;
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
            this.interrupt();
        }
    }
}
