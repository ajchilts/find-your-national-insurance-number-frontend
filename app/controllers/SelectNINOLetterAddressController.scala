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

package controllers

import config.FrontendAppConfig
import connectors.IndividualDetailsConnector
import controllers.actions._
import forms.SelectNINOLetterAddressFormProvider
import models.individualdetails.AddressType.ResidentialAddress
import models.individualdetails.ResolveMerge
import models.nps.{LetterIssuedResponse, NPSFMNRequest, RLSDLONFAResponse, TechnicalIssueResponse}
import models.pdv.PDVResponseData
import models.{CorrelationId, IndividualDetailsNino, IndividualDetailsResponseEnvelope, Mode, SelectNINOLetterAddress}
import navigation.Navigator
import pages.SelectNINOLetterAddressPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.{AuditService, NPSFMNService, PersonalDetailsValidationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.SelectNINOLetterAddressView
import org.apache.commons.lang3.StringUtils
import play.api.Logging
import play.api.data.Form
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.http.HeaderCarrier
import util.AuditUtils

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SelectNINOLetterAddressController @Inject()(
                                       override val messagesApi: MessagesApi,
                                       sessionRepository: SessionRepository,
                                       navigator: Navigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       requireData: DataRequiredAction,
                                       formProvider: SelectNINOLetterAddressFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       view: SelectNINOLetterAddressView,
                                       personalDetailsValidationService: PersonalDetailsValidationService,
                                       auditService: AuditService,
                                       npsFMNService: NPSFMNService,
                                       individualDetailsConnector: IndividualDetailsConnector,
                                       appConfig: FrontendAppConfig
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  val form: Form[SelectNINOLetterAddress] = formProvider()

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      val preparedForm = request.userAnswers.get(SelectNINOLetterAddressPage) match {
          case None => form
          case Some(value) => form.fill(value)
        }

      for {
        pdvData <- personalDetailsValidationService.getPersonalDetailsValidationByNino(request.nino.getOrElse(""))
        postCode = getPostCode(pdvData)
      } yield Ok(view(preparedForm, mode, postCode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      val nino = request.nino.getOrElse(StringUtils.EMPTY)

      form.bindFromRequest().fold(
        formWithErrors =>
          for {
            pdvData <- personalDetailsValidationService.getPersonalDetailsValidationByNino(nino)
            postCode = getPostCode(pdvData)
          } yield BadRequest(view(formWithErrors, mode, postCode)),

        value => {
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(SelectNINOLetterAddressPage, value))
            _ <- sessionRepository.set(updatedAnswers)
            pdvData <- personalDetailsValidationService.getPersonalDetailsValidationByNino(nino)
            status <- npsFMNService.sendLetter(nino, getNPSFMNRequest(pdvData))
          } yield {
            updatedAnswers.get(SelectNINOLetterAddressPage) match {
              case Some(SelectNINOLetterAddress.NotThisAddress) =>
                Redirect(navigator.nextPage(SelectNINOLetterAddressPage, mode, updatedAnswers))
              case Some(SelectNINOLetterAddress.Postcode) =>
                status match {
                  case LetterIssuedResponse() =>
                    for {
                      pdvData <- personalDetailsValidationService.getPersonalDetailsValidationByNino(request.nino.getOrElse(""))
                      idAddress <- getIndividualDetailsAddress(IndividualDetailsNino(request.nino.getOrElse("")))
                    } yield (pdvData, idAddress) match {
                      case (pdvData, Right(idAddress)) =>
                        auditService.audit(AuditUtils.buildAuditEvent(pdvData.flatMap(_.personalDetails),
                          Some(idAddress),
                          "FindYourNinoOnlineLetterOption",
                          pdvData.map(_.validationStatus).getOrElse(""),
                          pdvData.map(_.CRN.getOrElse("")).getOrElse(""),
                          Some(value.toString),
                          None,
                          None,
                          None,
                          None,
                          None
                        ))
                    }
                    Redirect(navigator.nextPage(SelectNINOLetterAddressPage, mode, updatedAnswers))
                  case RLSDLONFAResponse(responseStatus, responseMessage) =>
                    personalDetailsValidationService.getPersonalDetailsValidationByNino(request.nino.getOrElse("")).onComplete {
                      case Success(pdv) =>
                        auditService.audit(AuditUtils.buildAuditEvent(pdv.flatMap(_.personalDetails),
                          None,
                          "FindYourNinoError",
                          pdv.map(_.validationStatus).getOrElse(""),
                          pdv.map(_.CRN.getOrElse("")).getOrElse(""),
                          None,
                          None,
                          None,
                          Some("/postcode"),
                          Some(responseStatus.toString),
                          Some(responseMessage)
                        ))
                      case Failure(ex) => logger.warn(ex.getMessage)
                    }
                    Redirect(routes.SendLetterErrorController.onPageLoad(mode))
                  case TechnicalIssueResponse(responseStatus, responseMessage) =>
                    personalDetailsValidationService.getPersonalDetailsValidationByNino(request.nino.getOrElse("")).onComplete {
                      case Success(pdv) =>
                        auditService.audit(AuditUtils.buildAuditEvent(pdv.flatMap(_.personalDetails),
                          None,
                          "FindYourNinoError",
                          pdv.map(_.validationStatus).getOrElse(""),
                          pdv.map(_.CRN.getOrElse("")).getOrElse(""),
                          None,
                          None,
                          None,
                          Some("/postcode"),
                          Some(responseStatus.toString),
                          Some(responseMessage)
                        ))
                      case Failure(ex) => logger.warn(ex.getMessage)
                    }
                    Redirect(routes.TechnicalErrorController.onPageLoad())
                  case _ =>
                    logger.warn("Unknown NPS FMN API response")
                    Redirect(routes.TechnicalErrorController.onPageLoad())
                }
            }
          }
        }
      )
  }

  private def getPostCode(pdvResponseData: Option[PDVResponseData]): String =
    pdvResponseData match {
      case Some(pd) => pd.getPostCode
      case _ => StringUtils.EMPTY
    }

  private def getNPSFMNRequest(pdvResponseData: Option[PDVResponseData]): NPSFMNRequest =
    pdvResponseData match {
      case Some(pd) if pd.personalDetails.isDefined =>
        NPSFMNRequest(
          pd.getFirstName,
          pd.getLastName,
          pd.getDateOfBirth,
          pd.getPostCode
        )
      case _ => NPSFMNRequest.empty
    }

  def getIndividualDetailsAddress(nino: IndividualDetailsNino
                          )(implicit ec: ExecutionContext, hc: HeaderCarrier) = {
    implicit val crypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesCrypto(appConfig.cacheSecretKey)
    implicit val correlationId: CorrelationId = CorrelationId(UUID.randomUUID())
    val idAddress = for {
      idData <- IndividualDetailsResponseEnvelope.fromEitherF(individualDetailsConnector.getIndividualDetails(nino, ResolveMerge('Y')).value)
      idDataAddress = idData.addressList.getAddress.filter(_.addressType.equals(ResidentialAddress)).head
    } yield idDataAddress
    idAddress.value
  }
}