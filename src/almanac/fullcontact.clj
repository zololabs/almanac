(ns almanac.fullcontact
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :as conn-mgr]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.tools.logging :as log]))

(def connection-pool (conn-mgr/make-reusable-conn-manager {:threads (:fullcontact-conn-threads env 5)}))

(defn- lookup-by-email-url [api-key email]
  (format "https://api.fullcontact.com/v2/person.json?apiKey=%s&email=%s" api-key email))

(defn find-person [email]
  (try+
   (let [json-body (http/get (lookup-by-email-url (:fullcontact-apikey env) email)
                             {:as :string
                              :connection-manager connection-pool})
         response (json/parse-string (:body json-body))]
     response)
   (catch Object e
     (log/error "Error retrieving person data by email %s, reason: %s" email e)
     (throw+))))
