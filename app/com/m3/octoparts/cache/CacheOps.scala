package com.m3.octoparts.cache

import com.m3.octoparts.cache.directive.CacheDirective
import com.m3.octoparts.cache.versioning._
import com.m3.octoparts.model.PartResponse
import com.twitter.zipkin.gen.Span

import scala.concurrent.Future

/**
 * Operations related to caching of [[com.m3.octoparts.model.PartResponse]]s.
 */
trait CacheOps {

  /**
   * Increase the cache version of the given part ID,
   * effectively invalidating all cached PartResponses for this part.
   */
  def increasePartVersion(partId: String)(implicit parentSpan: Span): Future[Unit]

  /**
   * Increase the cache version of the given (partId, parameter name, parameter value) combination,
   * effectively invalidating all cached PartResponses for this combination.
   */
  def increaseParamVersion(vpk: VersionedParamKey)(implicit parentSpan: Span): Future[Unit]

  /**
   * Lookup the PartResponse in the cache. If it is found, return it,
   * otherwise run the provided block, store the result in the cache and return it.
   *
   * @param directive cache directive
   * @param f block to generate a value if one is not found in the cache
   * @return a Future of the value
   */
  def putIfAbsent(directive: CacheDirective)(
    f: => Future[PartResponse]
  )(implicit parentSpan: Span): Future[PartResponse]

  /**
   * Unconditional PUT of a PartResponse
   */
  def saveLater(
    partResponse: PartResponse,
    directive: CacheDirective
  )(implicit parentSpan: Span): Future[Unit]
}
