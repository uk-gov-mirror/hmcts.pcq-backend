provider "azurerm" {
  version = "=1.33.1"
}

locals {
  db_connection_options  = "?sslmode=require"
  vault_name             = "${var.product}-${var.env}"
  asp_name               = "${var.product}-${var.env}"
}

module "pcq-db" {
  source                 = "git@github.com:hmcts/cnp-module-postgres?ref=master"
  product                = "${var.product}-${var.component}"
  location               = "${var.location_db}"
  env                    = "${var.env}"
  database_name          = "pcq"
  postgresql_user        = "pcquser"
  postgresql_version     = "11"
  postgresql_listen_port = "5432"
  sku_name               = "GP_Gen5_2"
  sku_tier               = "GeneralPurpose"
  common_tags            = "${var.common_tags}"
  subscription           = "${var.subscription}"
}

module "pcq" {
  source                          = "git@github.com:hmcts/cnp-module-webapp?ref=master"
  product                         = "${var.product}-${var.component}"
  location                        = "${var.location}"
  env                             = "${var.env}"
  java_container_version          = "9.0"
  subscription                    = "${var.subscription}"
  common_tags                     = "${var.common_tags}"
  appinsights_instrumentation_key = "${var.appinsights_instrumentation_key}"
  is_frontend                     = "false"
  asp_name                        = "${local.asp_name}"
  asp_rg                          = "${local.asp_name}"

  app_settings = {
    // db
    PCQ_DB_HOST                   = "${module.pcq-db.host_name}"
    PCQ_DB_PORT                   = "${module.pcq-db.postgresql_listen_port}"
    PCQ_DB_USERNAME               = "${module.pcq-db.user_name}"
    PCQ_DB_PASSWORD               = "${module.pcq-db.postgresql_password}"
    PCQ_DB_NAME                   = "${module.pcq-db.postgresql_database}"
    PCQ_DB_CONN_OPTIONS           = "${local.db_connection_options}"
    FLYWAY_URL                    = "jdbc:postgresql://${module.pcq-db.host_name}:${module.pcq-db.postgresql_listen_port}/${module.pcq-db.postgresql_database}${local.db_connection_options}"
    FLYWAY_USER                   = "${module.pcq-db.user_name}"
    FLYWAY_PASSWORD               = "${module.pcq-db.postgresql_password}"
    FLYWAY_NOOP_STRATEGY          = "true"
  }
}

data "azurerm_key_vault" "key_vault" {
  name                = "${local.vault_name}"
  resource_group_name = "${local.vault_name}"
}
  
////////////////////////////////
// Populate Vault with DB info
////////////////////////////////

resource "azurerm_key_vault_secret" "POSTGRES-USER" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${var.component}-POSTGRES-USER"
  value        = "${module.pcq-db.user_name}"
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${var.component}-POSTGRES-PASS"
  value        = "${module.pcq-db.postgresql_password}"
}
  
resource "azurerm_key_vault_secret" "POSTGRES_HOST" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${var.component}-POSTGRES-HOST"
  value        = "${module.pcq-db.host_name}"
}

resource "azurerm_key_vault_secret" "POSTGRES_PORT" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${var.component}-POSTGRES-PORT"
  value        = "${module.pcq-db.postgresql_listen_port}"
}

resource "azurerm_key_vault_secret" "POSTGRES_DATABASE" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${var.component}-POSTGRES-DATABASE"
  value        = "${module.pcq-db.postgresql_database}"
}

# Copy postgres password for flyway migration
resource "azurerm_key_vault_secret" "flyway_password" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "flyway-password"
  value        = "${module.pcq-db.postgresql_password}"
}
