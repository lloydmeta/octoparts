package com.m3.octoparts.util

import org.apache.commons.lang3.mutable.MutableBoolean
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FunSpec, Matchers }

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class KeyedResourcePoolSpec extends FunSpec with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.reflectiveCalls

  it("should clean up obsolete clients") {

    val holder = new KeyedResourcePool[String, Int] {
      var i = 0
      var j = 0

      def makeNew(k: String) = {
        i += 1
        i
      }

      override def onRemove(value: Int): Unit = {
        j += value
      }
    }
    holder.getOrCreate("a") should be(1)

    // return existing
    holder.getOrCreate("a") should be(1)

    // new one
    holder.getOrCreate("b") should be(2)

    //clean up
    holder.cleanObsolete(Set("b"))
    holder.j should be(1)

    holder.getOrCreate("a") should be(3)

  }

  it("should never return closed items") {
    val firstTime = new MutableBoolean(false)
    val holder = new KeyedResourcePool[String, MutableBoolean] {
      def makeNew(k: String) = {
        if (firstTime.booleanValue()) {
          firstTime.setValue(false)
          // do a map.get within another map.get
          getOrCreate("a").booleanValue() should be(false)
        }

        new MutableBoolean(false)
      }

      def onRemove(value: MutableBoolean) = value.setValue(true) // true represents "closed"
    }

    holder.getOrCreate("a").booleanValue() should be(false)

    // failure cause bug #85949
    holder.getOrCreate("a").booleanValue() should be(false)
  }

  describe("#getOrCreate") {

    def globalStateAndPool = {
      val trieMap = new TrieMap[String, Int]
      val subject = new SyncedKeyedResourcePool[String, Int] {

        def makeNew(k: String) = {
          trieMap.put("state", 1)
          1
        }

        override def onRemove(value: Int): Unit = {
          trieMap.remove("state")
        }
      }
      (trieMap, subject)
    }

    it("should not cause race conditions on global mutable state") {
      val (mutableState, pool) = globalStateAndPool
      val aLotOfSimultaneousGets = Future.sequence((1 to 100).map(_ => Future { pool.getOrCreate("String") }))
      whenReady(aLotOfSimultaneousGets) { r =>
        r.forall(_ == 1) should be(true)
        mutableState.get("state") should be(Some(1))
      }
    }

  }

}
