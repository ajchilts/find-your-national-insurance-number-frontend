/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import java.time.Instant
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

case class TryAgainCount(id: String, count: Int, lastUpdated: Instant = Instant.now)

object TryAgainCount {
  val reads: Reads[TryAgainCount] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[String] and
        (__ \ "count").read[Int] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
      )(TryAgainCount.apply _)
  }

  val writes: OWrites[TryAgainCount] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[String] and
        (__ \ "count").write[Int] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
      )(unlift(TryAgainCount.unapply))
  }

  implicit val format: OFormat[TryAgainCount] = OFormat(reads, writes)
}
