(ns almanac.core
  (:require [clojure.set :as set]
            [slingshot.slingshot :refer [try+ throw+]]
            [almanac.fullcontact :as fullcontact]
            [almanac.cache :as cache :refer [KeyValueStore]]
            [almanac.utils :as utils]
            [clojure.tools.logging :as log])  )

(defn fullcontact->almanac [{:keys [photos socialProfiles]}]
  "Converts Fullcontact response to the Alamanac response"
  (let [filtered-photos (utils/group-by-and-compacted :type
                                                      #(select-keys % [:url])
                                                      photos)
        filtered-profiles (utils/group-by-and-compacted :type
                                                        #(select-keys % [:username :url])
                                                        socialProfiles)]
    {:sources (vec (set/union (set (keys filtered-photos))
                              (set (keys filtered-profiles))))
     :photos filtered-photos
     :socialProfiles filtered-profiles}))

(defn- empty-response? [info]
  (and (= 0 (count (:photos info)))
       (= 0 (count (:socialProfiles info)))))

(defn- get-fullcontact-info [email]
  (-> email
      (fullcontact/find-person)
      (fullcontact->almanac)))

(defn- get-cached-if-needed [cache email force-update]
  (and (not force-update)
       (not (nil? cache))
       (get-value cache email)))

(defn get-social-info [email cache & {:keys [force-update networks] :as options
                                :or {:force-update false
                                     :networks []}}]
  "Returns all available information by email or nil if no info available
   Skips cache if supplied with :force-update true
   :networks can limit response only to required social networks
   "
  (let [cached-info (get-cached-if-needed cache email force-update)
        info        (delay (get-fullcontact-info email))]
    (cond
     (not (nil? cached-info)) (do (log/debug (format "Cache hit for %s" email))
                                  cached-info)
     (empty-response? @info) nil
     :else (do (log/debug (format "Store to cache %s (force-update %s)" email force-update))
               (when-not (nil? cache) (set-value cache email @info))
               @info))))
