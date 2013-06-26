(ns almanac.fullcontact
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :as conn-mgr]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+ throw+]]))

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
     (comment (log/error "Error retrieving person data %s" e))
     (throw+))))
