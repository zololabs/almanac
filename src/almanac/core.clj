(ns almanac.core
  (:require [clojure.set :as set]
            [slingshot.slingshot :refer [try+ throw+]]
            [almanac.fullcontact :as fullcontact]
            [almanac.storage :as storage]
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

(defn get-social-info [email & {:keys [force-update networks] :as options
                                :or {:force-update false
                                     :networks []}}]
  "Returns all available information by email or nil if no info available
   Skips cache if supplied with :force-update true
   :networks can limit response only to required social networks
   "
  (if-let [cached-info (and (not force-update)
                            (storage/get-info email))]
    (do
      (log/debug (format "Got a cached response for %s" email))
      cached-info)
    (let [info (-> email
                   (fullcontact/find-person)
                   (fullcontact->almanac))]
      (log/debug (format "Recieved fullcontact data for %s (force-update %s)"
                         email
                         force-update))
      (if (and (= 0 (count (:photos info)))
               (= 0 (count (:socialProfiles info))))
        nil
        (do
          (log/debug (format "Storing fullcontact data for %s (force-update %s)"
                             email
                             force-update))
          (storage/store-info email info)
          info)))))
