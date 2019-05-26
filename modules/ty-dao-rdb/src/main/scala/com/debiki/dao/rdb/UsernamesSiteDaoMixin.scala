/**
 * Copyright (C) 2017 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.debiki.dao.rdb

import com.debiki.core._
import com.debiki.core.EmailNotfPrefs.EmailNotfPrefs
import com.debiki.core.Prelude._
import com.debiki.core.Participant.{LowestNonGuestId, LowestAuthenticatedUserId}
import _root_.java.{util => ju, io => jio}
import java.{sql => js}
import scala.collection.{immutable, mutable}
import scala.collection.{mutable => mut}
import scala.collection.mutable.{ArrayBuffer, StringBuilder}
import Rdb._
import RdbUtil._


/** Keeps track of which usernames have been used already in the past, in case
  * people change their usernames. If a username has been @mentioned, reusing it
  * shouldn't be allowed (but that's not enforced by this class, ed-server deals with that).
  *
  * Not yet implemented: If it has never been mentioned, reusing it is allowed,
  * after a grace period? 3 months? a year?
  */
trait UsernamesSiteDaoMixin extends SiteTransaction {
  self: RdbSiteTransaction =>

  private val orderBy = "order by in_use_from"


  def insertUsernameUsage(usage: UsernameUsage) {
    val statement = s"""
      insert into usernames3 (
        site_id, username_lowercase, in_use_from, in_use_to, user_id, first_mention_at)
      values (?, ?, ?, ?, ?, ?)
      """
    val values = List(siteId.asAnyRef, usage.usernameLowercase, usage.inUseFrom.asTimestamp,
        usage.inUseTo.orNullTimestamp, usage.userId.asAnyRef, usage.firstMentionAt.orNullTimestamp)
    runUpdateSingleRow(statement, values)
  }


  def deleteUsernameUsagesForMemberId(memberId: UserId): Int = {
    val statement = s"""
      delete from usernames3
      where site_id = ?
        and user_id = ?
      """
    val values = List(siteId.asAnyRef, memberId.asAnyRef)
    runUpdate(statement, values)
  }


  def updateUsernameUsage(usage: UsernameUsage) {
    val statement = s"""
      update usernames3 set in_use_to = ?, first_mention_at = ?
      where site_id = ?
        and username_lowercase = ?
        and in_use_from = ?
        and user_id = ?
      """
    val values = List(usage.inUseTo.orNullTimestamp, usage.firstMentionAt.orNullTimestamp,
      siteId.asAnyRef, usage.usernameLowercase, usage.inUseFrom.asTimestamp, usage.userId.asAnyRef)
    runUpdateExactlyOneRow(statement, values)
  }


  def loadUsersOldUsernames(userId: UserId): Seq[UsernameUsage] = {
    val query = s"select * from usernames3 where site_id = ? and user_id = ? $orderBy"
    val values = List(siteId.asAnyRef, userId.asAnyRef)
    runQueryFindMany(query, values, readUsernameUsage)
  }


  def loadUsernameUsages(username: String): Seq[UsernameUsage] = {
    // [CANONUN] Also search for canonical version of username, e.g. "us-er.na___me" —> "us_er_na_me".
    //val canonicalUsername = User.makeUsernameCanonical(username)
    val query = s"""
      select * from usernames3
      where site_id = ?
        and username_lowercase = ? $orderBy"""
        // and (username_lowercase = ? or username_lowercase = ?) $orderBy"""
    val values = List(siteId.asAnyRef, username.toLowerCase)  // canonicalUsername)
    runQueryFindMany(query, values, readUsernameUsage)
  }


  private def readUsernameUsage(rs: java.sql.ResultSet): UsernameUsage = {
    UsernameUsage(
      usernameLowercase = rs.getString("username_lowercase"),
      inUseFrom = getWhen(rs, "in_use_from"),
      inUseTo = getOptWhen(rs, "in_use_to"),
      userId = rs.getInt("user_id"),
      firstMentionAt = getOptWhen(rs, "first_mention_at"))
  }
}



