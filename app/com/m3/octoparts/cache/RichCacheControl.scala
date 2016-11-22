package com.m3.octoparts.cache

import com.m3.octoparts.model.CacheControl
import org.apache.http.client.cache.HeaderConstants
import org.joda.time.DateTimeUtils

import scala.language.implicitConversions

object RichCacheControl {

  /**
   * Enrich model cacheControl because we do not want joda or httpclient to be dependency of the exported models.
   */
  implicit class RichCacheControlOps(
      val cacheControl: CacheControl
  ) extends AnyVal {

    def shouldRevalidate = cacheControl.noCache || (cacheControl.canRevalidate && hasExpired)

    /**
     * @return true if there is no max-age header, or there is a max-age header and it has expired
     */
    def hasExpired = cacheControl.expiresAt match {
      case None => true // no max-age header, so always revalidate if we can
      case Some(expiresAt) => expiresAt <= DateTimeUtils.currentTimeMillis()
    }

    def revalidationHeaders: Seq[(String, String)] = Seq(
      cacheControl.etag.map(HeaderConstants.IF_NONE_MATCH -> _),
      cacheControl.lastModified.map(HeaderConstants.IF_MODIFIED_SINCE -> _)
    ).flatten

  }
}
