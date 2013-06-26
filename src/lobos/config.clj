(ns lobos.config
  (:use lobos.connectivity)
  (:use [environ.core :only [env]]))

(def db {:classname "com.mysql.jdbc.Driver"
         :subprotocol "mysql"
         :user (:rds-user env)
         :password (:rds-pass env)
         :subname (format "//%s:3306/%s" (:rds-host env) (:rds-db env))})

(open-global db)
