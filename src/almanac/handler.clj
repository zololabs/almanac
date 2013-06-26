(ns almanac.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cheshire.core :as json]
            [almanac.core :as core]
            [almanac.utils :as utils]
            [slingshot.slingshot :refer [try+ throw+]]
            [ring.util.response :as ring-response]))

(defmulti format-response type)

(defmethod format-response String [data]
  data)

(defmethod format-response clojure.lang.IPersistentMap [data]
  (json/generate-string data))

(defmulti get-content-type type)

(defmethod get-content-type String [data]
  "text/plain")

(defmethod get-content-type clojure.lang.IPersistentMap [data]
  "application/json")

(defn response
  ([status-code] (response status-code ""))
  ([status-code data] (-> (ring-response/response (format-response data))
                          (ring-response/status status-code)
                          (ring-response/content-type (get-content-type data)))))

(defn process-email-request [request]
  (let [email (get-in request [:params :email])]
    (if-not (utils/valid-email? email)
      (response 400 {:error "Invalid email address"})
      (try+
       (if-let [result (core/get-social-info email)]
         (response 200 result)
         (response 404))
       (catch Exception e
         (comment (log/error "Failed"))
         (response 500 {:error (format "exception info: %s" e)}))))))

(defroutes app-routes
  (GET "/" [] process-email-request)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/api app-routes))
