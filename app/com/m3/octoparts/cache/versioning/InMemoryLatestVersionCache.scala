package com.m3.octoparts.cache.versioning

import com.google.common.cache.{ Cache, CacheBuilder }

/**
 * A simple implementation of [[LatestVersionCache]] that holds the latest versions as Maps
 */
class InMemoryLatestVersionCache(
    maxCacheKeys: Long
) extends LatestVersionCache {

  import com.m3.octoparts.cache.versioning.LatestVersionCache._

  private[versioning] val partVersions = configureMemoryCache(CacheBuilder.newBuilder()).build[PartId, java.lang.Long]()
  private[versioning] val paramVersions = configureMemoryCache(CacheBuilder.newBuilder()).build[VersionedParamKey, java.lang.Long]()

  def updatePartVersion(partId: PartId, version: Version): Unit = {
    partVersions.put(partId, version)
  }

  def updateParamVersion(
    versionedParamKey: VersionedParamKey,
    version: Version
  ): Unit = {
    paramVersions.put(versionedParamKey, version)
  }

  def getPartVersion(partId: PartId) = Option(partVersions.getIfPresent(partId)).map(Long.unbox)

  def getParamVersion(versionedParamKey: VersionedParamKey) =
    Option(paramVersions.getIfPresent(versionedParamKey)).map(Long.unbox)

  private def configureMemoryCache(builder: CacheBuilder[Object, Object]): CacheBuilder[Object, Object] = {
    builder.maximumSize(maxCacheKeys)
  }
}
