package com.github.j5ik2o.spetstore.application.controller

import com.github.j5ik2o.spetstore.application.EntityIOContextProvider
import com.github.j5ik2o.spetstore.application.json.CustomerJsonSupport
import com.github.j5ik2o.spetstore.domain.infrastructure.support.EntityNotFoundException
import com.github.j5ik2o.spetstore.domain.lifecycle.IdentifierService
import com.github.j5ik2o.spetstore.domain.lifecycle.customer.CustomerRepository
import com.github.j5ik2o.spetstore.domain.model.basic.Contact
import com.github.j5ik2o.spetstore.domain.model.basic.PostalAddress
import com.github.j5ik2o.spetstore.domain.model.basic._
import com.github.j5ik2o.spetstore.domain.model.customer.Customer
import com.github.j5ik2o.spetstore.domain.model.customer.CustomerConfig
import com.github.j5ik2o.spetstore.domain.model.customer.CustomerId
import com.github.j5ik2o.spetstore.domain.model.customer.CustomerProfile
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.JsArray
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._
import scala.util.Success

class CustomerController @Inject()
(customerRepository: CustomerRepository,
 entityIOContextProvider: EntityIOContextProvider)
  extends ControllerSupport with CustomerJsonSupport {

  implicit val ctx = entityIOContextProvider.get

  private def convertToEntity(customerJson: CustomerJson): Customer =
    Customer(
      id = CustomerId(customerJson.id.getOrElse(IdentifierService.generate(classOf[Customer]))),
      status = StatusType.Enabled,
      name = customerJson.name,
      sexType = SexType(customerJson.sexType),
      profile = CustomerProfile(
        postalAddress = PostalAddress(
          ZipCode(customerJson.zipCode1, customerJson.zipCode2),
          Pref(customerJson.prefCode),
          customerJson.cityName,
          customerJson.addressName,
          customerJson.buildingName
        ),
        contact = Contact(customerJson.email, customerJson.phone)
      ),
      config = CustomerConfig(
        loginName = customerJson.loginName,
        password = customerJson.password,
        favoriteCategoryId = None
      )
    )

  def create = Action {
    request =>
      request.body.asJson.map {
        e =>
          e.validate[CustomerJson].map {
            customerJson =>
              customerRepository.storeEntity(convertToEntity(customerJson)).map {
                case (_, customer) =>
                  OkForCreatedEntity(customer.id)
              }.recover {
                case ex =>
                  Logger.error("catch error", ex)
                  BadRequestForIOError
              }.get
          }.recoverTotal {
            error =>
              BadRequestForValidate(JsError.toFlatJson(error))
          }
      }.getOrElse(InternalServerError)
  }

  def list = Action {
    request =>
      val offset = request.getQueryString("offset").map(_.toInt).getOrElse(0)
      val limit = request.getQueryString("limit").map(_.toInt).getOrElse(100)
      customerRepository.resolveEntities(offset, limit).map {
        entities =>
          Ok(prettyPrint(JsArray(entities.map(toJson(_)))))
      }.getOrElse(InternalServerError)
  }

  def get(customerId: Long) = Action {
    val id = CustomerId(customerId)
    customerRepository.resolveEntity(id).map {
      entity =>
        Ok(prettyPrint(toJson(entity)))
    }.recoverWith {
      case ex: EntityNotFoundException =>
        Success(NotFoundForEntity(id))
    }.getOrElse(InternalServerError)
  }

  def update(customerId: Long) = Action {
    request =>
      val id = CustomerId(customerId)
      customerRepository.existByIdentifier(id).map {
        exist =>
          if (exist) {
            request.body.asJson.map {
              e =>
                e.validate[CustomerJson].map {
                  customerJson =>
                    require(id.value == customerJson.id.get)
                    customerRepository.storeEntity(convertToEntity(customerJson)).map {
                      case (_, customer) =>
                        OkForCreatedEntity(customer.id)
                    }.recover {
                      case ex =>
                        Logger.error("catch error", ex)
                        BadRequestForIOError
                    }.get
                }.recoverTotal {
                  error =>
                    BadRequestForValidate(JsError.toFlatJson(error))
                }
            }.getOrElse(InternalServerError)
          } else {
            BadRequest
          }
      }.get
  }

  def delete(customerId: Long) = Action {
    val id = CustomerId(customerId)
    customerRepository.deleteByIdentifier(id).map {
      case (_, entity) =>
        Ok(prettyPrint(toJson(entity)))
    }.recoverWith {
      case ex: EntityNotFoundException =>
        Success(NotFoundForEntity(id))
    }.getOrElse(InternalServerError)
  }
}
