(ns almanac.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cheshire.core :as json]
            [almanac.core :as core]
            [almanac.cache :as cache]
            [almanac.utils :as utils]
            [almanac.system :refer [dev-system prod-system]]
            [almanac.storage :as ss]
            [clojure.string :as str]
            [slingshot.slingshot :refer [try+ throw+]]
            [ring.util.response :as ring-response]
            [clojure.tools.logging :as log]))

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

(defn- wrapped-email-request [system email]
  (if-not (utils/valid-email? email)
    (throw+ {:error ::invalid-email})
    (try+
     (if-let [result (core/get-social-info email (:fullcontact-cache system))]
       result
       (throw+ {:error ::not-found}))
     (catch [:error ::not-found] _
       (throw+))
     (catch Object _
       (throw+ {:error ::internal-error})))))

(defn process-email-request [system]
  (fn [request]
   (let [email (get-in request [:params :email])]
     (try+
      (response 200 (wrapped-email-request system email))
      (catch [:error ::invalid-email] _
        (response 400 {:error "Invalid email address"}))
      (catch [:error ::not-found] _
        (response 404))
      (catch [:error ::internal-error] _
        (let [original-throwable (:cause &throw-context)]
          (log/error (format "Failed request: %s, reason: %s" email original-throwable))
          (response 500 {:error (format "exception info: %s" original-throwable )})))))))

(defn process-batch-request [system]
  (fn [request]
    (let [emails (-> (get-in request [:params :emails])
                          (str/split #","))
               safe-get-info-fn (fn [email]
                                  (try+
                                   (wrapped-email-request system email)
                                   (catch [:error ::invalid-email] _
                                     nil)
                                   (catch [:error ::not-found] _
                                     nil)
                                   (catch [:error ::internal-error] _
                                     (log/error (format "Failed to get info [batch] for %s: %s" email (:cause &throw-context)))
                                     {:error (format "exception info: %s" (:cause &throw-context))})))
               data (utils/reduce-fast #(if-let [result (safe-get-info-fn %2)]
                                          (assoc! % %2 result)
                                          %)
                                       {}
                                       emails)]
           (response 200 data))))

(defn- public-activity-by-email [system email]
  (let [person (core/get-social-info email (:fullcontact-cache system))
        profiles (:socialProfiles person)
        storage (:activity-storage system)]
    (->> (keys profiles)
         (mapcat #(ss/get-user-posts storage (:username (get profiles %)) %))
         (sort-by :created-time))))

(defn process-public-activity-request [system]
  (fn [request]
    (let [email (get-in request [:params :email])]
      (if-not (utils/valid-email? email)
        (response 400 {:error "Invalid email address"})
        (try+
         (response 200 (public-activity-by-email system email))
         (catch Object _
           (let [reason (:cause &throw-context)]
             (log/error (format "Failed to get public activity for %s: %s" email reason))
             (response 500 {:error (format "Internal server error: %s" reason)}))))))))

(defn app-routes [system]
  (routes
   (GET "/api/person" [] (process-email-request system))
   (GET "/api/person/batch" [] (process-batch-request system))
   (GET "/api/person/public-activity" [] (process-public-activity-request system))
   (route/resources "/")
   (route/not-found "Not Found")))

(def app
  (handler/api (app-routes (prod-system))))

(def dev-app
  (handler/api (app-routes (dev-system))))
