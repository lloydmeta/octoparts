package com.m3.octoparts.wiring

import java.util.concurrent.{ TimeUnit, ThreadPoolExecutor, ArrayBlockingQueue, BlockingQueue }

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.m3.octoparts.cache.dummy.{ DummyCacheOps, DummyCache, DummyRawCache, DummyLatestVersionCache }
import com.m3.octoparts.cache.key.MemcachedKeyGenerator
import com.m3.octoparts.cache.memcached.{ MemcachedCacheOps, MemcachedCache, InMemoryRawCache, MemcachedRawCache }
import com.m3.octoparts.cache.versioning.InMemoryLatestVersionCache
import com.m3.octoparts.cache.LoggingRawCache
import shade.memcached.{ Configuration => ShadeConfig, FailureMode, AuthConfiguration, Memcached, Protocol }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import com.softwaremill.macwire._

trait CacheModule extends UtilsModule {

  private implicit lazy val cacheExecutor: ExecutionContext = {

    val namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("cache-%d").build()
    val poolSize = typesafeConfig.getInt("caching.pool.size")
    val queueSize = typesafeConfig.getInt("caching.queue.size")
    val queue: BlockingQueue[Runnable] = new ArrayBlockingQueue[Runnable](queueSize)

    ExecutionContext.fromExecutor(
      new ThreadPoolExecutor(0, poolSize, 1L, TimeUnit.MINUTES, queue, namedThreadFactory)
    )
  }

  lazy val latestVersionCache = {
    if (cachingDisabled) {
      DummyLatestVersionCache
    } else {
      val maxInMemoryLVCKeys = configuration.getLong("caching.versionCachingSize").getOrElse(100000L)
      new InMemoryLatestVersionCache(maxInMemoryLVCKeys)
    }
  }

  lazy val rawCache = if (cachingDisabled) {
    DummyRawCache
  } else {
    val cache = if (useInMemoryCache) {
      new InMemoryRawCache(zipkinService)(cacheExecutor)
    } else {
      val hostPort = typesafeConfig.getString("memcached.host")
      val timeout = typesafeConfig.getInt("memcached.timeout").millis
      // should be one of "Text" or "Binary"
      val protocol = typesafeConfig.getString("memcached.protocol")
      val auth = for {
        user <- configuration.getString("memcached.user")
        password <- configuration.getString("memcached.password")
      } yield AuthConfiguration(user, password)

      val shade = Memcached(
        ShadeConfig(
          addresses = hostPort,
          operationTimeout = timeout,
          protocol = Protocol.withName(protocol),
          authentication = auth,
          failureMode = FailureMode.Redistribute
        )
      )(cacheExecutor)

      new LoggingRawCache(new MemcachedRawCache(shade))(cacheExecutor)
    }
    sys.addShutdownHook {
      cache.close()
    }
    cache
  }

  lazy val memcachedKeyGenerator = MemcachedKeyGenerator

  lazy val cache = {
    if (cachingDisabled) {
      DummyCache
    } else {
      wire[MemcachedCache]
    }
  }

  lazy val cacheOps = {
    if (cachingDisabled) {
      DummyCacheOps
    } else {
      wire[MemcachedCacheOps]
    }
  }

  lazy val cachingDisabled = configuration.getBoolean("caching.disabled").getOrElse(false)

  lazy val useInMemoryCache = configuration.getBoolean("caching.inmemory").getOrElse(false)

}